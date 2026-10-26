package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kleinapps.kaspitracker.ui.theme.ThemeController
import com.kleinapps.kaspitracker.ui.theme.ThemeMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.Radius

/** Main action: a full accent pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = Kaspi.colors.accent
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Kaspi.colors.onAcc,
            // Disabled keeps the accent, faded: the button is still recognisable.
            disabledContainerColor = color.copy(alpha = 0.38f),
            disabledContentColor = Kaspi.colors.onAcc.copy(alpha = 0.9f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = Kaspi.type.bodyL.copy(fontWeight = Kaspi.type.titleM.fontWeight))
    }
}

/** Secondary action: outlined pill on the card. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Kaspi.colors.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Kaspi.colors.ink),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = Kaspi.type.bodyL.copy(fontWeight = Kaspi.type.titleM.fontWeight))
    }
}

/**
 * Joined segments with a check on the chosen one — operation type in the add
 * form and the edit sheet.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Radius.icon)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Kaspi.colors.line, shape)
            .height(48.dp)
    ) {
        options.forEachIndexed { index, (value, label) ->
            val active = value == selected
            if (index > 0) Box(Modifier.width(1.dp).height(48.dp).background(Kaspi.colors.line))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(if (active) Kaspi.colors.accentSoft else Kaspi.colors.surf)
                    .clickable(role = Role.RadioButton) { onSelect(value) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (active) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Kaspi.colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = Kaspi.type.bodyM,
                    color = if (active) Kaspi.colors.accent else Kaspi.colors.ink,
                    maxLines = 1
                )
            }
        }
    }
}

/** "Своё / Общее" — two separate outlined cards, the chosen one in the accent. */
@Composable
fun SharedChoice(
    shared: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceCard(Icons.Outlined.Person, "Своё", !shared, { onChange(false) }, Modifier.weight(1f))
        ChoiceCard(Icons.Outlined.Group, "Общее", shared, { onChange(true) }, Modifier.weight(1f))
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Radius.icon)
    val tint = if (active) Kaspi.colors.accent else Kaspi.colors.ink
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (active) Kaspi.colors.accentSoft else Kaspi.colors.surf)
            .border(1.dp, if (active) Kaspi.colors.accent.copy(alpha = 0.5f) else Kaspi.colors.line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = Kaspi.type.bodyM, color = tint)
    }
}

/** Filled, softly outlined field; the label sits above it, not inside. */
@Composable
fun KaspiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    textStyle: TextStyle = Kaspi.type.bodyL,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = textStyle,
        placeholder = placeholder?.let { { Text(it, style = textStyle, color = Kaspi.colors.ink3) } },
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(Radius.field),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Kaspi.colors.bg,
            unfocusedContainerColor = Kaspi.colors.bg,
            errorContainerColor = Kaspi.colors.bg,
            unfocusedBorderColor = Kaspi.colors.line,
            focusedBorderColor = Kaspi.colors.accent,
            errorBorderColor = Kaspi.colors.neg,
            cursorColor = Kaspi.colors.accent,
            focusedTextColor = Kaspi.colors.ink,
            unfocusedTextColor = Kaspi.colors.ink
        )
    )
}

/** Label above a field or a group: "Сумма, ₸", "Чья трата". */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Kaspi.type.bodyS,
        color = Kaspi.colors.ink2,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/**
 * Pill filter chip. Selected is a full accent fill; a selected chip that can be
 * cleared shows a cross.
 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    closable: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) Kaspi.colors.accent else Kaspi.colors.surf)
            .border(1.dp, if (selected) Kaspi.colors.accent else Kaspi.colors.line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val content = if (selected) Kaspi.colors.onAcc else Kaspi.colors.ink
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = label, style = Kaspi.type.bodyM, color = content, maxLines = 1)
        if (selected && closable) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Close, contentDescription = "Убрать фильтр", tint = content, modifier = Modifier.size(16.dp))
        }
    }
}

/** Two-way tab on a track: "По месяцам / Общая". */
@Composable
fun <T> TabSwitch(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.field))
            .background(Kaspi.colors.surf2)
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(Radius.icon))
                    .background(if (active) Kaspi.colors.surf else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = Kaspi.type.bodyM.copy(fontWeight = Kaspi.type.titleM.fontWeight),
                    color = if (active) Kaspi.colors.ink else Kaspi.colors.ink2
                )
            }
        }
    }
}

/** Light → dark → system, with the current mode's glyph. */
@Composable
fun ThemeToggleButton() {
    val mode by ThemeController.mode.collectAsStateWithLifecycle()
    IconButton(onClick = { ThemeController.cycle() }) {
        Icon(
            imageVector = when (mode) {
                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                ThemeMode.DARK -> Icons.Outlined.DarkMode
                ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
            },
            contentDescription = when (mode) {
                ThemeMode.LIGHT -> "Тема: светлая"
                ThemeMode.DARK -> "Тема: тёмная"
                ThemeMode.SYSTEM -> "Тема: как в системе"
            },
            tint = Kaspi.colors.ink
        )
    }
}
