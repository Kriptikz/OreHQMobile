package com.kriptikz.orehqmobile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kriptikz.orehqmobile.data.repositories.KeypairRepository
import com.kriptikz.orehqmobile.service.OreHQMobileForegroundService
import com.kriptikz.orehqmobile.ui.OreHQMobileApp
import com.kriptikz.orehqmobile.ui.screens.home_screen.HomeScreenViewModel
import com.kriptikz.orehqmobile.ui.theme.OreHQMobileTheme
import com.kriptikz.orehqmobile.worker.MiningWorker
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ExecutionException


class MainActivity : ComponentActivity() {
    private lateinit var homeScreenViewModel: HomeScreenViewModel
    private var oreHQMobileService: OreHQMobileForegroundService? = null

    private var serviceBoundState by mutableStateOf(false)

    private var powerSlider by mutableStateOf(0f)

    private val workManager = WorkManager.getInstance(application)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(TAG, "onServiceConnected")

            val binder = service as OreHQMobileForegroundService.LocalBinder
            oreHQMobileService = binder.getService()

            if (powerSlider > 0f) {
                oreHQMobileService?.updateSelectedThreads(powerSlider.toInt())
            } else {
                powerSlider = oreHQMobileService?.powerSlider ?: 0f
            }
            serviceBoundState = true

            onServiceConnected()
        }

        override fun onServiceDisconnected(arg0: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")

            serviceBoundState = false
            oreHQMobileService = null
        }
    }

    // we need notification permission to be able to display a notification for the foreground service
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // if permission was denied, the service can still run only the notification won't be visible
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val workInfoStatuses = workManager.getWorkInfosByTag("MiningWorker")

        try {
            val workInfoList: List<WorkInfo> = workInfoStatuses.get()
            for (workInfo in workInfoList) {
                val state: WorkInfo.State = workInfo.state
                if ((state == WorkInfo.State.RUNNING) or (state == WorkInfo.State.ENQUEUED)) {
                    serviceBoundState = true
                }
            }
        } catch (e: ExecutionException) {
            e.printStackTrace()
            serviceBoundState = false
        } catch (e: InterruptedException) {
            e.printStackTrace()
            serviceBoundState = false
        }




        val keypairRepository = KeypairRepository(this);

        val hasEncryptedKeypair = keypairRepository.encryptedKeypairExists();

        val sender = ActivityResultSender(this)

        homeScreenViewModel = ViewModelProvider(this, HomeScreenViewModel.Factory)[HomeScreenViewModel::class.java]
        homeScreenViewModel.setIsMiningSwitchOn(serviceBoundState)

        setContent {
            OreHQMobileTheme(true) {
                OreHQMobileApp(
                    sender,
                    hasEncryptedKeypair,
                    setPowerSlider = ::onSetPowerSlider,
                    onClickService = ::onStartOrStopForegroundServiceClick,
                    serviceRunning = homeScreenViewModel.homeUiState.isMiningSwitchOn,
                    homeScreenViewModel
                )
            }
        }

        checkAndRequestNotificationPermission()
        if (serviceBoundState) {
            startForegroundService()
        }
        tryToBindToServiceIfRunning()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    private fun onSetPowerSlider(newValue: Float) {
        powerSlider = newValue
    }

    /**
     * Check for notification permission before starting the service so that the notification is visible
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    // permission already granted
                }

                else -> {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun onStartOrStopForegroundServiceClick() {
        if (serviceBoundState) {
            homeScreenViewModel.setIsMiningSwitchOn(false)
            workManager.cancelAllWorkByTag("MiningWorker")
            // service is already running, stop it
            oreHQMobileService?.stopForegroundService()
            serviceBoundState = false
        } else {
            homeScreenViewModel.setIsMiningSwitchOn(true)
            startForegroundService()
            val miningRequest = OneTimeWorkRequestBuilder<MiningWorker>()
                .addTag("MiningWorker")
                .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMillis(5000))
                .build()

            workManager.beginUniqueWork(
                "MiningWorker",
                ExistingWorkPolicy.KEEP,
                miningRequest
            ).enqueue()

            serviceBoundState = true
        }
    }

    /**
     * Creates and starts the OreHQMobileForgroundService as a foreground service.
     *
     * It also tries to bind to the service to update the UI with location updates.
     */
    private fun startForegroundService() {
        // start the service
        startForegroundService(Intent(this, OreHQMobileForegroundService::class.java))

        // bind to the service to update UI
        tryToBindToServiceIfRunning()
    }

    private fun tryToBindToServiceIfRunning() {
        Intent(this, OreHQMobileForegroundService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }

    private fun onServiceConnected() {
        lifecycleScope.launch {
            oreHQMobileService?.poolBalance?.collectLatest { it ->
                if (it > 0) {
                    homeScreenViewModel.setPoolbalance(it)
                }
            }
        }
        lifecycleScope.launch {
            oreHQMobileService?.poolMultiplier?.collectLatest { it ->
                if (it > 0) {
                    homeScreenViewModel.setPoolMultiplier(it)
                }
            }
        }
        lifecycleScope.launch {
            oreHQMobileService?.activeMiners?.collectLatest { it ->
                if (it > 0u) {
                    homeScreenViewModel.setActiveMiners(it.toInt())
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
