package com.example.orehqmobile.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "submission_results")
class SubmissionResult {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Int = 0

    @ColumnInfo(name = "poolDifficulty")
    var poolDifficulty: Int = 0
    @ColumnInfo(name = "poolEarned")
    var poolEarned: Long = 0
    @ColumnInfo(name = "minerPercentage")
    var minerPercentage: Double = 0.0
    @ColumnInfo(name = "minerDifficulty")
    var minerDifficulty: Int = 0
    @ColumnInfo(name = "minerEarned")
    var minerEarned: Long = 0
    @ColumnInfo(name = "createdAt", defaultValue = "CURRENT_TIMESTAMP")
    var createdAt: Long = 0

    constructor()

    constructor(
        poolDifficulty: Int,
        poolEarned: Long,
        minerPercentage: Double,
        minerDifficulty: Int,
        minerEarned: Long,
    ) {
        this.poolDifficulty = poolDifficulty
        this.poolEarned = poolEarned
        this.minerPercentage = minerPercentage
        this.minerDifficulty = minerDifficulty
        this.minerEarned = minerEarned
        this.createdAt = System.currentTimeMillis()
    }
}
