package com.forge.hypertrophy.ui.screens.dayplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.dayplan.DayPlanCtaKind
import com.forge.hypertrophy.domain.dayplan.DayPlanRow
import com.forge.hypertrophy.domain.dayplan.DayPlanSection
import com.forge.hypertrophy.domain.dayplan.DayPlanSectionKind
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.usecase.DayPlanCommitment
import com.forge.hypertrophy.ui.components.BackButton
import com.forge.hypertrophy.ui.components.ExerciseSilhouette
import com.forge.hypertrophy.ui.components.PillShape
import com.forge.hypertrophy.ui.components.PrimaryButton
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Rose
import com.forge.hypertrophy.ui.theme.White
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DayPlanScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenDayEditor: (Long) -> Unit,
    onOpenSlotEditor: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.sessionToOpen) {
        val id = state.sessionToOpen ?: return@LaunchedEffect
        onOpenWorkout(id)
        viewModel.onEvent(DayPlanEvent.OpenedSession)
    }
    LaunchedEffect(state.slotToOpen) {
        val id = state.slotToOpen ?: return@LaunchedEffect
        onOpenSlotEditor(id)
        viewModel.onEvent(DayPlanEvent.OpenedSlotEditor)
    }
    DayPlanContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenDayEditor = onOpenDayEditor,
        onOpenSlotEditor = onOpenSlotEditor,
        modifier = modifier,
    )
}

@Composable
internal fun DayPlanContent(
    state: DayPlanUiState,
    onEvent: (DayPlanEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDayEditor: (Long) -> Unit,
    onOpenSlotEditor: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        DayPlanHeader(
            state = state,
            onBack = onBack,
            onOpenSettings = onOpenSettings,
        )
        DateStrip(
            chips = state.dateStrip,
            onSelect = { onEvent(DayPlanEvent.SelectDate(it.date)) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            DaySummary(state)
            Spacer(Modifier.height(12.dp))
            when {
                state.isRestDay -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.dayplan_rest_day),
                            color = colors.onBackground,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
                else -> {
                    state.sections.forEach { section ->
                        DayPlanSectionBlock(
                            section = section,
                            collapsed = section.kind.name in state.collapsedSections,
                            editable = state.editable,
                            onToggle = { onEvent(DayPlanEvent.ToggleSection(section.kind.name)) },
                            onAdd = {
                                if (state.editable) {
                                    onEvent(DayPlanEvent.AddSlotInCategory(section.kind.name))
                                } else {
                                    state.dayId?.let(onOpenDayEditor)
                                }
                            },
                            onEditSlot = onOpenSlotEditor,
                            onMoveSlot = { id, delta -> onEvent(DayPlanEvent.MoveSlot(id, delta)) },
                            onRemoveSlot = { onEvent(DayPlanEvent.RemoveSlot(it)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        DayPlanBottomBar(state = state, onEvent = onEvent)
    }
}

@Composable
private fun DayPlanHeader(
    state: DayPlanUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, size = TouchTargets.Workout)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.dayplan_title),
                    color = colors.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (state.programName.isNotBlank()) {
                    val mode = when (state.scheduleMode) {
                        ScheduleMode.FIXED -> stringResource(R.string.dayplan_mode_fixed)
                        ScheduleMode.ROLLING -> stringResource(R.string.dayplan_mode_rolling)
                        null -> ""
                    }
                    Text(
                        text = stringResource(
                            R.string.dayplan_chip,
                            state.programName.uppercase(Locale.getDefault()),
                            mode,
                            state.trainingDayCount,
                        ),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(TouchTargets.Workout),
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = colors.onBackground,
                )
            }
        }
    }
}

@Composable
private fun DateStrip(
    chips: List<DayPlanDateChip>,
    onSelect: (DayPlanDateChip) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chips.forEach { chip ->
            val selected = chip.isSelected
            val emphasize = chip.isToday || selected
            Column(
                modifier = Modifier
                    .heightIn(min = TouchTargets.Workout)
                    .clickable { onSelect(chip) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = chip.date.dayOfMonth.toString(),
                    color = if (emphasize) colors.onBackground else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (chip.isToday || selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = chip.date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .uppercase(Locale.getDefault()),
                    color = if (emphasize) colors.onBackground else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (chip.isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .background(
                            if (selected) colors.onBackground else colors.background,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun DaySummary(state: DayPlanUiState) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.dayLabel.orEmpty(),
            color = colors.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (state.estimatedSeconds > 0 && !state.isRestDay) {
            Text(
                text = stringResource(R.string.dayplan_mins, (state.estimatedSeconds + 59) / 60),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (state.commitment == DayPlanCommitment.PROJECTED) {
        Text(
            text = stringResource(R.string.dayplan_projected),
            color = colors.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.dayplan_projected_note),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DayPlanSectionBlock(
    section: DayPlanSection,
    collapsed: Boolean,
    editable: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onEditSlot: (Long) -> Unit,
    onMoveSlot: (Long, Int) -> Unit,
    onRemoveSlot: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTargets.Workout)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sectionTitle(section.kind),
                color = colors.onBackground,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.dayplan_mins, (section.estimatedSeconds + 59) / 60),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            if (editable) {
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(TouchTargets.Workout),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.dayplan_add),
                            tint = colors.onSurface,
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(
                    if (collapsed) R.string.dayplan_expand else R.string.dayplan_collapse,
                ),
                tint = colors.onBackground,
            )
        }
        HorizontalDivider(color = colors.outlineVariant)
        if (!collapsed) {
            val slotRows = section.rows.filterIsInstance<DayPlanRow.Slot>()
            section.rows.forEach { row ->
                when (row) {
                    is DayPlanRow.Checklist -> ChecklistRow(row, section.kind)
                    is DayPlanRow.Slot -> {
                        val index = slotRows.indexOf(row)
                        SlotRow(
                            row = row,
                            editable = editable,
                            canMoveUp = index > 0,
                            canMoveDown = index >= 0 && index < slotRows.lastIndex,
                            onEditSlot = onEditSlot,
                            onMoveUp = { onMoveSlot(row.slot.id, -1) },
                            onMoveDown = { onMoveSlot(row.slot.id, 1) },
                            onRemove = { onRemoveSlot(row.slot.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(row: DayPlanRow.Checklist, kind: DayPlanSectionKind) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseSilhouette(exerciseName = row.item.text, sectionKind = kind)
        Column(Modifier.weight(1f)) {
            Text(row.item.text, color = colors.onBackground, style = MaterialTheme.typography.titleMedium)
            if (row.schemeSummary.isNotBlank()) {
                Text(row.schemeSummary, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SlotRow(
    row: DayPlanRow.Slot,
    editable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEditSlot: (Long) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val upDescription = stringResource(R.string.dayplan_reorder_up)
    val downDescription = stringResource(R.string.dayplan_reorder_down)
    Column(Modifier.fillMaxWidth()) {
        if (row.isGroupStart && row.groupLabel != null) {
            Text(
                text = row.groupLabel,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExerciseSilhouette(
                exerciseName = row.exerciseName,
                category = row.slot.prescription.category,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = editable) { onEditSlot(row.slot.id) },
            ) {
                Text(row.exerciseName, color = colors.onBackground, style = MaterialTheme.typography.titleMedium)
                if (row.schemeSummary.isNotBlank()) {
                    Text(row.schemeSummary, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                row.setupNotes?.let { notes ->
                    Text(notes, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (editable) {
                TextButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier
                        .size(TouchTargets.Editor)
                        .semantics { contentDescription = upDescription },
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = colors.primary)
                }
                TextButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier
                        .size(TouchTargets.Editor)
                        .semantics { contentDescription = downDescription },
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = colors.primary)
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(TouchTargets.Workout),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.dayplan_more),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dayplan_edit)) },
                            onClick = {
                                menuOpen = false
                                onEditSlot(row.slot.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dayplan_reorder_up)) },
                            enabled = canMoveUp,
                            onClick = {
                                menuOpen = false
                                onMoveUp()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dayplan_reorder_down)) },
                            enabled = canMoveDown,
                            onClick = {
                                menuOpen = false
                                onMoveDown()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dayplan_remove)) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
        if (row.isGroupEnd) {
            HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun DayPlanBottomBar(
    state: DayPlanUiState,
    onEvent: (DayPlanEvent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val label = when (state.cta.kind) {
        DayPlanCtaKind.START_WORKOUT -> stringResource(
            if (state.activeSessionId != null) R.string.workout_resume else R.string.workout_start,
        )
        DayPlanCtaKind.TAKE_REST -> stringResource(R.string.dayplan_take_rest)
        // Cult Fit screenshot copy: always "Swap with Today" for future FIXED days.
        DayPlanCtaKind.SWAP_WITH_TODAY -> stringResource(R.string.dayplan_swap_with_today)
        DayPlanCtaKind.PROJECTED -> stringResource(R.string.dayplan_projected_cta)
        DayPlanCtaKind.NONE -> null
    } ?: return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(20.dp),
    ) {
        when (state.cta.kind) {
            DayPlanCtaKind.PROJECTED -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(1.dp, colors.outline, RoundedCornerShape(28.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                if (dark) {
                    Button(
                        onClick = { onEvent(DayPlanEvent.PrimaryAction) },
                        enabled = state.cta.kind != DayPlanCtaKind.NONE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = White,
                            contentColor = Rose,
                            disabledContainerColor = White.copy(alpha = 0.35f),
                            disabledContentColor = Rose.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            text = label.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                } else {
                    PrimaryButton(
                        label = label,
                        onClick = { onEvent(DayPlanEvent.PrimaryAction) },
                        enabled = state.cta.kind != DayPlanCtaKind.NONE,
                    )
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(kind: DayPlanSectionKind): String = stringResource(
    when (kind) {
        DayPlanSectionKind.PREP -> R.string.dayplan_section_prep
        DayPlanSectionKind.MOBILITY -> R.string.dayplan_section_mobility
        DayPlanSectionKind.SKILL -> R.string.dayplan_section_skill
        DayPlanSectionKind.COMPOUND -> R.string.dayplan_section_compound
        DayPlanSectionKind.ISOLATION -> R.string.dayplan_section_isolation
        DayPlanSectionKind.CORE -> R.string.dayplan_section_core
        DayPlanSectionKind.CARDIO -> R.string.dayplan_section_cardio
        DayPlanSectionKind.COOLDOWN -> R.string.dayplan_section_cooldown
        DayPlanSectionKind.OTHER -> R.string.dayplan_section_other
    },
)
