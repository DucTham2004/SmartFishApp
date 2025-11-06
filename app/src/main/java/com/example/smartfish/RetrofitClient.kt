package com.example.smartfish

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory // <-- THAY ĐỔI 1
import kotlinx.serialization.json.Json // <-- THÊM MỚI
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

object RetrofitClient {

    private const val BASE_URL = "https://eu.thingsboard.cloud"

    // Thêm một bộ cấu hình Json (nếu cần)
    private val json = Json {
        ignoreUnknownKeys = true // Bỏ qua các trường không xác định trong JSON
    }

    val instance: ThingsBoardApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            // .addConverterFactory(GsonConverterFactory.create()) // <-- XÓA DÒNG NÀY
            .addConverterFactory( // <-- THAY BẰNG 2 DÒNG NÀY
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()

        retrofit.create(ThingsBoardApi::class.java)
    }
}