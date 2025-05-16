package com.example.apptodos

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TASK_ID = "TASK_ID"
        const val KEY_TASK_TITLE = "TASK_TITLE"
        const val KEY_LEAD_TIME_DESCRIPTION = "LEAD_TIME_DESCRIPTION" // Key baru
        const val CHANNEL_ID = "task_reminder_channel"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getInt(KEY_TASK_ID, 0)
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Tugas"
        val leadTimeDescription = inputData.getString(KEY_LEAD_TIME_DESCRIPTION) ?: "segera"

        Log.d("ReminderWorker", "Worker starting for task ID: $taskId, Title: $taskTitle, Lead Time: $leadTimeDescription")

        if (taskId == 0) {
            Log.e("ReminderWorker", "Invalid Task ID received.")
            return Result.failure()
        }

        try {
            Log.d("ReminderWorker", "Creating Intent for MainActivity.")
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                // Anda mungkin ingin menambahkan EXTRA_TASK_ID ke intent agar MainActivity bisa menyorot tugasnya
                // putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            }

            Log.d("ReminderWorker", "Creating PendingIntent.")
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                applicationContext,
                (taskId.toString() + leadTimeDescription).hashCode(),
                intent,
                pendingIntentFlags
            )

            Log.d("ReminderWorker", "Building notification.")
            val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_time)
                .setContentTitle("Pengingat Tugas: $taskTitle")
                // Gunakan leadTimeDescription di teks notifikasi
                .setContentText("Tugas '$taskTitle' akan jatuh tempo dalam $leadTimeDescription.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            Log.d("ReminderWorker", "Checking notification permission.")
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("ReminderWorker", "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
                return Result.failure()
            }

            Log.d("ReminderWorker", "Attempting to show notification with ID: $taskId for $leadTimeDescription")
            NotificationManagerCompat.from(applicationContext).notify((taskId.toString() + leadTimeDescription).hashCode(), builder.build())
            Log.d("ReminderWorker", "Notification shown successfully for task ID: $taskId, Lead Time: $leadTimeDescription")

        } catch (e: Exception) {
            Log.e("ReminderWorker", "Error in doWork for task ID: $taskId, Lead Time: $leadTimeDescription", e)
            return Result.failure()
        }

        return Result.success()
    }
}