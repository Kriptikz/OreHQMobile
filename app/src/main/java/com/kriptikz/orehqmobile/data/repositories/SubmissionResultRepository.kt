package com.kriptikz.orehqmobile.data.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kriptikz.orehqmobile.data.daos.SubmissionResultDao
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

class SubmissionResultRepository(private val submissionResultDao: SubmissionResultDao) {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    fun insertSubmissionResult(newSubmissionResult: SubmissionResult) {
        coroutineScope.launch(Dispatchers.IO) {
            submissionResultDao.insertSubmissionResult(newSubmissionResult)
        }
    }

    fun getAllSubmissionResults(): List<SubmissionResult> {
        return submissionResultDao.getAllSubmissionResults()
    }

    fun getAllSubmissionResultsAsFlow(): Flow<List<SubmissionResult>> {
        return submissionResultDao.getAllSubmissionResultsAsFlow()
    }
}
