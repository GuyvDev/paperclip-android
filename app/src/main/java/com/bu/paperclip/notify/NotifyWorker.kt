package com.bu.paperclip.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bu.paperclip.MainActivity
import com.bu.paperclip.R
import com.bu.paperclip.data.AppState
import com.bu.paperclip.data.Repo
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.ApiException
import com.bu.paperclip.util.parseInstant
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Periodically checks the attention feed and notifies about items that arrived since the last check. */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val s = AppState.settings.current()
        if (!s.notifications || s.baseUrl.isEmpty() || s.companyId.isEmpty()) return Result.success()
        if (Api.baseUrl.isEmpty()) Api.configure(s.baseUrl, s.apiToken)
        val since = parseInstant(s.lastNotifiedAt) ?: Instant.now().minusSeconds(3600)
        return try {
            val feed = Repo.attention(s.companyId, limit = 30)
            val fresh = feed.items.filter { (parseInstant(it.activityAt ?: it.createdAt) ?: Instant.EPOCH).isAfter(since) }
            fresh.take(5).forEach { item ->
                val title = when (item.sourceKind) {
                    "issue_thread_interaction" -> "${item.originAgentName ?: "An agent"} needs your input"
                    "approval" -> "Approval requested"
                    "failed_run" -> "Run failed"
                    "budget_alert" -> "Budget alert"
                    "agent_error_alert" -> "Agent error"
                    "decision" -> "Decision needed"
                    else -> "Needs attention"
                }
                val ref = item.relatedIssue?.identifier ?: item.subject.identifier
                val body = listOfNotNull(ref, item.subject.title ?: item.relatedIssue?.title, item.whyNow).joinToString(" · ")
                val link = item.relatedIssue?.identifier?.let { "/issues/$it" } ?: item.subject.href
                notify(applicationContext, item.id.hashCode(), title, body, link)
            }
            if (fresh.size > 5) notify(applicationContext, 1, "${fresh.size} items need attention", "Open the inbox to review them", "/inbox")
            AppState.settings.setLastNotifiedAt(Instant.now().toString())
            Result.success()
        } catch (e: ApiException.AuthRequired) {
            if (s.lastNotifiedAt != "auth") {
                notify(applicationContext, 2, "Paperclip sign-in expired", "Open the app to sign in again so notifications keep working.", null)
                AppState.settings.setLastNotifiedAt("auth")
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val CHANNEL = "attention"
        private const val NAME = "paperclip-attention"

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Agent requests", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }

        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("$NAME-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<NotifyWorker>().build())
        }

        fun notify(ctx: Context, id: Int, title: String, body: String, link: String?) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (link != null) putExtra(MainActivity.EXTRA_LINK, link)
            }
            val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(id, n)
        }
    }
}
