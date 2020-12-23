package com.inexture.barcodescanner

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import com.inexture.barcodescanner.databinding.ActivityCustomModelBinding
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.os.Environment.DIRECTORY_PICTURES
import android.os.Environment.getExternalStoragePublicDirectory
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class CustomModelActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityCustomModelBinding

    var bitmap:Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_custom_model)

        var conditionsBuilder: FirebaseModelDownloadConditions.Builder =
            FirebaseModelDownloadConditions.Builder().requireWifi()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Enable advanced conditions on Android Nougat and newer.
            conditionsBuilder = conditionsBuilder
                .requireCharging()
                .requireDeviceIdle()
                .requireWifi()
        }
        val conditions = conditionsBuilder.build()

        configureLocalModelSource()

        // Build a remote model object by specifying the name you assigned the model
        // when you uploaded it in the Firebase console.
        val cloudSource = FirebaseRemoteModel.Builder("imageclassification")
            // enable model update
            .enableModelUpdates(true)
            .setInitialDownloadConditions(conditions)
            .setUpdatesDownloadConditions(conditions)
            .build()
        FirebaseModelManager.getInstance().registerRemoteModel(cloudSource)

        mBinding.btn.setOnClickListener {
              // runInference()
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {
                    startActivityForResult(takePictureIntent, 11)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Override the MainActivity function to check when a result is returned
        // from any intent - if the requestCode matches then draw the resulting
        // bitmap to the image view
        if (requestCode == 11 && resultCode == RESULT_OK) {
            bitmap = data?.extras?.get("data") as Bitmap
            // Pass the bitmap result to the inference method
            bitmapToInputArray(bitmap)
            runInference()
        }
    }


    /**
     * Configure local model source
     * Sets file path for tensorflow file
     */
    private fun configureLocalModelSource() {
        // [START mlkit_local_model_source]
        val localSource = FirebaseLocalModel.Builder("customlocalemodeldemo") // Assign a name to this model
            .setAssetFilePath("imageClassification.tflite")
            .build()
        FirebaseModelManager.getInstance().registerLocalModel(localSource)
        // [END mlkit_local_model_source]
    }

    /**
     * Create interpreter to run the custom model with tensorflow file
     * @return interpreter
     */
    @Throws(FirebaseMLException::class)
    private fun createInterpreter(): FirebaseModelInterpreter? {
        // [START mlkit_create_interpreter]
        val options = FirebaseModelOptions.Builder()
            .setRemoteModelName("imageclassification")
            .setLocalModelName("customlocalemodeldemo")
            .build()
        val interpreter = FirebaseModelInterpreter.getInstance(options)
        // [END mlkit_create_interpreter]

        return interpreter
    }

    /**
     * Create input, output format as per your requirement
     * @return FirebaseModelInputOutputOptions object
     */
    @Throws(FirebaseMLException::class)
    private fun createInputOutputOptions(): FirebaseModelInputOutputOptions {
        // [START mlkit_create_io_options]
        val inputOutputOptions = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 224, 224, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 1001))
            .build()
        // [END mlkit_create_io_options]
        return inputOutputOptions
    }

    private fun bitmapToInputArray(imageBitmap: Bitmap?): Array<Array<Array<FloatArray>>> {
        // [START mlkit_bitmap_input]
        val bitmap = Bitmap.createScaledBitmap(imageBitmap, 224, 224, true)

        val batchNum = 0
        val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
        for (x in 0..223) {
            for (y in 0..223) {
                val pixel = bitmap.getPixel(x, y)
                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                input[batchNum][x][y][0] = (Color.red(pixel) - 127) / 255.0f
                input[batchNum][x][y][1] = (Color.green(pixel) - 127) / 255.0f
                input[batchNum][x][y][2] = (Color.blue(pixel) - 127) / 255.0f
            }
        }
        // [END mlkit_bitmap_input]
        return input
    }

    /**
     * Run the custom model
     * create a FirebaseModelInputs object with your input data, and
     * pass it and the model's input and output specification to the model interpreter's run method
     */
    @Throws(FirebaseMLException::class)
    private fun runInference() {
        val firebaseInterpreter = createInterpreter()!!
        val input = bitmapToInputArray(bitmap!!)
        val inputOutputOptions = createInputOutputOptions()

        // [START mlkit_run_inference]
        val inputs = FirebaseModelInputs.Builder()
            .add(input) // add() as many input arrays as your model requires
            .build()
        firebaseInterpreter.run(inputs, inputOutputOptions)
            .addOnSuccessListener { result ->
                // [START mlkit_read_result]
                val output = result.getOutput<Array<FloatArray>>(0)
                Log.d("===",output.toString())
                val probabilities = output[0]
                Log.d("===output", probabilities.toString())
                // [END mlkit_read_result]
            }
            .addOnFailureListener(
                object : OnFailureListener {
                    override fun onFailure(e: Exception) {
                        // Task failed with an exception
                        Log.d("===output", e.toString())
                    }
                })
        // [END mlkit_run_inference]
    }
}
