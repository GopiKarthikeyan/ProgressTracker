package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Cream
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.NeonAccent

@Composable
fun BuilderColumn(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Cream),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = verticalArrangement,
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.heightIn(min = TouchTargets.Editor),
                        ) {
                            Text(stringResource(R.string.builder_back))
                        }
                    }
                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                }
                content()
            },
        )
    }
}

@Composable
fun EditorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchTargets.Editor),
    ) {
        Text(label)
    }
}

@Composable
fun DeleteIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.builder_delete)
    TextButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = TouchTargets.Editor, minHeight = TouchTargets.Editor)
            .semantics { contentDescription = description },
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, tint = NeonAccent)
    }
}

@Composable
fun NumericEntry(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    onValue: (Int?) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 72.dp, max = 112.dp),
        )
        EditorButton(
            label = stringResource(R.string.builder_minus),
            onClick = { onValue(value?.minus(1)?.coerceAtLeast(0)) },
        )
        NumericText(
            text = value?.toString() ?: stringResource(R.string.builder_none),
            color = NeonAccent,
            modifier = Modifier.widthIn(min = 48.dp),
            textAlign = TextAlign.Center,
        )
        EditorButton(
            label = stringResource(R.string.builder_plus),
            onClick = { onValue((value ?: 0) + 1) },
        )
        if (value != null) {
            EditorButton(label = stringResource(R.string.builder_none), onClick = { onValue(null) })
        }
    }
}

@Composable
fun KgEntry(
    label: String,
    value: Double?,
    onValue: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label)
        NumericText(
            text = value?.let(::formatKg) ?: stringResource(R.string.builder_none),
            color = NeonAccent,
        )
        OutlinedTextField(
            value = value?.let(::formatKg).orEmpty(),
            onValueChange = { raw ->
                onValue(raw.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull())
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = numericFieldStyle(),
            singleLine = true,
        )
    }
}

internal fun formatKg(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun numericFieldStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum",
)
