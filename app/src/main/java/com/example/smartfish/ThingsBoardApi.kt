package com.example.smartfish

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class RpcRequest(
    val method: String,
    val params: String
)

interface ThingsBoardApi {

    @POST("/api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse> // "suspend" để dùng với Coroutines

    @POST("/api/plugins/rpc/oneway/{deviceId}")
    suspend fun sendRpcRequest(
        @Header("X-Authorization") token: String, // "Bearer " + JWT Token
        @Path("deviceId") deviceId: String,
        @Body request: RpcRequest
    ): Response<Unit> // Chúng ta không cần quan tâm data trả về

    // Hàm lấy dữ liệu lịch sử
    @GET("/api/plugins/telemetry/DEVICE/{deviceId}/values/timeseries")
    suspend fun getTimeseries(
        @Header("X-Authorization") token: String,
        @Path("deviceId") deviceId: String,
        @Query("keys") keys: String,      // Ví dụ: "nhietDo,doAm"
        @Query("startTs") startTs: Long,  // Thời gian bắt đầu (milisecond)
        @Query("endTs") endTs: Long       // Thời gian kết thúc (milisecond)
    ): Response<JsonObject> // Trả về JsonObject để linh hoạt parse
}