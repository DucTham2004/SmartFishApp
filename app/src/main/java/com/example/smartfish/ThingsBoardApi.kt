package com.example.smartfish

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

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

}