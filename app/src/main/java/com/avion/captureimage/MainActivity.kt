package com.avion.captureimage

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.avion.captureimage.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), UploadRequestBody.UploadCallback {

    private lateinit var binding: ActivityMainBinding
    private var imageUri: Uri? = null

    //Activity Contract to capture image
    private val captureImageContract =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            isSuccess?.let {
                if (it) {
                    binding.imageView.setImageURI(null)
                    binding.imageView.setImageURI(imageUri)
                    progressUpdate(0)
                }
            }
        }

    //Activity Contract to choose image
    private val chooseImageContract =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                imageUri = uri
                binding.imageView.setImageURI(uri)
                progressUpdate(0)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener {
            imageUri = createImageUri()
            imageUri?.let {
                //Open Camera
                captureImageContract.launch(it)
            }

        }

        binding.buttonGallery.setOnClickListener {
            //Open gallery to choose image
            chooseImageContract.launch("image/*")
        }
        binding.buttonUpload.setOnClickListener {
            uploadImage()
        }

    }

    //Upload image to server
    private fun uploadImage() {
        binding.progressBar.isVisible = true
        progressUpdate(0)
        imageUri?.let {
            //Create file in cacheDir
            val file = File(cacheDir, contentResolver.getFileName(it))
            val parcelFileDescriptor =
                contentResolver.openFileDescriptor(it, "r", null) ?: return
            val inputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            ///////////////////////////////

            //Create Request body of image
            val body = UploadRequestBody(
                file = file,
                contentType = "image",
                this
            )
            //check if file exist or not
            if (file.exists()) {
                MyApi().uploadImage(
                    MultipartBody.Part.createFormData(
                        "image",
                        filename = file.name,
                        body
                    ), // Create multipart request
                    file.name.toRequestBody("multipart/form-data".toMediaTypeOrNull()),
                ).enqueue(object : Callback<UploadResponse> {
                    override fun onResponse(
                        call: Call<UploadResponse>,
                        response: Response<UploadResponse>
                    ) {
                        binding.root.showSnack(response.body()?.message.toString())
                        progressUpdate(100)
                        //Delete file from cache after upload
                        file.delete()
                    }

                    override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                        binding.root.showSnack(t.toString())
                    }

                })
            } else {
                //file create failed
                binding.root.showSnack("Choose an image first.")
            }
        } ?: binding.root.showSnack("Choose an image first.")
    }

    //create uri and provide to camera application so captured image stored at this location
    private fun createImageUri(): Uri? {
        val image =
            File(applicationContext.filesDir, "camera_image.png")

        return FileProvider.getUriForFile(
            applicationContext,
            "com.avion.captureimage.fileProvider",
            image
        )
    }

    //Receive image upload progress status and update UI accordingly
    override fun progressUpdate(percentage: Int) {
        Log.e(TAG, "progressUpdate: $percentage")
        binding.progressBar.progress = percentage
        val progressText = "$percentage% Uploaded."
        binding.tvProgress.text = progressText
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

//Extension function to show snack bar
fun View.showSnack(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).also { snack ->
        snack.setAction("Ok") {
            snack.dismiss()
        }
    }.show()
}

//get filename from uri
fun ContentResolver.getFileName(uri: Uri): String {
    var name = ""
    val cursor = query(uri, null, null, null, null)
    cursor?.use {
        it.moveToFirst()
        name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
    }
    return name
}