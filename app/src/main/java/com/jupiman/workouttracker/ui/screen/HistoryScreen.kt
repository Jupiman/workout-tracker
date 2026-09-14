package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.formatCentiKg
import com.jupiman.workouttracker.ui.theme.StatusPill
import com.jupiman.workouttracker.ui.theme.WorkoutRadii
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.ui.theme.WorkoutVisualState
import com.jupiman.workouttracker.ui.theme.workoutStateColors
import com.jupiman.workouttracker.ui.viewmodel.HistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedSession = uiState.selectedSession

    if (selectedSession != null) {
        BackHandler(onBack = viewModel::closeDetails)
        WorkoutHistoryDetail(
            sessionDetails = selectedSession,
            onBack = viewModel::closeDetails,
            modifier = modifier,
        )
    } else {
        WorkoutHistoryList(
            sessions = uiState.sessions,
            onSelectSession = viewModel::selectSession,
            modifier = modifier,
        )
    }
}

@Composable
private fun WorkoutHistoryList(
    sessions: List<WorkoutSessionWithDetails>,
    onSelectSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionsByDate = remember(sessions) {
        sessions.groupBy { it.historyDate() }
    }
    val latestWorkoutDate = remember(sessions) {
        sessions.firstOrNull()?.historyDate()
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedMonth by remember {
        mutableStateOf(YearMonth.from(latestWorkoutDate ?: LocalDate.now()))
    }
    LaunchedEffect(latestWorkoutDate) {
        if (selectedDate == null && latestWorkoutDate != null) {
            selectedDate = latestWorkoutDate
            selectedMonth = YearMonth.from(latestWorkoutDate)
        }
    }
    val selectedDateSessions = selectedDate
        ?.let { date -> sessionsByDate[date].orEmpty() }
        .orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = WorkoutSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "No completed workouts yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            item {
                HistoryCalendarCard(
                    selectedMonth = selectedMonth,
                    selectedDate = selectedDate,
                    workoutCountsByDate = sessionsByDate.mapValues { (_, workouts) -> workouts.size },
                    onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                    onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
                    onSelectDate = { selectedDate = it },
                )
            }

            item {
                SelectedHistoryDayHeader(
                    selectedDate = selectedDate,
                    workoutCount = selectedDateSessions.size,
                )
            }

            if (selectedDateSessions.isEmpty()) {
                item {
                    Text(
                        text = "No workouts logged for this day.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            selectedDateSessions.forEach { sessionDetails ->
                item(key = sessionDetails.session.id) {
                    HistorySessionRow(
                        sessionDetails = sessionDetails,
                        onClick = { onSelectSession(sessionDetails.session.id) },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

}

@Composable
private fun HistoryCalendarCard(
    selectedMonth: YearMonth,
    selectedDate: LocalDate?,
    workoutCountsByDate: Map<LocalDate, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onPreviousMonth) {
                    Text("Prev")
                }
                Text(
                    text = selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        " ${selectedMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = onNextMonth) {
                    Text("Next")
                }
            }
            CalendarWeekHeader()
            calendarWeeks(selectedMonth).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            currentMonth = selectedMonth,
                            selected = selectedDate == date,
                            workoutCount = workoutCountsByDate[date] ?: 0,
                            onSelectDate = onSelectDate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarWeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    currentMonth: YearMonth,
    selected: Boolean,
    workoutCount: Int,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inCurrentMonth = YearMonth.from(date) == currentMonth
    val hasWorkouts = workoutCount > 0
    val workoutMarkerColor = Color(0xFF2E7D32)
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        inCurrentMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    }

    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .background(background, RoundedCornerShape(WorkoutRadii.row))
            .clickable(onClick = { onSelectDate(date) })
            .padding(vertical = 6.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = if (hasWorkouts) {
                            workoutMarkerColor
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasWorkouts) Color.White else content,
                    fontWeight = if (selected || hasWorkouts) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Text(
                text = if (workoutCount > 0) workoutCount.toString() else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasWorkouts) workoutMarkerColor else MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1,
            )
        }
    }
}

@Composable
private fun SelectedHistoryDayHeader(
    selectedDate: LocalDate?,
    workoutCount: Int,
) {
    val dateText = selectedDate?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        ?: "Choose a day"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = when (workoutCount) {
                0 -> "No workouts"
                1 -> "1 workout"
                else -> "$workoutCount workouts"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySessionRow(
    sessionDetails: WorkoutSessionWithDetails,
    onClick: () -> Unit,
) {
    val session = sessionDetails.session

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(
                text = if (session.status == WorkoutSessionStatus.PARTIAL) "Partial" else "Completed",
                state = if (session.status == WorkoutSessionStatus.PARTIAL) {
                    WorkoutVisualState.Rest
                } else {
                    WorkoutVisualState.Completed
                },
            )
            Text(
                text = session.workoutNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatHistoryDate(session.completedAt ?: session.startedAt),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = historySummary(sessionDetails),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkoutHistoryDetail(
    sessionDetails: WorkoutSessionWithDetails,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = sessionDetails.session

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = WorkoutSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = session.workoutNameSnapshot,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = session.programNameSnapshot,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatHistoryDate(session.completedAt ?: session.startedAt)} · " +
                        "${formatHistoryTime(session.startedAt)}-${formatHistoryTime(session.completedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.status == WorkoutSessionStatus.PARTIAL) {
                    StatusPill(
                        text = "Partial workout",
                        state = WorkoutVisualState.Rest,
                    )
                }
            }
        }

        sessionDetails.exercises
            .sortedBy { it.exercise.sortOrderSnapshot }
            .forEach { exercise ->
                item(key = exercise.exercise.id) {
                    HistoryExerciseCard(exercise = exercise)
                }
            }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HistoryExerciseCard(
    exercise: SessionExerciseWithSets,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
        ) {
            Text(
                text = exercise.exercise.exerciseNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${exercise.exercise.plannedSetCountSnapshot} x " +
                    "${exercise.exercise.targetRepsSnapshot} @ " +
                    "${formatCentiKg(exercise.exercise.prescribedWeightCentiKgSnapshot)} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exercise.exercise.supersetGroupSnapshot != null) {
                StatusPill(
                    text = "Superset",
                    state = WorkoutVisualState.Current,
                )
            }
            if (exercise.exercise.setupNoteSnapshot.isNotBlank()) {
                Text(
                    text = "Setup: ${exercise.exercise.setupNoteSnapshot}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            exercise.sets
                .sortedBy { it.setOrder }
                .forEach { set ->
                    HistorySetLine(set = set)
                }
        }
    }
}

@Composable
private fun HistorySetLine(
    set: SessionSetEntity,
) {
    val state = when (set.status) {
        SessionSetStatus.COMPLETED -> WorkoutVisualState.Completed
        SessionSetStatus.SKIPPED -> WorkoutVisualState.Skipped
        SessionSetStatus.PENDING -> WorkoutVisualState.Pending
    }
    val palette = workoutStateColors(state)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.container, RoundedCornerShape(WorkoutRadii.row))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = set.historyLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.content,
        )
        Text(
            text = set.historyLoad(),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun historySummary(sessionDetails: WorkoutSessionWithDetails): String {
    val completedSets = sessionDetails.exercises
        .flatMap { it.sets }
        .count { it.status == SessionSetStatus.COMPLETED }
    val exerciseCount = sessionDetails.exercises.size
    val status = if (sessionDetails.session.status == WorkoutSessionStatus.PARTIAL) {
        "partial"
    } else {
        "completed"
    }

    return "$exerciseCount exercise(s), $completedSets set(s), $status"
}

private fun SessionSetEntity.historyLabel(): String =
    when (setType) {
        SetType.WARMUP -> "Warm-up"
        SetType.WORKING -> if (isPlanned) "Set ${setOrder + 1}" else "Extra ${setOrder + 1}"
        SetType.EXTRA -> "Extra ${setOrder + 1}"
        SetType.AMRAP -> "AMRAP ${setOrder + 1}"
        SetType.DROP -> "Drop ${setOrder + 1}"
    }

private fun SessionSetEntity.historyLoad(): String =
    when (status) {
        SessionSetStatus.COMPLETED -> {
            val weight = actualWeightCentiKg ?: prescribedWeightCentiKg
            val reps = actualReps ?: prescribedReps
            "${weight.kgText()} x ${reps?.toString() ?: "-"}"
        }
        SessionSetStatus.SKIPPED -> "${prescribedWeightCentiKg.kgText()} x " +
            "${prescribedReps?.toString() ?: "-"} skipped"
        SessionSetStatus.PENDING -> "${prescribedWeightCentiKg.kgText()} x " +
            "${prescribedReps?.toString() ?: "-"} pending"
    }

private fun Int?.kgText(): String = this?.let { "${formatCentiKg(it)} kg" } ?: "- kg"

private fun WorkoutSessionWithDetails.historyDate(): LocalDate =
    Instant.ofEpochMilli(session.completedAt ?: session.startedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

private fun calendarWeeks(month: YearMonth): List<List<LocalDate>> {
    val firstOfMonth = month.atDay(1)
    val firstCalendarDay = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())
    return (0 until 6).map { week ->
        (0 until 7).map { day ->
            firstCalendarDay.plusDays((week * 7 + day).toLong())
        }
    }
}

private fun formatHistoryDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

private fun formatHistoryTime(timestamp: Long?): String =
    timestamp?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    } ?: "--:--"
