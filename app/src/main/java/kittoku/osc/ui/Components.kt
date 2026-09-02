package kittoku.osc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import home.keenetic.sstp.R


@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
internal fun SettingRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )

            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }

        if (trailing != null) {
            Row(modifier = Modifier.padding(start = 12.dp)) { trailing() }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
    ) {
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun NavigationRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        SettingRow(
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = onClick,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

/** Строка со значением-текстом: тап открывает диалог редактирования. */
@Composable
internal fun TextSettingRow(
    title: String,
    value: String,
    placeholder: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    isMultiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    note: String? = null,
    onValueChange: (String) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    val summary = when {
        value.isEmpty() -> placeholder ?: "—"
        isPassword -> "•".repeat(value.length.coerceAtMost(12))
        isMultiline -> value.lineSequence().first() + if (value.lines().size > 1) " …" else ""
        else -> value
    }

    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        TextInputDialog(
            title = title,
            initialValue = value,
            placeholder = placeholder,
            isPassword = isPassword,
            isMultiline = isMultiline,
            keyboardType = keyboardType,
            note = note,
            onDismiss = { isDialogShown = false },
            onConfirm = {
                onValueChange(it)
                isDialogShown = false
            },
        )
    }
}

@Composable
internal fun TextInputDialog(
    title: String,
    initialValue: String,
    placeholder: String? = null,
    isPassword: Boolean = false,
    isMultiline: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    note: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    var isRevealed by remember { mutableStateOf(!isPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = !isMultiline,
                    placeholder = placeholder?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (isRevealed) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = if (isPassword) {
                        {
                            IconButton(onClick = { isRevealed = !isRevealed }) {
                                Icon(
                                    imageVector = if (isRevealed) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (isRevealed) R.string.hide_password else R.string.show_password
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Выпадающий список. Хранимое значение и подпись разделены намеренно: значение
 *  уходит в SharedPreferences и в экспортируемые профили, переводить его нельзя. */
@Composable
internal fun DropdownSettingRow(
    title: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    labelOf: @Composable (String) -> String = { it },
    onValueChange: (String) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        SettingRow(
            title = title,
            summary = labelOf(value),
            enabled = enabled,
            onClick = { isExpanded = true },
        )

        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onValueChange(option)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun MultiSelectSettingRow(
    title: String,
    summary: String,
    options: List<String>,
    selected: Set<String>,
    enabled: Boolean = true,
    onSelectionChange: (Set<String>) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        var current by remember { mutableStateOf(selected) }

        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    current = if (option in current) {
                                        current - option
                                    } else {
                                        current + option
                                    }
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = option in current,
                                onCheckedChange = {
                                    current = if (it) current + option else current - option
                                },
                            )

                            Text(text = option, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelectionChange(current)
                        isDialogShown = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun IntSettingRow(
    title: String,
    value: Int,
    summary: String? = null,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    TextSettingRow(
        title = title,
        value = value.toString(),
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        note = summary,
        onValueChange = { text -> text.trim().toIntOrNull()?.also(onValueChange) },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String? = null,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
internal fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
