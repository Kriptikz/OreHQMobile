package com.kriptikz.orehqmobile.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kriptikz.orehqmobile.data.entities.AppAccount
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import kotlinx.coroutines.flow.Flow

@Dao
interface AppAccountDao {
    @Insert
    fun insertNewAppAccount(appAccount: AppAccount)

    @Query("SELECT * FROM app_accounts ORDER BY id DESC LIMIT 1")
    fun getAppAccount(): List<AppAccount>

    @Query("SELECT * FROM app_accounts ORDER BY id DESC LIMIT 1")
    fun getAppAccountAsFlow(): Flow<List<AppAccount>>

    @Update
    fun updateAppAccount(newAppAccountData: AppAccount): Int

    @Query("UPDATE app_accounts SET isMining = :isMining WHERE id = :id")
    fun updateIsMining(isMining: Boolean, id: Int): Int

    @Query("UPDATE app_accounts SET miningPowerLevel = :newLevel WHERE id = :id")
    fun updateMiningPowerLevel(newLevel: Int, id: Int): Int

    @Query("UPDATE app_accounts SET hashPower = :newHashpower WHERE id = :id")
    fun updateHashpower(newHashpower: Int, id: Int): Int

    @Query("UPDATE app_accounts SET lastDifficulty = :newLastDifficulty WHERE id = :id")
    fun updateLastDifficulty(newLastDifficulty: Int, id: Int): Int

    @Query("UPDATE app_accounts SET isMiningSwitchOn = :newIsMiningSwitchOn WHERE id = :id")
    fun updateIsMiningSwitchOn(newIsMiningSwitchOn: Boolean, id: Int): Int
}