package com.kriptikz.orehqmobile.data.repositories

import android.util.Log
import com.kriptikz.orehqmobile.data.models.ServerMessage
import com.funkatronics.encoders.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.errors.IOException
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

interface IPoolRepository {
    suspend fun connectWebSocket(
        timestamp: ULong,
        signature: String,
        publicKey: String,
        onConnectionCallback: (() -> Unit)?,
    ): Flow<ServerMessage>

    suspend fun disconnectWebsocket()
    suspend fun fetchTimestamp(): Result<ULong>
    suspend fun sendWebSocketMessage(message: ByteArray)
    suspend fun fetchMinerBalance(publicKey: String): Result<Double>
    suspend fun fetchMinerRewards(publicKey: String): Result<Double>
    suspend fun fetchActiveMinersCount(): Result<Int>
    suspend fun fetchPoolBalance(): Result<Double>
    suspend fun fetchPoolMultiplier(): Result<Double>
    suspend fun fetchPoolAuthorityPubkey(): Result<String>
    suspend fun fetchLatestBlockhash(): Result<String>
    suspend fun fetchSolBalance(publicKey: String): Result<Double>
    suspend fun fetchSignupFee(): Result<Double>
    suspend fun signup(minerPubkey: String): Result<String>
    suspend fun claim(
        timestamp: ULong,
        signature: String,
        minerPubkey: String,
        receiverPubkey: String,
        amount: ULong,
    ): Result<String>
}

const val HOST_URL = "ec1ipse.me"
const val STATS_HOST_URL = "domainexpansion.tech"

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
        onConnectionCallback: (() -> Unit)?,
    ): Flow<ServerMessage> = flow {
        val auth = Base64.getEncoder().encodeToString("${publicKey}:${signature}".toByteArray())

        client.wss(
            urlString = "wss://$HOST_URL/v2/ws?timestamp=$timestamp",
            request = {
                header(HttpHeaders.Host, HOST_URL)
                header(HttpHeaders.Authorization, "Basic $auth")
            },

        ) {
            webSocketSession = this
            onConnectionCallback?.invoke()
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> {
                        val message = ServerMessage.fromUByteArray(frame.readBytes().toUByteArray())
                        message?.let { emit(it) }
                    }
                    is Frame.Text -> {
                        Log.d("PoolRepository", "Got Text: ${frame.readText()}")

                    }
                    is Frame.Close -> {
                        //Log.d("PoolRepository", "Got close")
                    }
                    is Frame.Ping -> {
                        //Log.d("PoolRepository", "Got ping")
                    }
                    is Frame.Pong -> {
                        //Log.d("PoolRepository", "Got pong")
                    }
                }
            }
        }
    }

    override suspend fun disconnectWebsocket() {
        webSocketSession?.close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Reconnecting"))
    }

    override suspend fun fetchTimestamp(): Result<ULong> {
        return try {
            val response: HttpResponse = client.get("https://$HOST_URL/timestamp")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toULong())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchMinerBalance(publicKey: String): Result<Double> {
      return try {
          val response: HttpResponse = client.get("https://$HOST_URL/miner/balance") {
              parameter("pubkey", publicKey)
          }
          if (response.status.value in 200..299) {
              Result.success(response.bodyAsText().toDouble())
          } else {
              Result.failure(IOException("HTTP error ${response.status.value}"))
          }
      } catch (e: Exception) {
          Result.failure(e)
      }
    }

    override suspend fun fetchMinerRewards(publicKey: String): Result<Double> {
        return try {
            val response: HttpResponse = client.get("https://$HOST_URL/miner/rewards") {
                parameter("pubkey", publicKey)
            }
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toDouble())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchActiveMinersCount(): Result<Int> {
        return try {
            val response: HttpResponse = client.get("https://$HOST_URL/active-miners")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toInt())
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

    override suspend fun fetchPoolBalance(): Result<Double> {
        return try {
            val response: HttpResponse = client.get("https://$STATS_HOST_URL/pool/staked")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toDouble() / 100000000000)
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchPoolMultiplier(): Result<Double> {
        return try {
            val response: HttpResponse = client.get("https://$STATS_HOST_URL/stake-multiplier")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toDouble())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchPoolAuthorityPubkey(): Result<String> {
        return try {
            val response: HttpResponse = client.get("https://$HOST_URL/pool/authority/pubkey")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchSignupFee(): Result<Double> {
        return try {
            val response: HttpResponse = client.get("https://$STATS_HOST_URL/signup-fee")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toDouble())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchSolBalance(publicKey: String): Result<Double> {
        return try {
            val response: HttpResponse = client.get("https://$STATS_HOST_URL/sol-balance?pubkey=$publicKey")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText().toDouble())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchLatestBlockhash(): Result<String> {
        return try {
            val response: HttpResponse = client.get("https://$HOST_URL/latest-blockhash")
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signup(minerPubkey: String): Result<String> {
        return try {
            Log.d("PoolRepository", "Signup with pubkey: $minerPubkey")
            val response: HttpResponse = client.post("https://$HOST_URL/v2/signup?miner=$minerPubkey") {
                setBody("BLANK")
            }
            if (response.status.value in 200..299) {
                Result.success(response.bodyAsText())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun claim(
        timestamp: ULong,
        signature: String,
        minerPubkey: String,
        receiverPubkey: String,
        amount: ULong,
    ): Result<String> {
        try {
            Log.d("PoolRepository", "Claiming $amount ORE to $receiverPubkey")
            val auth = Base64.getEncoder().encodeToString("${minerPubkey}:${signature}".toByteArray())
            val response: HttpResponse =
                client.post("https://$HOST_URL/v2/claim?timestamp=$timestamp&receiver_pubkey=$receiverPubkey&amount=$amount") {
                    header(HttpHeaders.Host, HOST_URL)
                    header(HttpHeaders.Authorization, "Basic $auth")
                }
            return if (response.status.value in 200..299) {
                Result.success(response.bodyAsText())
            } else {
                Result.failure(IOException("HTTP error ${response.status.value}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}

