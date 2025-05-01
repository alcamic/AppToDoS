package com.example.apptodos

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        const val CHANNEL_ID = "task_reminder_channel"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getInt(KEY_TASK_ID, 0)
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Tugas"

        Log.d("ReminderWorker", "Worker running for task ID: $taskId, Title: $taskTitle")

        if (taskId == 0) {
            Log.e("ReminderWorker", "Invalid Task ID received.")
            return Result.failure()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext,
            taskId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // Recommended flags
        )


        // Buat notifikasi
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Pengingat Tugas: $taskTitle")
            .setContentText("Tugas '$taskTitle' akan jatuh tempo dalam 1 jam.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Tampilkan notifikasi
        with(NotificationManagerCompat.from(applicationContext)) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("ReminderWorker", "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
                return Result.failure()
            }
            notify(taskId, builder.build())
            Log.d("ReminderWorker", "Notification shown for task ID: $taskId")
        }

        return Result.success()
    }
}