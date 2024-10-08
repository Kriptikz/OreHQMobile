package com.kriptikz.orehqmobile.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kriptikz.orehqmobile.data.entities.AppAccount
import com.kriptikz.orehqmobile.data.entities.SubmissionResult

@Dao
interface AppAccountDao {
    @Insert
    fun insertNewAppAccount(appAccount: AppAccount)

    @Query("SELECT * FROM app_accounts ORDER BY id DESC LIMIT 1")
    fun getAppAccount(): AppAccount

    @Update
    fun updateAppAccount(newAppAccountData: AppAccount): Int
}