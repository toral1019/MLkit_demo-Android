package com.inexture.barcodescanner

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.common.modeldownload.FirebaseLocalModel
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.common.modeldownload.FirebaseRemoteModel
import com.google.firebase.ml.custom.*
import com.inexture.barcodescanner.databinding.ActivityCustomBinding
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.experimental.and


class CustomActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityCustomBinding

    companion object {
        // Name of the model file hosted with Firebase
        private val HOSTED_MODEL_NAME = "custom"
        private val LOCAL_MODEL_NAME = "my_local_model"
        private val LOCAL_MODEL_ASSET = "mobilenet_v1_1.0_224_quant.tflite"

        private val RC_TAKE_PICTURE = 104

        // Name of the label file stored in Assets.
        private val LABEL_PATH = "labels.txt"

        private val RESULTS_TO_SHOW = 3
        private val DIM_BATCH_SIZE = 1
        private val DIM_PIXEL_SIZE = 3
        private val DIM_IMG_SIZE_X = 224
        private val DIM_IMG_SIZE_Y = 224
    }

    // Labels corresponding to the output of the vision model
    private var mLabelList: List<String> = listOf()

    private val sortedLabels = PriorityQueue<Map.Entry<String, Float>>(RESULTS_TO_SHOW,
        Comparator { o1, o2 -> o1.value.compareTo(o2.value) })

    // Preallocated buffers for storing image data
    private val intValues = IntArray(DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y)

    private var mInterpreter: FirebaseModelInterpreter? = null

    // Data configuration of input & output data of model.
    private var mDataOptions: FirebaseModelInputOutputOptions? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_custom)

        mLabelList = loadLabelList(this)

        Log.d("TAG--Size", "${mLabelList.size}")
        Log.d("TAG--item", "${mLabelList.get(0)}")

        val inputDims = intArrayOf(DIM_BATCH_SIZE, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, DIM_PIXEL_SIZE)
        // val outputDims = intArrayOf(DIM_BATCH_SIZE, mLabelList.size)
        val outputDims = intArrayOf(DIM_BATCH_SIZE, mLabelList.size)

        try {
            mDataOptions = FirebaseModelInputOutputOptions.Builder()
                .setInputFormat(0, FirebaseModelDataType.BYTE, inputDims)
                .setOutputFormat(0, FirebaseModelDataType.BYTE, outputDims)
                .build()
            val conditionsBuilder = FirebaseModelDownloadConditions.Builder().requireWifi()

            /*
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				// Enable advanced conditions on Android Nougat and newer.
				conditionsBuilder = conditionsBuilder.requireCharging().requireDeviceIdle();
			}
			*/

            val conditions = conditionsBuilder.build()


            val localModel = FirebaseLocalModel.Builder(LOCAL_MODEL_NAME)
                .setAssetFilePath(LOCAL_MODEL_ASSET)
                .build()

            val cloudModel = FirebaseRemoteModel.Builder(HOSTED_MODEL_NAME)
                .enableModelUpdates(true)
                .setInitialDownloadConditions(conditions)
                .setUpdatesDownloadConditions(conditions)
                .build()

            val manager = FirebaseModelManager.getInstance()
            manager.registerLocalModel(localModel)
            manager.registerRemoteModel(cloudModel)

            val modelOptions = FirebaseModelOptions.Builder()
                .setLocalModelName(LOCAL_MODEL_NAME)
                .setRemoteModelName(HOSTED_MODEL_NAME)
                .build()

            mInterpreter = FirebaseModelInterpreter.getInstance(modelOptions)
        } catch (e: FirebaseMLException) {
            //  mTextView.setText(R.string.error_setup_model)
            e.printStackTrace()
        }

        mBinding.btn.setOnClickListener {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {
                    startActivityForResult(takePictureIntent, RC_TAKE_PICTURE)
                }
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_TAKE_PICTURE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            Log.d("===bitmap", imageBitmap.toString())
            mBinding.image.setImageBitmap(imageBitmap)
            runModelInference(imageBitmap)
        }
    }

    private fun loadLabelList(activity: Activity): List<String> {
        val labelList = ArrayList<String>(1001)
        try {
            val bufferedReader = BufferedReader(InputStreamReader(activity.assets.open(LABEL_PATH)))
            val br = bufferedReader.readLines()
            br.forEach {
                labelList.add(it)
            }
            bufferedReader.close()
        } catch (e: IOException) {
            //You'll need to add proper error handling here
        }
        return labelList
    }

    @Synchronized
    private fun convertBitmapToByteBuffer(bitmap: Bitmap?): ByteBuffer {
        val imgData = ByteBuffer.allocateDirect(DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE)
        imgData.order(ByteOrder.nativeOrder())
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, true)
        imgData.rewind()
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
        // Convert the image to int points.
        var pixel = 0
        for (i in 0 until DIM_IMG_SIZE_X) {
            for (j in 0 until DIM_IMG_SIZE_Y) {
                val `val` = intValues[pixel++]
                imgData.put((`val` shr 16 and 0xFF).toByte())
                imgData.put((`val` shr 8 and 0xFF).toByte())
                imgData.put((`val` and 0xFF).toByte())
            }
        }
        return imgData
    }

    private fun runModelInference(bitmap: Bitmap?) {
        if (mInterpreter == null) {
            //  mTextView.setText(R.string.error_image_init)
            return
        }
        val imgData = convertBitmapToByteBuffer(bitmap)

        try {
            val inputs = FirebaseModelInputs.Builder().add(imgData).build()
            mDataOptions?.let {
                val boolean = mInterpreter?.run(inputs, it)?.isSuccessful
                Log.d("===", boolean.toString())
                mInterpreter?.run(inputs, it)?.addOnFailureListener(object : OnFailureListener {
                    override fun onFailure(p0: Exception) {
                        Log.e("===", p0.localizedMessage)
                        p0.printStackTrace()
                    }
                })?.continueWith { task ->
                    Log.d("===result", task.result.toString())
                    val labelProbArray = task.result?.getOutput<Array<ByteArray>>(0)
                    Log.d("===", labelProbArray.toString())
                    val topLabels = getTopLabels(labelProbArray!!)
                    for (label in topLabels) {
                        mBinding.text.append(label + "\n\n")
                    }
                    Log.d("===label", topLabels.toString())
                    return@continueWith
                }
                    ?.addOnSuccessListener {
                        Log.d("TAG", "Success")
                    }
            }
        } catch (e: FirebaseMLException) {
            e.printStackTrace()
            //  mTextView.setText(R.string.error_run_model)
        }

    }

    @Synchronized
    private fun getTopLabels(labelProbArray: Array<ByteArray>): List<String> {
        for (i in mLabelList.indices) {
            sortedLabels.add(AbstractMap.SimpleEntry(mLabelList[i], (labelProbArray[0][i] and 0xff.toByte()) / 255.0f))
            if (sortedLabels.size > RESULTS_TO_SHOW) {
                sortedLabels.poll()
            }
        }
        val result = ArrayList<String>()
        val size = sortedLabels.size
        for (i in 0 until size) {
            val label = sortedLabels.poll()
            result.add(label.key + ": " + label.value)
        }
        return result
    }
}
