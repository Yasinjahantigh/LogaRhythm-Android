package com.yasinjahantigh.logarhythm.domain.repository

import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeHistory(): Flow<List<DownloadItem>>

    /** Persists the request and schedules the download worker. Returns the download id. */
    suspend fun enqueue(result: DownloadSearchResult): String

    suspend fun cancel(id: String)

    suspend fun retry(id: String)

    suspend fun remove(id: String)

    suspend fun clearFinished()
}
