package com.inexture.barcodescanner

import android.content.Intent
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.camerakit.CameraKit
import com.camerakit.CameraKitView
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.inexture.barcodescanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityMainBinding
    val REQUEST_IMAGE_CAPTURE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        FirebaseApp.initializeApp(this)
        dispatchTakePictureIntent()

    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        }
    }

    private fun getQRCodeDetails(bitmap: Bitmap) {
        val options = FirebaseVisionBarcodeDetectorOptions.Builder()
            .setBarcodeFormats(
                FirebaseVisionBarcode.FORMAT_ALL_FORMATS
            )
            .build()
        val detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options);
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val result = detector.detectInImage(image)
        result.addOnSuccessListener {
            Log.d("===success", it.toString())
            for (firebaseBarcode in it) {
                mBinding.textName.text = firebaseBarcode.displayValue + firebaseBarcode.email + firebaseBarcode.phone
            }
        }
        result.addOnFailureListener {
            it.printStackTrace()
            Toast.makeText(baseContext, "Sorry, something went wrong!", Toast.LENGTH_SHORT).show()
        }
        result.addOnCanceledListener {

        }
        result.addOnCompleteListener {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == AppCompatActivity.RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as Bitmap
            Log.d("===bitmap", imageBitmap.toString())
            getQRCodeDetails(imageBitmap)
        }
    }
}
