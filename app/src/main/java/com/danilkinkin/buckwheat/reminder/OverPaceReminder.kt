package com.danilkinkin.buckwheat.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.danilkinkin.buckwheat.MainActivity
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.budgetDataStore
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.dao.BudgetDao
import com.danilkinkin.buckwheat.di.budgetKey
import com.danilkinkin.buckwheat.di.currencyStoreKey
import com.danilkinkin.buckwheat.di.finishPeriodActualDateKey
import com.danilkinkin.buckwheat.di.finishPeriodDateKey
import com.danilkinkin.buckwheat.di.spentFromDailyBudgetKey
import com.danilkinkin.buckwheat.di.spentKey
import com.danilkinkin.buckwheat.di.startPeriodDateKey
import com.danilkinkin.buckwheat.util.calculatePace
import com.danilkinkin.buckwheat.util.numberFormat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date

val overPaceReminderStoreKey = booleanPreferencesKey("overPaceReminder")

private const val CHANNEL_ID = "over_pace"
private const val CHECK_ACTION = "com.danilkinkin.buckwheat.CHECK_PACE"
private const val REMINDER_HOUR = 19

object OverPaceReminder {
    fun isEnabled(context: Context) = context.budgetDataStore.data.map {
        it[overPaceReminderStoreKey] ?: false
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.budgetDataStore.edit {
            it[overPaceReminderStoreKey] = enabled
        }

        if (enabled) schedule(context) else cancel(context)
    }

    // Alarms are dropped on reboot and app update, so this is called on every app start
    fun restore(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            if (isEnabled(context).first()) schedule(context)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, OverPaceReceiver::class.java).setAction(CHECK_ACTION)

        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val firstTrigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            firstTrigger.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverPaceReceiverEntryPoint {
    fun budgetDao(): BudgetDao
}

class OverPaceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            OverPaceReminder.restore(context)
            return
        }

        if (intent.action != CHECK_ACTION) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkPace(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkPace(context: Context) {
        if (!OverPaceReminder.canNotify(context)) return

        val prefs = context.budgetDataStore.data.first()
        if (prefs[overPaceReminderStoreKey] != true) return

        val currency = prefs[currencyStoreKey]?.let { ExtendCurrency.getInstance(it) }
            ?: ExtendCurrency.none()
        val now = Date()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val budgetDao = EntryPointAccessors
            .fromApplication(context.applicationContext, OverPaceReceiverEntryPoint::class.java)
            .budgetDao()

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.over_pace_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )

        budgetDao.getAllOnce().forEach { budget ->
            val start = prefs[startPeriodDateKey(budget.uid)] ?: return@forEach
            val finish = prefs[finishPeriodDateKey(budget.uid)] ?: return@forEach

            if (prefs[finishPeriodActualDateKey(budget.uid)] != null) return@forEach
            if (finish < now.time) return@forEach

            fun value(key: androidx.datastore.preferences.core.Preferences.Key<String>) =
                prefs[key]?.toBigDecimal() ?: BigDecimal.ZERO

            val pace = calculatePace(
                budget = value(budgetKey(budget.uid)),
                startDate = Date(start),
                finishDate = Date(finish),
                spent = value(spentKey(budget.uid)) + value(spentFromDailyBudgetKey(budget.uid)),
                today = now,
            )

            if (pace.difference >= BigDecimal.ZERO) {
                notificationManager.cancel(budget.uid)
                return@forEach
            }

            val name = budget.name.ifBlank { context.getString(R.string.budget_default_name) }
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_balance_wallet)
                .setContentTitle(context.getString(R.string.over_pace_notification_title))
                .setContentText(
                    context.getString(
                        R.string.over_pace_notification_text,
                        name,
                        numberFormat(context, pace.difference.abs(), currency = currency),
                    )
                )
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(budget.uid, notification)
        }
    }
}
