package com.avion.captureimage

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface MyApi {


    @Multipart
    @POST("/projects/upload/upload.php")
    fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("name") desc: RequestBody
    ): Call<UploadResponse>


    companion object {
        operator fun invoke(): MyApi {
            return Retrofit.Builder()
                .baseUrl("http://192.168.0.103")
                .addConverterFactory(MoshiConverterFactory.create()).build()
                .create(MyApi::class.java)
        }
    }
}

data class UploadResponse(
    val status: Boolean,
    val message: String
)