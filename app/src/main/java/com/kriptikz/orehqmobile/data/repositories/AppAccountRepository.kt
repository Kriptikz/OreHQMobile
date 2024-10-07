package com.kriptikz.orehqmobile.data.repositories

import com.kriptikz.orehqmobile.data.daos.AppAccountDao
import com.kriptikz.orehqmobile.data.entities.AppAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppAccountRepository(private val appAccountDao: AppAccountDao) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun getAppAccount(): AppAccount {
        return appAccountDao.getAppAccount()
    }

    fun insertAppAccount(newAppAccount: AppAccount) {
        coroutineScope.launch(Dispatchers.IO) {
            appAccountDao.insertNewAppAccount(newAppAccount)
        }
    }

    fun updateAppAccount(newAppAccountData: AppAccount): Int {
        return appAccountDao.updateAppAccount(newAppAccountData)
    }
}
