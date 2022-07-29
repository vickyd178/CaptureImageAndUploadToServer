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

    private val captureImageContract =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
            binding.imageView.setImageURI(null)
            binding.imageView.setImageURI(imageUri)
            // Use this  "imageUri" to create multipart request
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        imageUri = createImageUri()

        binding.button.setOnClickListener {
            imageUri?.let {
                captureImageContract.launch(it)
            }

        }

        binding.buttonUpload.setOnClickListener {
            uploadImage()
        }

    }

    private fun uploadImage() {
        binding.progressBar.isVisible = true
        progressUpdate(0)
        imageUri?.let {
            val file = File(cacheDir, contentResolver.getFileName(it))
            if (file.exists()) {
                val parcelFileDescriptor =
                    contentResolver.openFileDescriptor(it, "r", null) ?: return
                val inputStream = FileInputStream(parcelFileDescriptor.fileDescriptor)
                val outputStream = FileOutputStream(file)
                inputStream.copyTo(outputStream)

                val body = UploadRequestBody(
                    file = file,
                    contentType = "image",
                    this
                )

                MyApi().uploadImage(
                    MultipartBody.Part.createFormData("image", filename = file.name, body),
                    file.name.toRequestBody("multipart/form-data".toMediaTypeOrNull()),
                ).enqueue(object : Callback<UploadResponse> {
                    override fun onResponse(
                        call: Call<UploadResponse>,
                        response: Response<UploadResponse>
                    ) {
                        binding.root.showSnack(response.body()?.message.toString())
                        progressUpdate(100)

                    }

                    override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                        binding.root.showSnack(t.toString())
                    }

                })
            } else {
                binding.root.showSnack("Capture an image first")
            }
        } ?: binding.root.showSnack("Capture an image first")
    }

    private fun createImageUri(): Uri? {
        val image =
            File(applicationContext.filesDir, "camera_image${System.currentTimeMillis()}.png")

        return FileProvider.getUriForFile(
            applicationContext,
            "com.avion.captureimage.fileProvider",
            image
        )
    }

    override fun progressUpdate(percentage: Int) {
        Log.e(Companion.TAG, "progressUpdate: $percentage")
        binding.progressBar.progress = percentage
        binding.tvProgress.text = "$percentage%"

    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

fun View.showSnack(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).also { snack ->
        snack.setAction("Ok") {
            snack.dismiss()
        }
    }.show()
}

fun ContentResolver.getFileName(uri: Uri): String {
    var name = ""
    val cursor = query(uri, null, null, null, null)
    cursor?.use {
        it.moveToFirst()
        name = cursor.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
    }
    return name
}