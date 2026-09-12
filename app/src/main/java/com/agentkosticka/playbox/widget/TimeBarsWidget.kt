package com.agentkosticka.playbox.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.agentkosticka.playbox.R
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class TimeBarsWidget : InstanceWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetInstanceSettings(context).load(it) }
        requestImmediateUpdate(context)
        schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        requestImmediateUpdate(context)
    }

    override fun onDisabled(context: Context) { cancelIfUnused(context) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            if (widgetIds(context).isNotEmpty() || DashboardWidget.hasWidgets(context) || UtilityDashboardWidget.hasWidgets(context) || AgendaWidget.hasWidgets(context) || NextEventWidget.hasWidgets(context)) {
                requestImmediateUpdate(context)
                schedule(context)
            }
        }
    }

    companion object {
        private const val WORK_NAME = "time-bars-quarter-hour"
        private const val IMMEDIATE_WORK_NAME = "time-bars-immediate"
        private const val UTILITY_BURST_WINDOW_MS = 2_000L
        private var lastUtilityRefreshMs = -UTILITY_BURST_WINDOW_MS
        private val updateRevision = java.util.concurrent.atomic.AtomicInteger()
        private var lastUtilityStateHash = 0
        private var lastUtilityWidgetSignature = 0

        fun cancelIfUnused(context: Context) {
            if (widgetIds(context).isEmpty() && !DashboardWidget.hasWidgets(context) && !UtilityDashboardWidget.hasWidgets(context) && !AgendaWidget.hasWidgets(context) && !NextEventWidget.hasWidgets(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
            }
        }

        fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TimeBarsWidget::class.java))

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<TimeBarsWorker>(15, TimeUnit.MINUTES).build())
        }

        fun requestImmediateUpdate(context: Context) {
            updateRevision.incrementAndGet()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TimeBarsWorker>().setInitialDelay(150, TimeUnit.MILLISECONDS).build(),
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
                val stateHash = 31 * timeSettings.hashCode() + utilitySettings.hashCode() + WidgetInstanceSettings(context).stateHash() + updateRevision.get()
                val widgetSignature = UtilityDashboardWidget.providers.fold(1) { hash, provider -> 31 * hash + manager.getAppWidgetIds(ComponentName(context, provider)).contentHashCode() }
                val burstExpired = elapsed - lastUtilityRefreshMs >= UTILITY_BURST_WINDOW_MS
                if (burstExpired || stateHash != lastUtilityStateHash || widgetSignature != lastUtilityWidgetSignature) {
                    UtilityDashboardWidget.updateAll(context, now)
                    lastUtilityRefreshMs = elapsed
                    lastUtilityStateHash = stateHash
                    lastUtilityWidgetSignature = widgetSignature
                }
            }

            AgendaWidget.updateAll(context, now)
            NextEventWidget.updateAll(context, now)
            widgetIds(context).forEach { id ->
                val timeSettings = WidgetInstanceSettings(context).load(id).time
                val views = sizedWidgetViews(manager.getAppWidgetOptions(id), 250, 110) { width, height ->
                    val views = RemoteViews(context.packageName, R.layout.widget_time_bars)
                    views.setThemedWidgetBitmap(context, R.id.time_bars_image) { palette -> TimeBarsRenderer.render(now, timeSettings, width, height, palette) }
                    views.setContentDescription(R.id.time_bars_image, timeProgress(now, timeSettings.weekStart).joinToString { "${it.label}: ${(it.fraction * 100).toInt()} percent" })
                    views.setOnClickPendingIntent(R.id.time_bars_image, widgetPendingIntent(context, WidgetDestination.TIME_BARS, id))
                    views
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}

class TimeBarsWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result = try { TimeBarsWidget.updateAll(applicationContext); Result.success() } catch (_: Exception) { Result.retry() }
}
