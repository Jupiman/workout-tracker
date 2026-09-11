@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jupiman.workouttracker.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import com.jupiman.workouttracker.data.local.model.WorkoutTemplateExerciseEditorItem
import com.jupiman.workouttracker.data.repository.formatCentiKg
import com.jupiman.workouttracker.ui.component.WeightAdjuster
import com.jupiman.workouttracker.ui.component.toPositiveCentiKgOrDefault
import com.jupiman.workouttracker.ui.viewmodel.ProgramViewModel
import kotlinx.coroutines.flow.flowOf

private enum class ProgramDestinationTab(val label: String) {
    Programs("Programs"),
    Exercises("Exercises"),
}

@Composable
fun ProgramScreen(
    viewModel: ProgramViewModel,
    modifier: Modifier = Modifier,
) {
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val activeProgram by viewModel.activeProgram.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(ProgramDestinationTab.Programs) }
    var selectedProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedDayId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectNewestProgramWhenAvailable by rememberSaveable { mutableStateOf(false) }
    var selectNewestDayWhenAvailable by rememberSaveable { mutableStateOf(false) }

    val selectedProgram = programs.firstOrNull { it.id == selectedProgramId }
    val templatesFlow = remember(selectedProgramId) {
        selectedProgramId?.let(viewModel::workoutTemplates) ?: flowOf(emptyList())
    }
    val templates by templatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedDay = templates.firstOrNull { it.id == selectedDayId }

    LaunchedEffect(message) {
        val currentMessage = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentMessage)
        viewModel.clearMessage()
    }

    LaunchedEffect(programs, selectedProgramId) {
        when {
            selectNewestProgramWhenAvailable && programs.isNotEmpty() -> {
                selectedProgramId = programs.maxBy { it.createdAt }.id
                selectedDayId = null
                selectNewestProgramWhenAvailable = false
            }
            selectedProgramId == null -> {
                selectedProgramId = activeProgram?.id ?: programs.firstOrNull()?.id
            }
            selectedProgram == null -> {
                selectedProgramId = activeProgram?.id ?: programs.firstOrNull()?.id
                selectedDayId = null
            }
        }
    }

    LaunchedEffect(templates, selectedDayId) {
        when {
            selectNewestDayWhenAvailable && templates.isNotEmpty() -> {
                selectedDayId = templates.last().id
                selectNewestDayWhenAvailable = false
            }
            selectedDayId == null -> {
                selectedDayId = templates.firstOrNull()?.id
            }
            selectedDay == null -> {
                selectedDayId = templates.firstOrNull()?.id
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            ProgramDestinationTabs(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
            )
            when (selectedTab) {
                ProgramDestinationTab.Programs -> ProgramBuilderScreen(
                    programs = programs,
                    activeProgram = activeProgram,
                    selectedProgram = selectedProgram,
                    templates = templates,
                    selectedDay = selectedDay,
                    exercises = exercises,
                    viewModel = viewModel,
                    onSelectProgram = {
                        selectedProgramId = it
                        selectedDayId = null
                    },
                    onCreateProgram = { name ->
                        selectNewestProgramWhenAvailable = true
                        viewModel.createProgram(name)
                    },
                    onSelectDay = { selectedDayId = it },
                    onCreateDay = { programId, name ->
                        selectNewestDayWhenAvailable = true
                        viewModel.createWorkoutTemplate(programId, name)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ProgramDestinationTab.Exercises -> ExerciseLibraryScreen(
                    exercises = exercises,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ProgramDestinationTabs(
    selectedTab: ProgramDestinationTab,
    onSelectTab: (ProgramDestinationTab) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTab.ordinal) {
        ProgramDestinationTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                text = { Text(tab.label) },
            )
        }
    }
}

@Composable
private fun ProgramBuilderScreen(
    programs: List<ProgramEntity>,
    activeProgram: ProgramEntity?,
    selectedProgram: ProgramEntity?,
    templates: List<WorkoutTemplateEntity>,
    selectedDay: WorkoutTemplateEntity?,
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
    onSelectProgram: (Long) -> Unit,
    onCreateProgram: (String) -> Unit,
    onSelectDay: (Long) -> Unit,
    onCreateDay: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateProgramDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameProgramDialog by rememberSaveable(selectedProgram?.id) { mutableStateOf(false) }
    var showAddDayDialog by rememberSaveable(selectedProgram?.id) { mutableStateOf(false) }
    var showRenameDayDialog by rememberSaveable(selectedDay?.id) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Program builder")
        }

        if (programs.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No programs yet",
                    body = "Create a program to start building training days.",
                    actionLabel = "Create program",
                    onAction = { showCreateProgramDialog = true },
                )
            }
        } else {
            item {
                ProgramSelectorPanel(
                    programs = programs,
                    activeProgram = activeProgram,
                    selectedProgram = selectedProgram,
                    onSelectProgram = onSelectProgram,
                    onCreateProgram = { showCreateProgramDialog = true },
                    onRenameProgram = { showRenameProgramDialog = true },
                    onActivateProgram = {
                        selectedProgram?.let { viewModel.activateProgram(it.id) }
                    },
                    onArchiveProgram = {
                        selectedProgram?.let { viewModel.archiveProgram(it.id) }
                    },
                )
            }

            selectedProgram?.let { program ->
                item {
                    TrainingDayChipRow(
                        templates = templates,
                        selectedDay = selectedDay,
                        onSelectDay = onSelectDay,
                        onAddDay = { showAddDayDialog = true },
                    )
                }

                if (templates.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No training days",
                            body = "Add a day to start arranging exercises for this program.",
                            actionLabel = "Add training day",
                            onAction = { showAddDayDialog = true },
                        )
                    }
                } else if (selectedDay != null) {
                    item {
                        SelectedDayHeader(
                            day = selectedDay,
                            exerciseCount = null,
                            onRename = { showRenameDayDialog = true },
                            onRemove = { viewModel.deleteWorkoutTemplate(selectedDay.id) },
                        )
                    }
                    item {
                        SelectedDayExerciseList(
                            program = program,
                            day = selectedDay,
                            exercises = exercises,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateProgramDialog) {
        NameDialog(
            title = "Create program",
            label = "Program name",
            confirmLabel = "Create",
            initialValue = "",
            onDismiss = { showCreateProgramDialog = false },
            onConfirm = { name ->
                onCreateProgram(name)
                showCreateProgramDialog = false
            },
        )
    }

    selectedProgram?.let { program ->
        if (showRenameProgramDialog) {
            NameDialog(
                title = "Rename program",
                label = "Program name",
                confirmLabel = "Save",
                initialValue = program.name,
                onDismiss = { showRenameProgramDialog = false },
                onConfirm = { name ->
                    viewModel.renameProgram(program.id, name)
                    showRenameProgramDialog = false
                },
            )
        }

        if (showAddDayDialog) {
            NameDialog(
                title = "Add training day",
                label = "Training day name",
                confirmLabel = "Add",
                initialValue = "",
                onDismiss = { showAddDayDialog = false },
                onConfirm = { name ->
                    onCreateDay(program.id, name)
                    showAddDayDialog = false
                },
            )
        }
    }

    selectedDay?.let { day ->
        if (showRenameDayDialog) {
            NameDialog(
                title = "Rename training day",
                label = "Training day name",
                confirmLabel = "Save",
                initialValue = day.name,
                onDismiss = { showRenameDayDialog = false },
                onConfirm = { name ->
                    viewModel.renameWorkoutTemplate(day.id, name)
                    showRenameDayDialog = false
                },
            )
        }
    }
}

@Composable
private fun ProgramSelectorPanel(
    programs: List<ProgramEntity>,
    activeProgram: ProgramEntity?,
    selectedProgram: ProgramEntity?,
    onSelectProgram: (Long) -> Unit,
    onCreateProgram: () -> Unit,
    onRenameProgram: () -> Unit,
    onActivateProgram: () -> Unit,
    onArchiveProgram: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Program",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(selectedProgram?.name ?: "Select program")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        programs.forEach { program ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (activeProgram?.id == program.id) {
                                            "${program.name} - Active"
                                        } else {
                                            program.name
                                        },
                                    )
                                },
                                onClick = {
                                    onSelectProgram(program.id)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    text = if (activeProgram?.id == selectedProgram?.id) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (activeProgram?.id == selectedProgram?.id) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCreateProgram) {
                    Text("New")
                }
                TextButton(onClick = onRenameProgram, enabled = selectedProgram != null) {
                    Text("Rename")
                }
                TextButton(
                    onClick = onActivateProgram,
                    enabled = selectedProgram != null && activeProgram?.id != selectedProgram.id,
                ) {
                    Text("Set active")
                }
                TextButton(onClick = onArchiveProgram, enabled = selectedProgram != null) {
                    Text("Archive")
                }
            }
        }
    }
}

@Composable
private fun TrainingDayChipRow(
    templates: List<WorkoutTemplateEntity>,
    selectedDay: WorkoutTemplateEntity?,
    onSelectDay: (Long) -> Unit,
    onAddDay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        templates.forEachIndexed { index, template ->
            FilterChip(
                selected = selectedDay?.id == template.id,
                onClick = { onSelectDay(template.id) },
                label = { Text("Day ${index + 1}: ${template.name}") },
            )
        }
        OutlinedButton(onClick = onAddDay) {
            Text("+")
        }
    }
}

@Composable
private fun SelectedDayHeader(
    day: WorkoutTemplateEntity,
    exerciseCount: Int?,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = day.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            exerciseCount?.let {
                Text(
                    text = "$it exercises",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRename) {
                    Text("Rename")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun SelectedDayExerciseList(
    program: ProgramEntity,
    day: WorkoutTemplateEntity,
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
) {
    val itemsFlow = remember(day.id) { viewModel.templateExercises(day.id) }
    val templateExercises by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddExerciseDialog by rememberSaveable(day.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { showAddExerciseDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add exercise")
        }

        if (templateExercises.isEmpty()) {
            Text("No exercises in this day.", style = MaterialTheme.typography.bodyLarge)
        } else {
            templateExercises.programExerciseDisplayBlocks().forEach { block ->
                when (block) {
                    is ProgramExerciseDisplayBlock.SingleExercise -> {
                        TemplateExerciseEditor(
                            item = block.exercise.item,
                            isFirst = block.exercise.index == 0,
                            isLast = block.exercise.index == templateExercises.lastIndex,
                            viewModel = viewModel,
                            onDragStep = { offset ->
                                viewModel.moveTemplateExercise(day.id, block.exercise.item.id, offset)
                            },
                        )
                    }
                    is ProgramExerciseDisplayBlock.Superset -> {
                        ProgramSupersetGroup(
                            block = block,
                            lastIndex = templateExercises.lastIndex,
                            viewModel = viewModel,
                            onDragStep = { exerciseId, offset ->
                                viewModel.moveTemplateExercise(day.id, exerciseId, offset)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            workoutTemplateId = day.id,
            exercises = exercises,
            viewModel = viewModel,
            onDismiss = { showAddExerciseDialog = false },
        )
    }
}

@Composable
private fun ExerciseLibraryScreen(
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var exerciseToRename by rememberSaveable { mutableStateOf<Long?>(null) }
    val renameExercise = exercises.firstOrNull { it.id == exerciseToRename }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Exercise library")
        }
        item {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create exercise")
            }
        }
        if (exercises.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No exercises yet",
                    body = "Create reusable exercises, then add them to any training day.",
                    actionLabel = "Create exercise",
                    onAction = { showCreateDialog = true },
                )
            }
        } else {
            exercises.forEach { exercise ->
                item(key = exercise.id) {
                    ExerciseLibraryRow(
                        exercise = exercise,
                        onRename = { exerciseToRename = exercise.id },
                        onArchive = { viewModel.archiveExercise(exercise.id) },
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = "Create exercise",
            label = "Exercise name",
            confirmLabel = "Create",
            initialValue = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createExercise(name)
                showCreateDialog = false
            },
        )
    }

    renameExercise?.let { exercise ->
        NameDialog(
            title = "Rename exercise",
            label = "Exercise name",
            confirmLabel = "Save",
            initialValue = exercise.name,
            onDismiss = { exerciseToRename = null },
            onConfirm = { name ->
                viewModel.renameExercise(exercise.id, name)
                exerciseToRename = null
            },
        )
    }
}

@Composable
private fun ExerciseLibraryRow(
    exercise: ExerciseEntity,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRename) {
                    Text("Rename")
                }
                TextButton(onClick = onArchive) {
                    Text("Archive")
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ProgramListScreen(
    programs: List<ProgramEntity>,
    activeProgram: ProgramEntity?,
    viewModel: ProgramViewModel,
    onSelectProgram: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newProgramName by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Programs")
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Create program",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newProgramName,
                            onValueChange = { newProgramName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Program name") },
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                viewModel.createProgram(newProgramName)
                                newProgramName = ""
                            },
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }

        if (programs.isEmpty()) {
            item {
                Text("No programs yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            programs.forEach { program ->
                item(key = program.id) {
                    ProgramListRow(
                        program = program,
                        isActive = activeProgram?.id == program.id,
                        onOpen = { onSelectProgram(program.id) },
                        onRename = { viewModel.renameProgram(program.id, it) },
                        onActivate = { viewModel.activateProgram(program.id) },
                        onArchive = { viewModel.archiveProgram(program.id) },
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
private fun ProgramListRow(
    program: ProgramEntity,
    isActive: Boolean,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onActivate: () -> Unit,
    onArchive: () -> Unit,
) {
    var name by remember(program) { mutableStateOf(program.name) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isActive) "Active program" else "Program",
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Name") },
                    singleLine = true,
                )
                Button(onClick = onOpen) {
                    Text("Open")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onRename(name) }) {
                    Text("Save")
                }
                TextButton(onClick = onActivate, enabled = !isActive) {
                    Text("Set active")
                }
                TextButton(onClick = onArchive) {
                    Text("Archive")
                }
            }
        }
    }
}

@Composable
private fun TrainingDaysScreen(
    program: ProgramEntity,
    activeProgram: ProgramEntity?,
    templates: List<WorkoutTemplateEntity>,
    viewModel: ProgramViewModel,
    onBack: () -> Unit,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDayDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = program.name, onBack = onBack)
            Text(
                text = if (activeProgram?.id == program.id) "Active program" else "Inactive program",
                style = MaterialTheme.typography.labelLarge,
                color = if (activeProgram?.id == program.id) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        item {
            Button(
                onClick = { showAddDayDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add training day")
            }
        }

        if (templates.isEmpty()) {
            item {
                Text("No training days yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            templates.forEachIndexed { index, template ->
                item(key = template.id) {
                    TrainingDayRow(
                        day = template,
                        index = index,
                        isFirst = index == 0,
                        isLast = index == templates.lastIndex,
                        onOpen = { onSelectDay(template.id) },
                        onRename = { viewModel.renameWorkoutTemplate(template.id, it) },
                        onMove = { offset -> viewModel.moveWorkoutTemplate(program.id, template.id, offset) },
                        onRemove = { viewModel.deleteWorkoutTemplate(template.id) },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAddDayDialog) {
        AddTrainingDayDialog(
            onDismiss = { showAddDayDialog = false },
            onAdd = { name ->
                viewModel.createWorkoutTemplate(program.id, name)
                showAddDayDialog = false
            },
        )
    }
}

@Composable
private fun TrainingDayRow(
    day: WorkoutTemplateEntity,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var name by remember(day) { mutableStateOf(day.name) }
    var isDragging by remember(day.id) { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    val cardModifier = if (isDragging) {
        Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Day ${index + 1}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                ReorderDragHandle(
                    canDragUp = !isFirst,
                    canDragDown = !isLast,
                    isDragging = isDragging,
                    onDraggingChange = { isDragging = it },
                    onDragStep = onMove,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Training day") },
                    singleLine = true,
                )
                Button(onClick = onOpen) {
                    Text("Edit")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onRename(name) }) {
                    Text("Save")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun EditDayScreen(
    program: ProgramEntity,
    day: WorkoutTemplateEntity,
    isFirst: Boolean,
    isLast: Boolean,
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemsFlow = remember(day.id) { viewModel.templateExercises(day.id) }
    val templateExercises by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var dayName by remember(day) { mutableStateOf(day.name) }
    var showAddExerciseDialog by rememberSaveable(day.id) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = day.name, onBack = onBack)
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = dayName,
                        onValueChange = { dayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Training day name") },
                        singleLine = true,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.renameWorkoutTemplate(day.id, dayName) }) {
                            Text("Save")
                        }
                        TextButton(
                            onClick = { viewModel.moveWorkoutTemplate(program.id, day.id, -1) },
                            enabled = !isFirst,
                        ) {
                            Text("Move up")
                        }
                        TextButton(
                            onClick = { viewModel.moveWorkoutTemplate(program.id, day.id, 1) },
                            enabled = !isLast,
                        ) {
                            Text("Move down")
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { showAddExerciseDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add exercise")
            }
        }

        if (templateExercises.isEmpty()) {
            item {
                Text("No exercises in this day.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            templateExercises.programExerciseDisplayBlocks().forEach { block ->
                item(key = block.key) {
                    when (block) {
                        is ProgramExerciseDisplayBlock.SingleExercise -> {
                            TemplateExerciseEditor(
                                item = block.exercise.item,
                                isFirst = block.exercise.index == 0,
                                isLast = block.exercise.index == templateExercises.lastIndex,
                                viewModel = viewModel,
                                onDragStep = { offset ->
                                    viewModel.moveTemplateExercise(day.id, block.exercise.item.id, offset)
                                },
                            )
                        }
                        is ProgramExerciseDisplayBlock.Superset -> {
                            ProgramSupersetGroup(
                                block = block,
                                lastIndex = templateExercises.lastIndex,
                                viewModel = viewModel,
                                onDragStep = { exerciseId, offset ->
                                    viewModel.moveTemplateExercise(day.id, exerciseId, offset)
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAddExerciseDialog) {
        AddExerciseDialog(
            workoutTemplateId = day.id,
            exercises = exercises,
            viewModel = viewModel,
            onDismiss = { showAddExerciseDialog = false },
        )
    }
}

@Composable
private fun ProgramSupersetGroup(
    block: ProgramExerciseDisplayBlock.Superset,
    lastIndex: Int,
    viewModel: ProgramViewModel,
    onDragStep: (Long, Int) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f), shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Superset",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${block.exercises.size} exercises",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        block.exercises.forEachIndexed { index, exercise ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            }
            TemplateExerciseEditor(
                item = exercise.item,
                isFirst = exercise.index == 0,
                isLast = exercise.index == lastIndex,
                viewModel = viewModel,
                showSupersetLabel = false,
                onDragStep = { offset -> onDragStep(exercise.item.id, offset) },
            )
        }
    }
}

@Composable
private fun TemplateExerciseEditor(
    item: WorkoutTemplateExerciseEditorItem,
    isFirst: Boolean,
    isLast: Boolean,
    viewModel: ProgramViewModel,
    showSupersetLabel: Boolean = true,
    onDragStep: (Int) -> Unit,
) {
    var sets by remember(item) { mutableStateOf(item.plannedWorkingSets.toString()) }
    var repMin by remember(item) { mutableStateOf(item.repMin.toString()) }
    var repMax by remember(item) { mutableStateOf(item.repMax.toString()) }
    var currentWeight by remember(item) { mutableStateOf(formatCentiKg(item.currentWeightCentiKg)) }
    var currentTargetReps by remember(item) { mutableStateOf(item.currentTargetReps.toString()) }
    var increment by remember(item) { mutableStateOf(formatCentiKg(item.incrementCentiKg)) }
    var restSeconds by remember(item) { mutableStateOf(item.restSeconds.toString()) }
    val setTargetsFlow = remember(item.id) { viewModel.templateSetTargets(item.id) }
    val setTargets by setTargetsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val warmupSetsFlow = remember(item.id) { viewModel.templateWarmupSets(item.id) }
    val warmupSets by warmupSetsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleSetCount = sets.trim().toIntOrNull()?.coerceAtLeast(1) ?: item.plannedWorkingSets
    var isDragging by remember(item.id) { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    val cardModifier = if (isDragging) {
        Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.exerciseName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ReorderDragHandle(
                    canDragUp = !isFirst,
                    canDragDown = !isLast,
                    isDragging = isDragging,
                    onDraggingChange = { isDragging = it },
                    onDragStep = onDragStep,
                )
            }
            Text(
                text = "${item.plannedWorkingSets} x ${item.repMin}-${item.repMax} | " +
                    "${formatCentiKg(item.currentWeightCentiKg)} kg | Rest ${item.restSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (showSupersetLabel && item.supersetGroupId != null) {
                Text(
                    text = "Superset",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
                SmallNumberField("Rep min", repMin, { repMin = it }, Modifier.weight(1f))
                SmallNumberField("Rep max", repMax, { repMax = it }, Modifier.weight(1f))
            }
            WeightAdjuster(
                label = "Weight kg",
                value = currentWeight,
                onValueChange = { currentWeight = it },
                incrementCentiKg = increment.toPositiveCentiKgOrDefault(item.incrementCentiKg),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Target reps", currentTargetReps, { currentTargetReps = it }, Modifier.weight(1f))
                SmallNumberField("Increment kg", increment, { increment = it }, Modifier.weight(1f), decimal = true)
            }
            SmallNumberField("Rest sec", restSeconds, { restSeconds = it }, Modifier.fillMaxWidth())
            TemplateSetTargetsEditor(
                setTargets = setTargets,
                setCount = visibleSetCount,
                defaultWeight = currentWeight,
                defaultReps = currentTargetReps,
                increment = increment,
                fallbackIncrementCentiKg = item.incrementCentiKg,
                onSaveSetTarget = { setOrder, weight, reps ->
                    viewModel.updateTemplateSetTarget(
                        workoutTemplateExerciseId = item.id,
                        setOrder = setOrder,
                        prescribedWeight = weight,
                        prescribedReps = reps,
                    )
                },
                onResetSetTarget = { setOrder ->
                    viewModel.resetTemplateSetTarget(item.id, setOrder)
                },
                onResetAllSetTargets = {
                    viewModel.resetTemplateSetTargets(item.id)
                },
            )
            WarmupSchemeEditor(
                warmupSets = warmupSets,
                onEnableDefault = { viewModel.enableDefaultWarmupScheme(item.id) },
                onClear = { viewModel.clearWarmupScheme(item.id) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        viewModel.updateTemplateExercise(
                            item = item,
                            sets = sets,
                            repMin = repMin,
                            repMax = repMax,
                            currentWeight = currentWeight,
                            currentTargetReps = currentTargetReps,
                            increment = increment,
                            restSeconds = restSeconds,
                        )
                    },
                ) {
                    Text("Save")
                }
                TextButton(onClick = { viewModel.removeTemplateExercise(item.id) }) {
                    Text("Remove")
                }
                TextButton(
                    onClick = { viewModel.supersetWithPrevious(item.workoutTemplateId, item.id) },
                    enabled = !isFirst,
                ) {
                    Text("Superset previous")
                }
                TextButton(
                    onClick = { viewModel.removeFromSuperset(item.id) },
                    enabled = item.supersetGroupId != null,
                ) {
                    Text("Remove superset")
                }
            }
        }
    }
}

@Composable
private fun ReorderDragHandle(
    canDragUp: Boolean,
    canDragDown: Boolean,
    isDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onDragStep: (Int) -> Unit,
) {
    val handleColor = when {
        isDragging -> MaterialTheme.colorScheme.onPrimaryContainer
        canDragUp || canDragDown -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    var accumulatedDrag by remember { mutableStateOf(0f) }

    Canvas(
        modifier = Modifier
            .size(44.dp)
            .pointerInput(canDragUp, canDragDown, thresholdPx) {
                if (!canDragUp && !canDragDown) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        accumulatedDrag = 0f
                        onDraggingChange(true)
                    },
                    onDragCancel = {
                        accumulatedDrag = 0f
                        onDraggingChange(false)
                    },
                    onDragEnd = {
                        accumulatedDrag = 0f
                        onDraggingChange(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount.y

                        while (accumulatedDrag <= -thresholdPx && canDragUp) {
                            onDragStep(-1)
                            accumulatedDrag += thresholdPx
                        }
                        while (accumulatedDrag >= thresholdPx && canDragDown) {
                            onDragStep(1)
                            accumulatedDrag -= thresholdPx
                        }
                    },
                )
            },
    ) {
        val centerX = size.width / 2f
        val topY = size.height * 0.34f
        val gapY = size.height * 0.16f
        val halfWidth = size.width * 0.18f

        repeat(3) { index ->
            val y = topY + gapY * index
            drawLine(
                color = handleColor,
                start = Offset(centerX - halfWidth, y),
                end = Offset(centerX + halfWidth, y),
                strokeWidth = if (isDragging) 4.dp.toPx() else 3.dp.toPx(),
            )
        }
    }
}

@Composable
private fun WarmupSchemeEditor(
    warmupSets: List<WorkoutTemplateWarmupSetEntity>,
    onEnableDefault: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            text = "Warm-up",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (warmupSets.isEmpty()) {
            Text(
                text = "No warm-up sets",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onEnableDefault, modifier = Modifier.fillMaxWidth()) {
                Text("Use default warm-up")
            }
        } else {
            Text(
                text = warmupSets.joinToString { warmup ->
                    "${warmup.reps} @ ${warmup.percentOfWorkingWeight}%"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEnableDefault,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reset default")
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun TemplateSetTargetsEditor(
    setTargets: List<WorkoutTemplateSetTargetEntity>,
    setCount: Int,
    defaultWeight: String,
    defaultReps: String,
    increment: String,
    fallbackIncrementCentiKg: Int,
    onSaveSetTarget: (Int, String, String) -> Unit,
    onResetSetTarget: (Int) -> Unit,
    onResetAllSetTargets: () -> Unit,
) {
    var selectedSetOrder by remember(setCount) { mutableStateOf<Int?>(null) }
    val selectedTarget = selectedSetOrder?.let { setOrder ->
        setTargets.firstOrNull { it.setOrder == setOrder }
    }
    val selectedDefaultWeight = selectedTarget?.prescribedWeightCentiKg?.let(::formatCentiKg) ?: defaultWeight
    val selectedDefaultReps = selectedTarget?.prescribedReps?.toString() ?: defaultReps
    var selectedWeight by remember(selectedSetOrder, selectedDefaultWeight) {
        mutableStateOf(selectedDefaultWeight)
    }
    var selectedReps by remember(selectedSetOrder, selectedDefaultReps) {
        mutableStateOf(selectedDefaultReps)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Set targets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = onResetAllSetTargets,
                enabled = setTargets.isNotEmpty(),
            ) {
                Text("Reset all")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until setCount).forEach { setOrder ->
                val setTarget = setTargets.firstOrNull { it.setOrder == setOrder }
                val repsLabel = setTarget?.prescribedReps?.toString() ?: defaultReps
                val weightLabel = setTarget?.prescribedWeightCentiKg?.let(::formatCentiKg) ?: defaultWeight
                val label = "Set ${setOrder + 1}: $repsLabel @ ${weightLabel}kg"
                if (selectedSetOrder == setOrder) {
                    Button(onClick = { selectedSetOrder = null }) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(onClick = { selectedSetOrder = setOrder }) {
                        Text(label)
                    }
                }
            }
        }

        selectedSetOrder?.let { setOrder ->
            WeightAdjuster(
                label = "Set ${setOrder + 1} weight kg",
                value = selectedWeight,
                onValueChange = { selectedWeight = it },
                incrementCentiKg = increment.toPositiveCentiKgOrDefault(fallbackIncrementCentiKg),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = selectedReps,
                onValueChange = { selectedReps = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Set ${setOrder + 1} reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveSetTarget(setOrder, selectedWeight, selectedReps) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save set target")
                }
                OutlinedButton(
                    onClick = { onResetSetTarget(setOrder) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedTarget != null,
                ) {
                    Text("Reset set")
                }
            }
        }
    }
}

private sealed interface ProgramExerciseDisplayBlock {
    val key: String

    data class SingleExercise(val exercise: IndexedTemplateExercise) : ProgramExerciseDisplayBlock {
        override val key: String = "exercise-${exercise.item.id}"
    }

    data class Superset(
        val groupId: Long,
        val exercises: List<IndexedTemplateExercise>,
    ) : ProgramExerciseDisplayBlock {
        override val key: String = "superset-$groupId"
    }
}

private data class IndexedTemplateExercise(
    val index: Int,
    val item: WorkoutTemplateExerciseEditorItem,
)

private fun List<WorkoutTemplateExerciseEditorItem>.programExerciseDisplayBlocks(): List<ProgramExerciseDisplayBlock> {
    val indexedItems = mapIndexed { index, item -> IndexedTemplateExercise(index, item) }
    val seenSupersetGroups = mutableSetOf<Long>()

    return buildList {
        indexedItems.forEach { indexedExercise ->
            val supersetGroup = indexedExercise.item.supersetGroupId
            if (supersetGroup == null) {
                add(ProgramExerciseDisplayBlock.SingleExercise(indexedExercise))
                return@forEach
            }
            if (seenSupersetGroups.add(supersetGroup)) {
                add(
                    ProgramExerciseDisplayBlock.Superset(
                        groupId = supersetGroup,
                        exercises = indexedItems.filter { it.item.supersetGroupId == supersetGroup },
                    ),
                )
            }
        }
    }
}

@Composable
private fun AddTrainingDayDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add training day") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Training day name") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onAdd(name) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AddExerciseDialog(
    workoutTemplateId: Long,
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
    onDismiss: () -> Unit,
) {
    var selectedExerciseId by rememberSaveable(workoutTemplateId) { mutableStateOf<Long?>(null) }
    var newExerciseName by rememberSaveable(workoutTemplateId) { mutableStateOf("") }
    var sets by rememberSaveable(workoutTemplateId) { mutableStateOf("3") }
    var repMin by rememberSaveable(workoutTemplateId) { mutableStateOf("8") }
    var repMax by rememberSaveable(workoutTemplateId) { mutableStateOf("12") }
    var currentWeight by rememberSaveable(workoutTemplateId) { mutableStateOf("0") }
    var currentTargetReps by rememberSaveable(workoutTemplateId) { mutableStateOf("8") }
    var increment by rememberSaveable(workoutTemplateId) { mutableStateOf("2.5") }
    var restSeconds by rememberSaveable(workoutTemplateId) { mutableStateOf("180") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            AddExerciseDialogContent(
                exercises = exercises,
                selectedExerciseId = selectedExerciseId,
                onSelectedExerciseChange = {
                    selectedExerciseId = it
                    newExerciseName = ""
                },
                newExerciseName = newExerciseName,
                onNewExerciseNameChange = {
                    newExerciseName = it
                    if (it.isNotBlank()) selectedExerciseId = null
                },
                sets = sets,
                onSetsChange = { sets = it },
                repMin = repMin,
                onRepMinChange = { repMin = it },
                repMax = repMax,
                onRepMaxChange = { repMax = it },
                currentWeight = currentWeight,
                onCurrentWeightChange = { currentWeight = it },
                currentTargetReps = currentTargetReps,
                onCurrentTargetRepsChange = { currentTargetReps = it },
                increment = increment,
                onIncrementChange = { increment = it },
                restSeconds = restSeconds,
                onRestSecondsChange = { restSeconds = it },
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newExerciseName.isBlank()) {
                        viewModel.addExistingExerciseToWorkout(
                            workoutTemplateId = workoutTemplateId,
                            exerciseId = selectedExerciseId,
                            sets = sets,
                            repMin = repMin,
                            repMax = repMax,
                            currentWeight = currentWeight,
                            currentTargetReps = currentTargetReps,
                            increment = increment,
                            restSeconds = restSeconds,
                        )
                    } else {
                        viewModel.createExerciseAndAddToWorkout(
                            workoutTemplateId = workoutTemplateId,
                            exerciseName = newExerciseName,
                            sets = sets,
                            repMin = repMin,
                            repMax = repMax,
                            currentWeight = currentWeight,
                            currentTargetReps = currentTargetReps,
                            increment = increment,
                            restSeconds = restSeconds,
                        )
                    }
                    onDismiss()
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AddExerciseDialogContent(
    exercises: List<ExerciseEntity>,
    selectedExerciseId: Long?,
    onSelectedExerciseChange: (Long?) -> Unit,
    newExerciseName: String,
    onNewExerciseNameChange: (String) -> Unit,
    sets: String,
    onSetsChange: (String) -> Unit,
    repMin: String,
    onRepMinChange: (String) -> Unit,
    repMax: String,
    onRepMaxChange: (String) -> Unit,
    currentWeight: String,
    onCurrentWeightChange: (String) -> Unit,
    currentTargetReps: String,
    onCurrentTargetRepsChange: (String) -> Unit,
    increment: String,
    onIncrementChange: (String) -> Unit,
    restSeconds: String,
    onRestSecondsChange: (String) -> Unit,
) {
    var exerciseMenuExpanded by remember { mutableStateOf(false) }
    val selectedExercise = exercises.firstOrNull { it.id == selectedExerciseId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { exerciseMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = exercises.isNotEmpty(),
            ) {
                Text(selectedExercise?.name ?: "Choose existing")
            }
            DropdownMenu(
                expanded = exerciseMenuExpanded,
                onDismissRequest = { exerciseMenuExpanded = false },
            ) {
                exercises.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.name) },
                        onClick = {
                            onSelectedExerciseChange(exercise.id)
                            exerciseMenuExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = newExerciseName,
            onValueChange = onNewExerciseNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Or new exercise") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Sets", sets, onSetsChange, Modifier.weight(1f))
            SmallNumberField("Rep min", repMin, onRepMinChange, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Rep max", repMax, onRepMaxChange, Modifier.weight(1f))
            SmallNumberField("Target", currentTargetReps, onCurrentTargetRepsChange, Modifier.weight(1f))
        }
        WeightAdjuster(
            label = "Weight kg",
            value = currentWeight,
            onValueChange = onCurrentWeightChange,
            incrementCentiKg = increment.toPositiveCentiKgOrDefault(250),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Inc kg", increment, onIncrementChange, Modifier.weight(1f), decimal = true)
            SmallNumberField("Rest sec", restSeconds, onRestSecondsChange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
) {
    Spacer(modifier = Modifier.height(8.dp))
    if (onBack != null) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SmallNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}
