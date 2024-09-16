package com.example.orehqmobile.data.repositories

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.ws
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.errors.IOException
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface IPoolRepository {
    suspend fun connectWebSocket(): Flow<ByteArray>
    suspend fun fetchTimestamp(): Result<String>
}

class PoolRepository: IPoolRepository {
    private val client = HttpClient {
        install(WebSockets)
    }

    override suspend fun connectWebSocket(): Flow<ByteArray> = flow {
        client.wss(host = "ec1ipse.me") {
            for (frame in incoming) {
                if (frame is Frame.Binary) {
                    emit(frame.readBytes())
                }
            }
        }
    }

    override suspend fun fetchTimestamp(): Result<String>  {
        return try {
            val response: HttpResponse = client.get("https://ec1ipse.me/timestamp")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        }  catch (e: Exception) {
            Result.failure(e)
        }
    }
}