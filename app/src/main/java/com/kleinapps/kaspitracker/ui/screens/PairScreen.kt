package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.sync.CloudError
import com.kleinapps.kaspitracker.sync.CoupleState
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.FieldLabel
import com.kleinapps.kaspitracker.ui.components.KaspiTextField
import com.kleinapps.kaspitracker.ui.components.LinkRow
import com.kleinapps.kaspitracker.ui.components.People
import com.kleinapps.kaspitracker.ui.components.PersonAvatar
import com.kleinapps.kaspitracker.ui.components.PrimaryButton
import com.kleinapps.kaspitracker.ui.components.RowDivider
import com.kleinapps.kaspitracker.ui.components.SecondaryButton
import com.kleinapps.kaspitracker.ui.components.SwitchRow
import com.kleinapps.kaspitracker.ui.components.rememberPeople
import com.kleinapps.kaspitracker.ui.components.syncAgo
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.PersonTone
import com.kleinapps.kaspitracker.ui.theme.ThemeController
import kotlinx.coroutines.delay

/**
 * Linking the two phones: who I am (name, colour), the state of the link, and
 * either a code to show / enter or the controls of an existing pair.
 */
@Composable
fun PairScreen(viewModel: PairViewModel = viewModel()) {
    val couple by viewModel.couple.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val hints by viewModel.hints.collectAsStateWithLifecycle()
    val people = rememberPeople()

    if (!viewModel.configured) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "Синхронизация не настроена",
                subtitle = "В этой сборке нет ключей Supabase — связать телефоны пока нельзя"
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (couple.paired) {
            LinkedCard(couple, people)
        } else {
            NotLinkedCard(
                couple = couple,
                people = people,
                busy = status.busy,
                onShowCode = viewModel::showCode,
                onCheck = viewModel::checkPending
            )
        }

        status.error?.let { ErrorNote(it) }

        ProfileCard(
            name = couple.myName,
            people = people,
            paired = couple.paired,
            onSave = viewModel::saveProfile
        )

        if (couple.paired) {
            var confirmUnlink by remember { mutableStateOf(false) }
            AppCard(contentPadding = 0.dp) {
                LinkRow(
                    icon = Icons.Outlined.Sync,
                    title = if (status.busy) "Синхронизирую…" else "Синхронизировать сейчас",
                    onClick = viewModel::syncNow,
                    showChevron = false
                )
                RowDivider()
                SwitchRow(
                    title = "Подсказывать «общее» по магазину",
                    subtitle = "Отметил магазин общим — следующие покупки там придут уже общими",
                    checked = hints,
                    onCheckedChange = viewModel::setHints,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                RowDivider()
                LinkRow(
                    icon = Icons.Outlined.LinkOff,
                    title = "Отвязать партнёра",
                    onClick = { confirmUnlink = true },
                    tint = Kaspi.colors.neg,
                    titleColor = Kaspi.colors.neg,
                    showChevron = false
                )
            }
            if (confirmUnlink) {
                AlertDialog(
                    onDismissRequest = { confirmUnlink = false },
                    containerColor = Kaspi.colors.surf,
                    title = { Text("Отвязать: ${people.partnerName}?", style = Kaspi.type.titleL) },
                    text = {
                        Text(
                            "Общие данные в облаке удалятся у обоих. На телефонах останутся только свои операции.",
                            style = Kaspi.type.bodyM,
                            color = Kaspi.colors.ink2
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmUnlink = false
                            viewModel.unpair()
                        }) { Text("Отвязать", color = Kaspi.colors.neg) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmUnlink = false }) { Text("Отмена", color = Kaspi.colors.ink) }
                    }
                )
            }
        } else {
            EnterCodeCard(busy = status.busy, onRedeem = viewModel::redeem)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LinkedCard(couple: CoupleState, people: People) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(64.dp)) {
                PersonAvatar(people.myInitial, people.myColor, size = 40.dp)
                PersonAvatar(
                    people.partnerInitial, people.partnerColor, size = 40.dp,
                    modifier = Modifier
                        .offset(x = 24.dp)
                        .border(2.dp, Kaspi.colors.surf, CircleShape)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = "Вы связаны · ${people.partnerName}", style = Kaspi.type.titleM, color = Kaspi.colors.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ok = couple.lastError == null
                    Box(Modifier.size(8.dp).background(if (ok) Kaspi.colors.pos else Kaspi.colors.warn, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (ok) syncAgo(couple.lastSyncAt).replaceFirstChar { it.uppercase() }
                        else "Последняя попытка не удалась",
                        style = Kaspi.type.bodyS,
                        color = Kaspi.colors.ink2
                    )
                }
            }
        }
    }
}

@Composable
private fun NotLinkedCard(
    couple: CoupleState,
    people: People,
    busy: Boolean,
    onShowCode: () -> Unit,
    onCheck: () -> Unit
) {
    val code = couple.pendingCode
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // While a code is on screen: tick the countdown and ask whether it was used.
    LaunchedEffect(code) {
        while (code != null) {
            now = System.currentTimeMillis()
            if (now > code.expiresAt) break
            onCheck()
            delay(3_000)
        }
    }
    val live = code != null && now < code.expiresAt

    AppCard(contentPadding = 20.dp) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.width(88.dp), contentAlignment = Alignment.CenterStart) {
                PersonAvatar(people.myInitial, people.myColor, size = 52.dp)
                Box(
                    modifier = Modifier
                        .offset(x = 36.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Kaspi.colors.surf)
                        .border(1.5.dp, Kaspi.colors.ink3, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Kaspi.colors.ink2)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text = "Вы ещё не связаны", style = Kaspi.type.titleL, color = Kaspi.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Свяжите телефоны, и появится вид «Вместе»: общие траты и кто сколько внёс. " +
                    "Каждый по-прежнему ведёт своё.",
                style = Kaspi.type.bodyS,
                color = Kaspi.colors.ink2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            if (live) {
                val left = ((code!!.expiresAt - now) / 1000).coerceAtLeast(0)
                Text(
                    text = code.code.chunked(4).joinToString("-"),
                    style = Kaspi.type.mono.copy(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 2.sp),
                    color = Kaspi.colors.ink
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Передай код партнёру · действует ещё %d:%02d".format(left / 60, left % 60),
                    style = Kaspi.type.label,
                    color = Kaspi.colors.ink2
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Жду, пока партнёр введёт код…",
                    style = Kaspi.type.label,
                    color = Kaspi.colors.accent
                )
            } else {
                Steps()
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (busy) "Получаю код…" else "Показать мой код",
                    onClick = onShowCode,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun Steps() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(
            "Один показывает код",
            "Второй вводит его у себя",
            "Отметки «общее» начнут синхронизироваться"
        ).forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(Kaspi.colors.accentSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "${i + 1}", style = Kaspi.type.label, color = Kaspi.colors.accent)
                }
                Spacer(Modifier.width(12.dp))
                Text(text = step, style = Kaspi.type.bodyM, color = Kaspi.colors.ink)
            }
        }
    }
}

@Composable
private fun ProfileCard(name: String, people: People, paired: Boolean, onSave: (String, PersonTone) -> Unit) {
    val tone by ThemeController.tone.collectAsStateWithLifecycle()
    var draft by rememberSaveable(name) { mutableStateOf(name) }
    AppCard {
        Text(text = "Я в приложении", style = Kaspi.type.titleM, color = Kaspi.colors.ink)
        Spacer(Modifier.height(12.dp))
        FieldLabel("Имя — его увидит партнёр")
        KaspiTextField(
            value = draft,
            onValueChange = { draft = it.take(20) },
            placeholder = "Как тебя зовут",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
        Spacer(Modifier.height(12.dp))
        FieldLabel("Мой цвет")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonTone.choices.forEach { option ->
                ColorSwatch(
                    color = option.color(Kaspi.colors.dark),
                    selected = option == tone,
                    onClick = { onSave(draft, option) }
                )
            }
            if (paired) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(12.dp).background(people.partnerColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(text = "цвет: ${people.partnerName}", style = Kaspi.type.label, color = Kaspi.colors.ink2)
            }
        }
        if (draft.trim() != name) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "Сохранить имя", onClick = { onSave(draft, tone) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, color, CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape)
            .background(color)
            .clickable(role = Role.RadioButton, onClick = onClick)
    )
}

@Composable
private fun EnterCodeCard(busy: Boolean, onRedeem: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val clean = code.replace("-", "").replace(" ", "")
    AppCard {
        Text(text = "Ввести код партнёра", style = Kaspi.type.titleM, color = Kaspi.colors.ink)
        Spacer(Modifier.height(12.dp))
        KaspiTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(9) },
            placeholder = "ABCD-EFGH",
            textStyle = Kaspi.type.mono.copy(fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 2.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = if (busy) "Связываю…" else "Связать",
            onClick = { onRedeem(clean) },
            enabled = clean.length == 8 && !busy,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ErrorNote(error: CloudError) {
    Text(
        text = when (error) {
            CloudError.CODE_INVALID -> "Такого кода нет — проверь буквы"
            CloudError.CODE_EXPIRED -> "Код устарел — попроси новый"
            CloudError.OWN_CODE -> "Это твой собственный код"
            CloudError.ALREADY_PAIRED -> "Кто-то из вас уже связан с другим человеком"
            CloudError.TOO_MANY_ATTEMPTS -> "Слишком много попыток — попробуй через час"
            CloudError.NETWORK -> "Нет интернета"
            CloudError.SERVER_UNAVAILABLE ->
                "Сервер не отвечает — возможно, проект Supabase на паузе. Разбуди его в панели Supabase"
            CloudError.NOT_CONFIGURED -> "Синхронизация не настроена в этой сборке"
            CloudError.UNKNOWN -> "Что-то пошло не так — попробуй ещё раз"
        },
        style = Kaspi.type.bodyM,
        color = Kaspi.colors.neg,
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(Kaspi.colors.tint(Kaspi.colors.neg, 0.1f))
            .padding(12.dp)
    )
}
