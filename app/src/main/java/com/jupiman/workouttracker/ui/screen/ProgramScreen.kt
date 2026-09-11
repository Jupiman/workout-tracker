@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jupiman.workouttracker.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.model.WorkoutTemplateExerciseEditorItem
import com.jupiman.workouttracker.data.repository.formatCentiKg
import com.jupiman.workouttracker.ui.component.WeightAdjuster
import com.jupiman.workouttracker.ui.component.toPositiveCentiKgOrDefault
import com.jupiman.workouttracker.ui.viewmodel.ProgramViewModel
import kotlinx.coroutines.flow.flowOf

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
    var selectedProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedDayId by rememberSaveable { mutableStateOf<Long?>(null) }

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
        if (selectedProgramId != null && selectedProgram == null) {
            selectedProgramId = null
            selectedDayId = null
        }
    }

    LaunchedEffect(templates, selectedDayId) {
        if (selectedDayId != null && selectedDay == null) {
            selectedDayId = null
        }
    }

    when {
        selectedDay != null -> BackHandler { selectedDayId = null }
        selectedProgram != null -> BackHandler { selectedProgramId = null }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when {
            selectedDay != null && selectedProgram != null -> {
                EditDayScreen(
                    program = selectedProgram,
                    day = selectedDay,
                    isFirst = templates.firstOrNull()?.id == selectedDay.id,
                    isLast = templates.lastOrNull()?.id == selectedDay.id,
                    exercises = exercises,
                    viewModel = viewModel,
                    onBack = { selectedDayId = null },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            selectedProgram != null -> {
                TrainingDaysScreen(
                    program = selectedProgram,
                    activeProgram = activeProgram,
                    templates = templates,
                    viewModel = viewModel,
                    onBack = { selectedProgramId = null },
                    onSelectDay = { selectedDayId = it },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                ProgramListScreen(
                    programs = programs,
                    activeProgram = activeProgram,
                    viewModel = viewModel,
                    onSelectProgram = { selectedProgramId = it },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Day ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
            )
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
                TextButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Text("Up")
                }
                TextButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Text("Down")
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
                            )
                        }
                        is ProgramExerciseDisplayBlock.Superset -> {
                            ProgramSupersetGroup(
                                block = block,
                                lastIndex = templateExercises.lastIndex,
                                viewModel = viewModel,
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
) {
    var sets by remember(item) { mutableStateOf(item.plannedWorkingSets.toString()) }
    var repMin by remember(item) { mutableStateOf(item.repMin.toString()) }
    var repMax by remember(item) { mutableStateOf(item.repMax.toString()) }
    var currentWeight by remember(item) { mutableStateOf(formatCentiKg(item.currentWeightCentiKg)) }
    var currentTargetReps by remember(item) { mutableStateOf(item.currentTargetReps.toString()) }
    var increment by remember(item) { mutableStateOf(formatCentiKg(item.incrementCentiKg)) }
    var restSeconds by remember(item) { mutableStateOf(item.restSeconds.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.exerciseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
                TextButton(
                    onClick = { viewModel.moveTemplateExercise(item.workoutTemplateId, item.id, -1) },
                    enabled = !isFirst,
                ) {
                    Text("Up")
                }
                TextButton(
                    onClick = { viewModel.moveTemplateExercise(item.workoutTemplateId, item.id, 1) },
                    enabled = !isLast,
                ) {
                    Text("Down")
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
