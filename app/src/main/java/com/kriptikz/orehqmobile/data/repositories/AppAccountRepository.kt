package com.kriptikz.orehqmobile.data.repositories

import android.util.Log
import com.kriptikz.orehqmobile.data.daos.AppAccountDao
import com.kriptikz.orehqmobile.data.entities.AppAccount
import com.kriptikz.orehqmobile.ui.screens.home_screen.HomeScreenViewModel
import com.kriptikz.orehqmobile.ui.screens.home_screen.HomeScreenViewModel.Companion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AppAccountRepository(private val appAccountDao: AppAccountDao) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun getAppAccount(): AppAccount? {
        val data =  appAccountDao.getAppAccount()
        return if (data.isNotEmpty()) {
            data.first()
        } else {
            null
        }
    }

    fun getAppAccountAsFlow(): Flow<List<AppAccount>> {
        return appAccountDao.getAppAccountAsFlow()
    }

    suspend fun insertAppAccount(newAppAccount: AppAccount) {
        appAccountDao.insertNewAppAccount(newAppAccount)
    }

    fun updateAppAccount(newAppAccountData: AppAccount): Int {
        return appAccountDao.updateAppAccount(newAppAccountData)
    }

    fun updateIsMining(newIsMining: Boolean, id: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            appAccountDao.updateIsMining(newIsMining, id)
        }
    }

    fun updateMiningPowerLevel(newLevel: Int, id: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            appAccountDao.updateMiningPowerLevel(newLevel, id)
        }
    }

    fun updateHashpower(newHashpower: Int, id: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            appAccountDao.updateHashpower(newHashpower, id)
        }
    }

    fun updateLastDifficulty(newLastDifficulty: Int, id: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            appAccountDao.updateLastDifficulty(newLastDifficulty, id)
        }
    }

    fun updateIsMiningSwitchOn(newIsMiningSwitchOn: Boolean, id: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            appAccountDao.updateIsMiningSwitchOn(newIsMiningSwitchOn, id)
        }
    }

    companion object {
        private const val TAG = "AppAccountRepository"
    }
}
