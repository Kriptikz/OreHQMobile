package com.example.orehqmobile.data.repositories

import com.funkatronics.encoders.Base64
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.errors.IOException
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

interface IPoolRepository {
    suspend fun connectWebSocket(timestamp: ULong, signature: String, publicKey: String): Flow<ByteArray>
    suspend fun fetchTimestamp(): Result<ULong>
}

class PoolRepository: IPoolRepository {
    private val client = HttpClient {
        install(WebSockets)
    }

    override suspend fun connectWebSocket(timestamp: ULong, signature: String, publicKey: String): Flow<ByteArray> = flow {
      val auth = Base64.getEncoder().encodeToString("${publicKey}:${signature}".toByteArray())
      
      client.wss(
          urlString = "wss://ec1ipse.me?timestamp=$timestamp",
          request = {
              header(HttpHeaders.Host, "ec1ipse.me")
              header(HttpHeaders.Authorization, "Basic $auth")
          }
      ) {
          for (frame in incoming) {
              if (frame is Frame.Binary) {
                  emit(frame.readBytes())
              }
          }
      }
    }

    override suspend fun fetchTimestamp(): Result<ULong>  {
        return try {
            val response: HttpResponse = client.get("https://ec1ipse.me/timestamp")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toULong())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        }  catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateWebSocketKey(): String {
        return Base64.getEncoder().encodeToString(UUID.randomUUID().toString().take(16).toByteArray())
    }
}

public fun ULong.toLittleEndianByteArray(): ByteArray {
  return ByteArray(8) { i -> (this shr (8 * i)).toByte() }
}

// Helper extension function to convert ByteArray to hex string
fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }