package com.kleinapps.kaspitracker.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.service.ListenerWatchdog
import com.kleinapps.kaspitracker.sync.CoupleStore
import com.kleinapps.kaspitracker.ui.components.KaspiTextField
import com.kleinapps.kaspitracker.ui.components.PrimaryButton
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.Radius
import com.kleinapps.kaspitracker.util.isNotificationAccessGranted

/** Whether the first-run walkthrough was already shown (or not needed). */
object Onboarding {

    private const val PREFS = "settings"
    private const val KEY = "onboarding_done"

    /**
     * True on a phone that never saw the app work. A phone that already has
     * notification access or has caught a Kaspi push — the owner's own, after
     * this update — skips straight to the app.
     */
    fun needed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return false
        val existingUser = isNotificationAccessGranted(context) || ListenerWatchdog.lastNotificationAt(context) != null
        if (existingUser) markDone(context)
        return !existingUser
    }

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

private data class Step(
    val icon: ImageVector,
    val title: String,
    val text: String,
    val button: String
)

private val STEPS = listOf(
    Step(
        Icons.Outlined.AutoStories,
        "Наш денежный дневник",
        "Приложение само записывает траты из уведомлений Kaspi, раскладывает их по категориям и показывает, " +
            "куда уходят деньги — твои, партнёра и общие.",
        "Начать"
    ),
    Step(
        Icons.Outlined.NotificationsActive,
        "Доступ к уведомлениям",
        "Без него траты из Kaspi не записываются. Текст уведомлений остаётся на телефоне.",
        "Открыть настройки доступа"
    ),
    Step(
        Icons.Outlined.BatterySaver,
        "Не усыплять приложение",
        "Samsung любит выключать фоновые приложения. Сними ограничения батареи, чтобы операции не терялись.",
        "Снять ограничения"
    ),
    Step(
        Icons.Outlined.Badge,
        "Как тебя зовут?",
        "Имя увидит партнёр в переключателе видов.",
        "Дальше"
    ),
    Step(
        Icons.Outlined.FavoriteBorder,
        "Связаться с партнёром",
        "Покажи код на своём телефоне или введи код с телефона партнёра. Можно сделать позже из инструментов.",
        "Связать телефоны"
    )
)

/**
 * Five short steps on a fresh install: what the app does, the two system
 * switches it cannot work without, a name, and pairing (skippable).
 */
@Composable
fun OnboardingScreen(onFinish: (openPairing: Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(CoupleStore.state.value.myName) }
    val current = STEPS[step]

    fun finish(openPairing: Boolean) {
        if (name.isNotBlank()) CoupleStore.update { it.copy(myName = name.trim()) }
        Onboarding.markDone(context)
        onFinish(openPairing)
    }

    BackHandler(enabled = step > 0) { step-- }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Kaspi.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            if (step > 0) {
                IconButton(onClick = { step-- }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = Kaspi.colors.ink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STEPS.indices.forEach { i ->
                    val width by animateDpAsState(if (i == step) 24.dp else 8.dp, label = "dot")
                    Box(
                        Modifier
                            .width(width)
                            .height(8.dp)
                            .background(if (i <= step) Kaspi.colors.accent else Kaspi.colors.line, RoundedCornerShape(50))
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Kaspi.colors.accentSoft, RoundedCornerShape(Radius.card)),
            contentAlignment = Alignment.Center
        ) {
            Icon(current.icon, contentDescription = null, tint = Kaspi.colors.accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(text = "Шаг ${step + 1} из ${STEPS.size}", style = Kaspi.type.label, color = Kaspi.colors.ink2)
        Spacer(Modifier.height(8.dp))
        Text(text = current.title, style = Kaspi.type.headline, color = Kaspi.colors.ink)
        Spacer(Modifier.height(12.dp))
        Text(text = current.text, style = Kaspi.type.bodyM, color = Kaspi.colors.ink2)
        if (step == 3) {
            Spacer(Modifier.height(20.dp))
            KaspiTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                placeholder = "Например, Нурлан",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        }
        Spacer(Modifier.weight(1.4f))

        PrimaryButton(
            text = current.button,
            modifier = Modifier.fillMaxWidth(),
            enabled = step != 3 || name.isNotBlank(),
            onClick = {
                when (step) {
                    1 -> context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    2 -> runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                    4 -> {
                        finish(openPairing = true)
                        return@PrimaryButton
                    }
                }
                // The settings screens open on top; coming back lands on the next step.
                step++
            }
        )
        Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            if (step >= 1) {
                TextButton(onClick = { if (step == STEPS.lastIndex) finish(openPairing = false) else step++ }) {
                    Text(
                        text = if (step == STEPS.lastIndex) "Пропустить, сделаю позже" else "Пропустить",
                        style = Kaspi.type.bodyM,
                        color = Kaspi.colors.accent
                    )
                }
            }
        }
    }
}
