package com.kriptikz.orehqmobile.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.kriptikz.orehqmobile.data.database.AppRoomDatabase
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import com.kriptikz.orehqmobile.data.models.ServerMessage
import com.kriptikz.orehqmobile.data.models.toLittleEndianByteArray
import com.kriptikz.orehqmobile.data.repositories.IKeypairRepository
import com.kriptikz.orehqmobile.data.repositories.IPoolRepository
import com.kriptikz.orehqmobile.data.repositories.KeypairRepository
import com.kriptikz.orehqmobile.data.repositories.PoolRepository
import com.kriptikz.orehqmobile.data.repositories.SubmissionResultRepository
import com.kriptikz.orehqmobile.notification.NotificationsHelper
import com.funkatronics.encoders.Base58
import com.funkatronics.encoders.Base64
import com.kriptikz.orehqmobile.data.repositories.AppAccountRepository
import com.kriptikz.orehqmobile.worker.MiningWorker
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.orehqmobileffi.DxSolution
import kotlin.math.pow

/**
 * Simple foreground service that shows a notification to the user.
 */
class OreHQMobileForegroundService : Service() {
    private val binder = LocalBinder()

    private var poolRepository: IPoolRepository = PoolRepository()
    private var keypairRepository: IKeypairRepository = KeypairRepository(this)

    private val appDb = AppRoomDatabase.getInstance(this)
    private val submissionResultDao = appDb.submissionResultDao()
    private var submissionResultRepository = SubmissionResultRepository(submissionResultDao)
    private val appAccountDao = appDb.appAccountDao()
    private var appAccountRepository = AppAccountRepository(appAccountDao)

    private val runtimeAvailableThreads = Runtime.getRuntime().availableProcessors()

    private val coroutineScope = CoroutineScope(Job())

    private val _threadCount = MutableStateFlow<Int>(1)
    var threadCount: StateFlow<Int> = _threadCount

    private val _hashpower = MutableStateFlow<UInt>(0u)
    var hashpower: StateFlow<UInt> = _hashpower

    private val _difficulty = MutableStateFlow<UInt>(0u)
    var difficulty: StateFlow<UInt> = _difficulty

    private val _poolBalance = MutableStateFlow<Double>(0.0)
    var poolBalance: StateFlow<Double> = _poolBalance

    private val _topStake = MutableStateFlow<Double>(0.0)
    var topStake: StateFlow<Double> = _topStake

    private val _poolMultiplier = MutableStateFlow<Double>(0.0)
    var poolMultiplier: StateFlow<Double> = _poolMultiplier

    private val _activeMiners = MutableStateFlow<UInt>(0u)
    var activeMiners: StateFlow<UInt> = _activeMiners

    private var keepMining = true
    private var isWebsocketConnected = false
    private var pubkey: String? = null

    private var localIsMining = true

    var powerSlider: Float = 0f

    private val NOTIFICATION_ID = 1

    inner class LocalBinder : Binder() {
        fun getService(): OreHQMobileForegroundService = this@OreHQMobileForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        startAsForegroundService()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        Toast.makeText(this, "Mining Started", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        coroutineScope.coroutineContext.cancelChildren()
        Toast.makeText(this, "Mining Stopped", Toast.LENGTH_SHORT).show()
    }

    /**
     * Promotes the service to a foreground service, showing a notification to the user.
     *
     * This needs to be called within 10 seconds of starting the service or the system will throw an exception.
     */
    private fun startAsForegroundService() {
        // create the notification channel
        NotificationsHelper.createNotificationChannel(this)

        // promote service to foreground service
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            NotificationsHelper.buildNotification(this, 0, 0u, 0u),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        coroutineScope.launch(Dispatchers.IO) {
            appAccountRepository.getAppAccountAsFlow().distinctUntilChanged().collect{
                it?.let {
                    if (it.isNotEmpty()) {
                        val acc = it.first()
                        val t = MiningWorker.calculateThreads(acc.miningPowerLevel)
                        if (acc.isMiningSwitchOn) {
                            NotificationsHelper.updateNotification(this@OreHQMobileForegroundService, NOTIFICATION_ID, t, acc.hashPower.toUInt(), acc.lastDifficulty.toUInt())
                        }
                    }
                }
            }

        }
    }

    /**
     * Stops the foreground service and removes the notification.
     * Can be called from inside or outside the service.
     */
    fun stopForegroundService() {
        stopSelf()
    }

    fun updateSelectedThreads(count: Int) {
        _threadCount.value.let {
            val newThreadsCount = when (count) {
                0 -> {
                    powerSlider = 0f
                    1
                }
                1 -> {
                    powerSlider = 1f
                    runtimeAvailableThreads / 3
                }
                2 -> {
                    powerSlider = 2f
                    runtimeAvailableThreads / 2
                }
                3 -> {
                    powerSlider = 3f
                    runtimeAvailableThreads - (runtimeAvailableThreads / 3)
                }
                4 -> {
                    powerSlider = 4f
                    runtimeAvailableThreads
                }
                else -> {
                    powerSlider = 4f
                    runtimeAvailableThreads
                }
            }
            _threadCount.value = newThreadsCount
            NotificationsHelper.updateNotification(this@OreHQMobileForegroundService, NOTIFICATION_ID, _threadCount.value, _hashpower.value, _difficulty.value)
        }
    }

    private fun getPubkey(): String? {
        return keypairRepository.getPubkey()?.toString()
    }

    companion object {
        private const val TAG = "OreHQMobileForegroundService"
    }
}
