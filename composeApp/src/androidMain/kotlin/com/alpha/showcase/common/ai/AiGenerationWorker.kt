package com.alpha.showcase.common.ai

import AndroidApp
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ai_creation_center_title
import showcaseapp.composeapp.generated.resources.ai_status_running

internal actual fun scheduleAiBackgroundWork() {
    WorkManager.getInstance(AndroidApp).enqueueUniqueWork(
        "showcase-ai-generation", ExistingWorkPolicy.APPEND_OR_REPLACE,
        OneTimeWorkRequestBuilder<AiGenerationWorker>().build(),
    )
}

/** Durable queue execution stays in the common engine; Android owns its foreground lifetime. */
class AiGenerationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            setForeground(getForegroundInfo())
            val engine = AiServices.engine
            engine.initialize()
            engine.library.first { library -> library.tasks.all { it.status.terminal } }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = getString(Res.string.ai_creation_center_title)
        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(title)
            .setContentText(getString(Res.string.ai_status_running))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL = "showcase-ai-generation"
        const val NOTIFICATION_ID = 7258
    }
}
