package com.kriptikz.orehqmobile.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kriptikz.orehqmobile.data.entities.SubmissionResult

@Dao
interface SubmissionResultDao {
    @Insert
    fun insertSubmissionResult(sr: SubmissionResult)

    @Query("SELECT * FROM submission_results ORDER BY id DESC")
    fun getAllSubmissionResults(): List<SubmissionResult>
}
