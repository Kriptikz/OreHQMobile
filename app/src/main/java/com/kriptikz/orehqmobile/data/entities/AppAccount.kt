package com.kriptikz.orehqmobile.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.min

@Entity(tableName = "app_accounts")
class AppAccount {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Int = 0

    @ColumnInfo(name = "publicKey")
    var publicKey: String = ""

    @ColumnInfo(name = "isSignedUp")
    var isSignedUp: Boolean = false

    @ColumnInfo(name = "isMining", defaultValue = "false")
    var isMining: Boolean = false

    @ColumnInfo(name = "miningPowerLevel", defaultValue = "0")
    var miningPowerLevel: Int = 0

    @ColumnInfo(name = "hashPower", defaultValue = "0")
    var hashPower: Int = 0

    @ColumnInfo(name = "lastDifficulty", defaultValue = "0")
    var lastDifficulty: Int = 0

    @ColumnInfo(name = "isMiningSwitchOn", defaultValue = "false")
    var isMiningSwitchOn: Boolean = false

    constructor()

    constructor(publicKey: String, isSignedUp: Boolean, isMining: Boolean, miningPowerLevel: Int, hashPower: Int, lastDifficulty: Int, isMiningSwitchOn: Boolean) {
        this.publicKey = publicKey
        this.isSignedUp = isSignedUp
        this.isMining = isMining
        this.isMiningSwitchOn = isMiningSwitchOn
        this.miningPowerLevel = miningPowerLevel
        this.hashPower = hashPower
        this.lastDifficulty = hashPower
    }
}