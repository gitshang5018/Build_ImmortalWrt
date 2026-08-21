package org.immortalwrt.manager.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.immortalwrt.manager.domain.model.RouterCredentials
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UbusClient {

    private val gson = Gson()
    private var currentCredentials: RouterCredentials? = null
    private var sessionToken: String = "00000000000000000000000000000000"
    private var api: UbusApi? = null

    val isConnected: Boolean get() = sessionToken != "00000000000000000000000000000000"
    val activeToken: String get() = sessionToken

    fun initClient(credentials: RouterCredentials) {
        currentCredentials = credentials

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClientBuilder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)

        if (credentials.useHttps) {
            configureUnsafeSsl(okHttpClientBuilder)
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(credentials.baseUrl)
            .client(okHttpClientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        api = retrofit.create(UbusApi::class.java)
    }

    private fun configureUnsafeSsl(builder: OkHttpClient.Builder) {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun login(credentials: RouterCredentials): Result<String> = withContext(Dispatchers.IO) {
        try {
            initClient(credentials)
            val request = UbusRequest.create(
                sessionToken = "00000000000000000000000000000000",
                module = "session",
                func = "login",
                args = mapOf("username" to credentials.username, "password" to credentials.password)
            )
            val response = api?.call(request)
            if (response != null && response.isSuccessful) {
                val body = response.body()
                val resultArr = body?.getAsJsonArray("result")
                if (resultArr != null && resultArr.size() >= 2) {
                    val code = resultArr[0].asInt
                    if (code == 0) {
                        val payload = resultArr[1].asJsonObject
                        val token = payload.get("ubus_rpc_session")?.asString
                        if (!token.isNullOrEmpty()) {
                            sessionToken = token
                            return@withContext Result.success(token)
                        }
                    }
                }
            }
            Result.failure(Exception("登录失败：用户名或密码错误 / 路由器无响应"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun callRaw(module: String, func: String, args: Any = emptyMap<String, Any>()): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val request = UbusRequest.create(sessionToken, module, func, args)
            val response = api?.call(request)
            if (response != null && response.isSuccessful) {
                val body = response.body()
                val resultArr = body?.getAsJsonArray("result")
                if (resultArr != null && resultArr.size() >= 2) {
                    val code = resultArr[0].asInt
                    if (code == 0) {
                        val payload = resultArr[1].asJsonObject
                        return@withContext Result.success(payload)
                    } else if (code == -1 && currentCredentials != null) {
                        // Token expired, attempt transparent relogin once
                        val reloginRes = login(currentCredentials!!)
                        if (reloginRes.isSuccess) {
                            val retryRequest = UbusRequest.create(sessionToken, module, func, args)
                            val retryResp = api?.call(retryRequest)
                            val retryBody = retryResp?.body()?.getAsJsonArray("result")
                            if (retryBody != null && retryBody.size() >= 2 && retryBody[0].asInt == 0) {
                                return@withContext Result.success(retryBody[1].asJsonObject)
                            }
                        }
                    }
                }
            }
            Result.failure(Exception("RPC 调用失败: $module.$func"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
