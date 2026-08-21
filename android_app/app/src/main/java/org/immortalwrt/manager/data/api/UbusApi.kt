package org.immortalwrt.manager.data.api

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface UbusApi {
    @POST("ubus")
    suspend fun call(
        @Body request: UbusRequest
    ): Response<JsonObject>

    @POST("ubus")
    suspend fun callBatch(
        @Body requests: List<UbusRequest>
    ): Response<List<JsonObject>>
}
