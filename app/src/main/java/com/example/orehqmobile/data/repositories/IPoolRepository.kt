package com.example.orehqmobile.data.repositories

import android.util.Log
import com.example.orehqmobile.data.models.ServerMessage
import com.funkatronics.encoders.Base64
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
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
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

interface IPoolRepository {
    suspend fun connectWebSocket(
        timestamp: ULong,
        signature: String,
        publicKey: String,
        sendReadyMessage: () -> Unit
    ): Flow<ServerMessage>

    suspend fun fetchTimestamp(): Result<ULong>
    suspend fun sendWebSocketMessage(message: ByteArray)
}

class PoolRepository : IPoolRepository {
    private val client = HttpClient {
        install(WebSockets) {
            pingInterval = 500
        }
    }

    private var webSocketSession: DefaultClientWebSocketSession? = null

    override suspend fun connectWebSocket(
        timestamp: ULong,
        signature: String,
        publicKey: String,
        sendReadyMessage: () -> Unit
    ): Flow<ServerMessage> = flow {
        val auth = Base64.getEncoder().encodeToString("${publicKey}:${signature}".toByteArray())

        client.wss(
            urlString = "wss://domainexpansion.tech?timestamp=$timestamp",
            request = {
                header(HttpHeaders.Host, "domainexpansion.tech")
                header(HttpHeaders.Authorization, "Basic $auth")
            }
        ) {
            webSocketSession = this
            sendReadyMessage()
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    Log.d("PoolRepository", "Got Text: ${frame.readText()}")
                }
                if (frame is Frame.Binary) {
                    val message = parseServerMessage(frame.readBytes().toUByteArray())
                    message?.let { emit(it) }
                }
            }
        }
    }

    private fun parseServerMessage(data: UByteArray): ServerMessage? {
        if (data.isEmpty()) return null

        return when (data[0].toUInt()) {
            0U -> parseStartMining(data)
            else -> {
                Log.w("PoolRepository", "Unknown message type: ${data[0]}")
                null
            }
        }
    }

    private fun parseStartMining(data: UByteArray): ServerMessage.StartMining? {
        if (data.size < 57) {
            Log.w("PoolRepository", "Invalid data for StartMining message")
            return null
        }

        val challenge = data.slice(1..32).toUByteArray()
        val cutoff = data.slice(33..40).toUByteArray().toULong()
        val nonceStart = data.slice(41..48).toUByteArray().toULong()
        Log.d("PoolRepository", "Nonce Start: $nonceStart")
        val nonceEnd = data.slice(49..56).toUByteArray().toULong()
        Log.d("PoolRepository", "Nonce End: $nonceEnd")


        return ServerMessage.StartMining(challenge, nonceStart until nonceEnd, cutoff)
    }

    override suspend fun fetchTimestamp(): Result<ULong> {
        return try {
            val response: HttpResponse = client.get("https://ec1ipse.me/timestamp")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toULong())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendWebSocketMessage(message: ByteArray) {
        webSocketSession?.send(Frame.Binary(true, message))
            ?: throw IllegalStateException("WebSocket session not initialized")
    }

    private fun generateWebSocketKey(): String {
        return Base64.getEncoder()
            .encodeToString(UUID.randomUUID().toString().take(16).toByteArray())
    }
}

// Helper conversion functions
public fun ULong.toLittleEndianByteArray(): ByteArray {
    return ByteArray(8) { i -> (this shr (8 * i)).toByte() }
}

fun Long.toLittleEndianByteArray(): ByteArray {
    return ByteArray(8) { i -> (this shr (8 * i) and 0xFFL).toByte() }
}

fun UByteArray.toULong(): ULong = this.foldIndexed(0UL) { index, acc, byte ->
    acc or (byte.toULong() shl (index * 8))
}

private fun List<UByte>.toUByteArray(): UByteArray {
    return UByteArray(size) { this[it] }
}

fun ULong.toLittleEndianUByteArray(): UByteArray {
    return UByteArray(8) { i -> ((this shr (8 * i)) and 0xFFU).toUByte() }
}

