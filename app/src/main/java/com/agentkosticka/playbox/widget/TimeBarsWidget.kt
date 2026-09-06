package com.agentkosticka.playbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.agentkosticka.playbox.MainActivity
import com.agentkosticka.playbox.R
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class TimeBarsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateAll(context)
    }

    override fun onDisabled(context: Context) {
        cancelIfUnused(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
        ) {
            if (widgetIds(context).isNotEmpty() || DashboardWidget.hasWidgets(context) || UtilityDashboardWidget.hasWidgets(context)) {
                updateAll(context)
                schedule(context)
            }
        }
    }

    companion object {
        private const val WORK_NAME = "time-bars-quarter-hour"
        private const val UTILITY_BURST_WINDOW_MS = 2_000L
        private var lastUtilityRefreshMs = -UTILITY_BURST_WINDOW_MS
        private var lastUtilityStateHash = 0
        private var lastUtilityWidgetSignature = 0

        fun cancelIfUnused(context: Context) {
            if (widgetIds(context).isEmpty() && !DashboardWidget.hasWidgets(context) && !UtilityDashboardWidget.hasWidgets(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        }

        fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TimeBarsWidget::class.java))

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TimeBarsWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        @Synchronized
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val now = ZonedDateTime.now()
            val timeSettings = TimeBarsSettings.load(context)
            val utilitySettings = UtilityWidgetSettings.load(context)
            DashboardWidget.updateAll(context, now)

            if (UtilityDashboardWidget.hasWidgets(context)) {
                val elapsed = SystemClock.elapsedRealtime()
                val stateHash = 31 * timeSettings.hashCode() + utilitySettings.hashCode()
                val widgetSignature = UtilityDashboardWidget.providers.fold(1) { hash, provider ->
                    31 * hash + manager.getAppWidgetIds(ComponentName(context, provider)).contentHashCode()
                }
                val burstExpired = elapsed - lastUtilityRefreshMs >= UTILITY_BURST_WINDOW_MS
                if (burstExpired || stateHash != lastUtilityStateHash || widgetSignature != lastUtilityWidgetSignature) {
                    UtilityDashboardWidget.updateAll(context, now)
                    lastUtilityRefreshMs = elapsed
                    lastUtilityStateHash = stateHash
                    lastUtilityWidgetSignature = widgetSignature
                }
            }

            widgetIds(context).forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_time_bars)
                views.setImageViewBitmap(R.id.time_bars_image, TimeBarsRenderer.render(now, timeSettings))
                views.setContentDescription(
                    R.id.time_bars_image,
                    timeProgress(now, timeSettings.weekStart).joinToString { "${it.label}: ${(it.fraction * 100).toInt()} percent" },
                )
                views.setOnClickPendingIntent(
                    R.id.time_bars_image,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java).putExtra("open_widgets", true),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                manager.updateAppWidget(id, views)
            }
        }
    }
}

class TimeBarsWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try {
        TimeBarsWidget.updateAll(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
