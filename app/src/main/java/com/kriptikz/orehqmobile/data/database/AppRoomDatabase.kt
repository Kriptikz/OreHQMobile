package com.kriptikz.orehqmobile.data.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kriptikz.orehqmobile.data.daos.AppAccountDao
import com.kriptikz.orehqmobile.data.daos.SubmissionResultDao
import com.kriptikz.orehqmobile.data.daos.WalletDao
import com.kriptikz.orehqmobile.data.entities.AppAccount
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import com.kriptikz.orehqmobile.data.entities.Wallet

@Database(
    version = 2,
    entities = [(Wallet::class), (SubmissionResult::class), (AppAccount::class)],
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
abstract class AppRoomDatabase: RoomDatabase() {

    abstract fun walletDao(): WalletDao
    abstract fun submissionResultDao(): SubmissionResultDao
    abstract fun appAccountDao(): AppAccountDao

    companion object {
        private var INSTANCE: AppRoomDatabase? = null

        fun getInstance(context: Context): AppRoomDatabase {
            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppRoomDatabase::class.java,
                        "app_database"
                    ).fallbackToDestructiveMigration()
                        .build()

                    INSTANCE = instance
                }

                instance.openHelper.writableDatabase

                return instance
            }
        }
    }
}