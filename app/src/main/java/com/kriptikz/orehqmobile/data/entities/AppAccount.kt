package com.kriptikz.orehqmobile.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_accounts")
class AppAccount {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Int = 0

    @ColumnInfo(name = "publicKey")
    var publicKey: String = ""

    @ColumnInfo(name = "isSignedUp")
    var isSignedUp: Boolean = false

    constructor()

    constructor(publicKey: String, isSignedUp: Boolean) {
        this.publicKey = publicKey
        this.isSignedUp = isSignedUp
    }
}