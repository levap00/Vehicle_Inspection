package com.example.vehicleinspection

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val base = inputData.getString("base")?.trim().orEmpty()
        if (base.isEmpty()) return@withContext Result.failure()

        // Czytaj token JWT z SharedPreferences (zapisany przy logowaniu)
        val token = ApiConfig.getToken(applicationContext)
        if (token.isNullOrEmpty()) {
            Log.w("VI", "UploadWorker: brak tokenu auth, pomijam upload")
            return@withContext Result.failure()
        }

        val client = OkHttpClient()
        val pending = OfflineQueue.listDirs(applicationContext)
        if (pending.isEmpty()) return@withContext Result.success()

        for (dir in pending) {
            try {
                val payloadJson = OfflineQueue.readPayload(dir)

                // Wykryj typ payloadu → dobierz endpoint
                val payload = try { JSONObject(payloadJson) } catch (e: Exception) { JSONObject() }
                val payloadType = payload.optString("type", "")
                val payloadEntity = payload.optString("entity", "")
                val expenseTypes = setOf(
                    "expense",
                    "receipt",
                    "fuel",
                    "carwash",
                    "toll",
                    "parking",
                    "other",
                    "fuel_purchase",
                    "card_expense",
                    "cash_expense",
                    "private_expense"
                )
                val endpoint = when {
                    payloadEntity == "expense" || payloadType in expenseTypes -> "$base/api/expenses"
                    else -> "$base/api/inspections"
                }

                val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("payload", payloadJson)

                // Dodaj wszystkie zdjęcia z katalogu offline queue
                val jpgs = dir.listFiles { f -> f.name.endsWith(".jpg") } ?: emptyArray()
                for (f in jpgs) {
                    val field = f.name.removeSuffix(".jpg")
                    mb.addFormDataPart(field, f.name, f.asRequestBody("image/jpeg".toMediaType()))
                }

                val req = Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer $token")
                    .post(mb.build())
                    .build()

                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    Log.d("VI", "UPLOAD ${dir.name}: HTTP ${resp.code} $body")
                    if (resp.isSuccessful) {
                        OfflineQueue.delete(dir)
                    } else {
                        return@withContext Result.retry()
                    }
                }
            } catch (e: Exception) {
                Log.e("VI", "UPLOAD exception for ${dir.name}", e)
                return@withContext Result.retry()
            }
        }

        Result.success()
    }

    companion object {
        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueueOnce(context: Context) {
            val work = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf("base" to ApiConfig.API_BASE))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("vi_upload_once", ExistingWorkPolicy.REPLACE, work)
        }

        fun ensurePeriodic(context: Context) {
            val work = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setInputData(workDataOf("base" to ApiConfig.API_BASE))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("vi_upload_periodic", ExistingPeriodicWorkPolicy.KEEP, work)
        }
    }
}
