package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AgriStockDatabase
import com.example.data.repository.ScanRepository
import java.util.concurrent.TimeUnit

class SyncPendingScansWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncPendingScansWorker started")
        return try {
            val db = AgriStockDatabase.getDatabase(applicationContext)
            val scanRepository = ScanRepository(db.scanTransactionDao(), db.productDao(), db.staffDao())
            val syncedCount = scanRepository.syncPendingScans()
            Log.d(TAG, "SyncPendingScansWorker finished, synced $syncedCount scans")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "SyncPendingScansWorker error: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncPendingScansWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncPendingScansWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "sync_pending_scans",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
