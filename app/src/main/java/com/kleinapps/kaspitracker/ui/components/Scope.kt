package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kleinapps.kaspitracker.data.Owner
import com.kleinapps.kaspitracker.data.ViewScope
import com.kleinapps.kaspitracker.sync.CoupleState
import com.kleinapps.kaspitracker.sync.CoupleStore
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.PersonTone
import com.kleinapps.kaspitracker.ui.theme.ThemeController
import com.kleinapps.kaspitracker.ui.theme.usColor

/** The two of us as the screens draw us: names, initials and colours in the current theme. */
data class People(
    val myName: String,
    val myColor: Color,
    val partnerName: String,
    val partnerColor: Color,
    val usColor: Color
) {
    val myInitial: String get() = initialOf(myName, "Я")
    val partnerInitial: String get() = initialOf(partnerName, "П")

    fun nameOf(owner: Owner) = if (owner == Owner.ME) myName else partnerName

    fun mark(owner: Owner): OwnerMark =
        if (owner == Owner.ME) OwnerMark(myInitial, myColor) else OwnerMark(partnerInitial, partnerColor)

    fun accentOf(scope: ViewScope): Color = when (scope) {
        ViewScope.ME -> myColor
        ViewScope.PARTNER -> partnerColor
        ViewScope.TOGETHER -> usColor
    }

    private fun initialOf(name: String, fallback: String) =
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: fallback
}

/** Current [People], following the pairing state, the user's colour and the theme. */
@Composable
fun rememberPeople(): People {
    val couple by CoupleStore.state.collectAsStateWithLifecycle()
    val tone by ThemeController.tone.collectAsStateWithLifecycle()
    return peopleOf(couple, tone, Kaspi.colors.dark)
}

fun peopleOf(couple: CoupleState, myTone: PersonTone, dark: Boolean): People {
    // The partner keeps their own colour unless it is the same as mine — then
    // they get plum, so the two of us never look alike on one screen.
    val partnerTone = PersonTone.choices.getOrNull(couple.partnerColor)
        ?.takeIf { it != myTone }
        ?: PersonTone.PLUM
    return People(
        myName = couple.myName.ifBlank { "Я" },
        myColor = myTone.color(dark),
        partnerName = couple.partnerName.ifBlank { "Партнёр" },
        partnerColor = partnerTone.color(dark),
        usColor = usColor(dark)
    )
}

/**
 * "Нурлан · Аня · Вместе" under a tab's title. Shown only while paired; the
 * choice is remembered.
 */
@Composable
fun ScopeBar(modifier: Modifier = Modifier) {
    val couple by CoupleStore.state.collectAsStateWithLifecycle()
    if (!couple.paired) return
    val people = rememberPeople()
    val scope = couple.effectiveScope
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Kaspi.colors.surf2)
            .padding(4.dp)
    ) {
        ScopeSegment(
            label = people.myName,
            selected = scope == ViewScope.ME,
            onClick = { CoupleStore.update { it.copy(scope = ViewScope.ME) } },
            modifier = Modifier.weight(1f)
        ) { ring -> PersonAvatar(people.myInitial, people.myColor, size = 26.dp, ring = ring) }
        ScopeSegment(
            label = people.partnerName,
            selected = scope == ViewScope.PARTNER,
            onClick = { CoupleStore.update { it.copy(scope = ViewScope.PARTNER) } },
            modifier = Modifier.weight(1f)
        ) { ring -> PersonAvatar(people.partnerInitial, people.partnerColor, size = 26.dp, ring = ring) }
        ScopeSegment(
            label = "Вместе",
            selected = scope == ViewScope.TOGETHER,
            onClick = { CoupleStore.update { it.copy(scope = ViewScope.TOGETHER) } },
            modifier = Modifier.weight(1f)
        ) { _ ->
            Box(
                modifier = Modifier.size(26.dp).background(people.usColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Kaspi.colors.onAcc, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun ScopeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable (ring: Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Kaspi.colors.surf else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        avatar(selected)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = if (selected) Kaspi.type.bodyL.copy(fontWeight = Kaspi.type.titleM.fontWeight) else Kaspi.type.bodyM,
            color = if (selected) Kaspi.colors.ink else Kaspi.colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "Данные Ани · только просмотр · синхр. 5 мин назад" — under the switcher in her view. */
@Composable
fun PartnerViewNote(couple: CoupleState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Visibility, contentDescription = null, tint = Kaspi.colors.ink2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Только просмотр · ${syncAgo(couple.lastSyncAt)}",
            style = Kaspi.type.label,
            color = Kaspi.colors.ink2
        )
    }
}

/** "синхр. только что" / "синхр. 5 мин назад" / "синхр. 3 ч назад". */
fun syncAgo(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0) return "ещё не синхронизировано"
    val minutes = (now - at) / 60_000
    return when {
        minutes < 1 -> "синхр. только что"
        minutes < 60 -> "синхр. $minutes мин назад"
        minutes < 48 * 60 -> "синхр. ${minutes / 60} ч назад"
        else -> "синхр. ${minutes / (24 * 60)} дн. назад"
    }
}
