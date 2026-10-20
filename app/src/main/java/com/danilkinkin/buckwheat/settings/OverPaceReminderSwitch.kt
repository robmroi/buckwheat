package com.danilkinkin.buckwheat.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.reminder.OverPaceReminder
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun OverPaceReminderSwitch() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val enabledFlow = remember { OverPaceReminder.isEnabled(context) }
    val enabled by enabledFlow.collectAsState(initial = false)

    fun toggle(value: Boolean) {
        if (
            value
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && !OverPaceReminder.canNotify(context)
        ) {
            context.findActivity()?.let {
                ActivityCompat.requestPermissions(
                    it,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    0,
                )
            }
        }

        coroutineScope.launch {
            OverPaceReminder.setEnabled(context, value)
        }
    }

    ButtonRow(
        icon = painterResource(R.drawable.ic_analytics),
        text = stringResource(R.string.over_pace_setting),
        description = stringResource(R.string.over_pace_setting_description),
        onClick = { toggle(!enabled) },
        endContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { toggle(it) },
            )
        },
    )
}
