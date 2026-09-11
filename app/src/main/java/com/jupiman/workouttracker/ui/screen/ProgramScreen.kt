@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jupiman.workouttracker.ui.screen

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    var selectedProgramId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(programs, activeProgram) {
        val selectedStillExists = programs.any { it.id == selectedProgramId }
        if (!selectedStillExists) {
            selectedProgramId = activeProgram?.id ?: programs.firstOrNull()?.id
        }
    }

    val templatesFlow = remember(selectedProgramId) {
        selectedProgramId?.let(viewModel::workoutTemplates) ?: flowOf(emptyList())
    }
    val templates by templatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Program",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        message?.let { currentMessage ->
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = currentMessage,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = viewModel::clearMessage) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        item {
            ProgramLibrarySection(
                programs = programs,
                activeProgram = activeProgram,
                selectedProgramId = selectedProgramId,
                onSelectProgram = { selectedProgramId = it },
                onCreateProgram = viewModel::createProgram,
                onRenameProgram = viewModel::renameProgram,
                onActivateProgram = viewModel::activateProgram,
                onArchiveProgram = viewModel::archiveProgram,
            )
        }

        item {
            ExerciseLibrarySection(
                exercises = exercises,
                onCreateExercise = viewModel::createExercise,
                onRenameExercise = viewModel::renameExercise,
                onArchiveExercise = viewModel::archiveExercise,
            )
        }

        item {
            SelectedProgramSection(
                selectedProgramId = selectedProgramId,
                templates = templates,
                exercises = exercises,
                viewModel = viewModel,
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProgramLibrarySection(
    programs: List<ProgramEntity>,
    activeProgram: ProgramEntity?,
    selectedProgramId: Long?,
    onSelectProgram: (Long) -> Unit,
    onCreateProgram: (String) -> Unit,
    onRenameProgram: (Long, String) -> Unit,
    onActivateProgram: (Long) -> Unit,
    onArchiveProgram: (Long) -> Unit,
) {
    var newProgramName by rememberSaveable { mutableStateOf("") }

    SectionCard(title = "Programs") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newProgramName,
                onValueChange = { newProgramName = it },
                modifier = Modifier.weight(1f),
                label = { Text("New program") },
                singleLine = true,
            )
            Button(
                onClick = {
                    onCreateProgram(newProgramName)
                    newProgramName = ""
                },
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (programs.isEmpty()) {
            Text("No programs yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            programs.forEach { program ->
                ProgramRow(
                    program = program,
                    isActive = activeProgram?.id == program.id,
                    isSelected = selectedProgramId == program.id,
                    onSelect = { onSelectProgram(program.id) },
                    onRename = { onRenameProgram(program.id, it) },
                    onActivate = { onActivateProgram(program.id) },
                    onArchive = { onArchiveProgram(program.id) },
                )
            }
        }
    }
}

@Composable
private fun ProgramRow(
    program: ProgramEntity,
    isActive: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: (String) -> Unit,
    onActivate: () -> Unit,
    onArchive: () -> Unit,
) {
    var name by remember(program) { mutableStateOf(program.name) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                label = { Text(if (isActive) "Active program" else "Program") },
                singleLine = true,
            )
            OutlinedButton(onClick = onSelect, enabled = !isSelected) {
                Text("Open")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onRename(name) }) {
                Text("Save")
            }
            TextButton(onClick = onActivate, enabled = !isActive) {
                Text("Activate")
            }
            TextButton(onClick = onArchive) {
                Text("Archive")
            }
        }
    }
}

@Composable
private fun ExerciseLibrarySection(
    exercises: List<ExerciseEntity>,
    onCreateExercise: (String) -> Unit,
    onRenameExercise: (Long, String) -> Unit,
    onArchiveExercise: (Long) -> Unit,
) {
    var newExerciseName by rememberSaveable { mutableStateOf("") }

    SectionCard(title = "Exercise Library") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newExerciseName,
                onValueChange = { newExerciseName = it },
                modifier = Modifier.weight(1f),
                label = { Text("New exercise") },
                singleLine = true,
            )
            Button(
                onClick = {
                    onCreateExercise(newExerciseName)
                    newExerciseName = ""
                },
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (exercises.isEmpty()) {
            Text("No exercises yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            exercises.forEach { exercise ->
                ExerciseLibraryRow(
                    exercise = exercise,
                    onRename = { onRenameExercise(exercise.id, it) },
                    onArchive = { onArchiveExercise(exercise.id) },
                )
            }
        }
    }
}

@Composable
private fun ExerciseLibraryRow(
    exercise: ExerciseEntity,
    onRename: (String) -> Unit,
    onArchive: () -> Unit,
) {
    var name by remember(exercise) { mutableStateOf(exercise.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.weight(1f),
            label = { Text("Exercise") },
            singleLine = true,
        )
        TextButton(onClick = { onRename(name) }) {
            Text("Save")
        }
        TextButton(onClick = onArchive) {
            Text("Archive")
        }
    }
}

@Composable
private fun SelectedProgramSection(
    selectedProgramId: Long?,
    templates: List<WorkoutTemplateEntity>,
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
) {
    var newWorkoutName by rememberSaveable(selectedProgramId) { mutableStateOf("") }

    SectionCard(title = "Workout Templates") {
        if (selectedProgramId == null) {
            Text("Create or open a program to add workouts.")
            return@SectionCard
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newWorkoutName,
                onValueChange = { newWorkoutName = it },
                modifier = Modifier.weight(1f),
                label = { Text("New workout") },
                singleLine = true,
            )
            Button(
                onClick = {
                    viewModel.createWorkoutTemplate(selectedProgramId, newWorkoutName)
                    newWorkoutName = ""
                },
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (templates.isEmpty()) {
            Text("No workout templates yet.")
        } else {
            templates.forEachIndexed { index, template ->
                WorkoutTemplateCard(
                    template = template,
                    index = index,
                    isFirst = index == 0,
                    isLast = index == templates.lastIndex,
                    exercises = exercises,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun WorkoutTemplateCard(
    template: WorkoutTemplateEntity,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
) {
    val itemsFlow = remember(template.id) { viewModel.templateExercises(template.id) }
    val templateExercises by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var name by remember(template) { mutableStateOf(template.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${index + 1}. ${template.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Workout name") },
                singleLine = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.renameWorkoutTemplate(template.id, name) }) {
                    Text("Save")
                }
                TextButton(
                    onClick = { viewModel.moveWorkoutTemplate(template.programId, template.id, -1) },
                    enabled = !isFirst,
                ) {
                    Text("Up")
                }
                TextButton(
                    onClick = { viewModel.moveWorkoutTemplate(template.programId, template.id, 1) },
                    enabled = !isLast,
                ) {
                    Text("Down")
                }
                TextButton(onClick = { viewModel.deleteWorkoutTemplate(template.id) }) {
                    Text("Remove")
                }
            }

            HorizontalDivider()

            if (templateExercises.isEmpty()) {
                Text("No exercises in this workout.")
            } else {
                templateExercises.forEachIndexed { exerciseIndex, item ->
                    TemplateExerciseEditor(
                        item = item,
                        isFirst = exerciseIndex == 0,
                        isLast = exerciseIndex == templateExercises.lastIndex,
                        viewModel = viewModel,
                    )
                }
            }

            HorizontalDivider()

            AddTemplateExerciseForm(
                workoutTemplateId = template.id,
                exercises = exercises,
                viewModel = viewModel,
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
) {
    var sets by remember(item) { mutableStateOf(item.plannedWorkingSets.toString()) }
    var repMin by remember(item) { mutableStateOf(item.repMin.toString()) }
    var repMax by remember(item) { mutableStateOf(item.repMax.toString()) }
    var currentWeight by remember(item) { mutableStateOf(formatCentiKg(item.currentWeightCentiKg)) }
    var currentTargetReps by remember(item) { mutableStateOf(item.currentTargetReps.toString()) }
    var increment by remember(item) { mutableStateOf(formatCentiKg(item.incrementCentiKg)) }
    var restSeconds by remember(item) { mutableStateOf(item.restSeconds.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.exerciseName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        item.supersetGroupId?.let {
            Text(
                text = "Superset group $it",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
            SmallNumberField("Rep min", repMin, { repMin = it }, Modifier.weight(1f))
            SmallNumberField("Rep max", repMax, { repMax = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Weight kg", currentWeight, { currentWeight = it }, Modifier.weight(1f), decimal = true)
            SmallNumberField("Target reps", currentTargetReps, { currentTargetReps = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Increment kg", increment, { increment = it }, Modifier.weight(1f), decimal = true)
            SmallNumberField("Rest sec", restSeconds, { restSeconds = it }, Modifier.weight(1f))
        }
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

@Composable
private fun AddTemplateExerciseForm(
    workoutTemplateId: Long,
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
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
    var exerciseMenuExpanded by remember { mutableStateOf(false) }
    val selectedExercise = exercises.firstOrNull { it.id == selectedExerciseId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Add exercise",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
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
                                selectedExerciseId = exercise.id
                                newExerciseName = ""
                                exerciseMenuExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = newExerciseName,
                onValueChange = {
                    newExerciseName = it
                    if (it.isNotBlank()) selectedExerciseId = null
                },
                modifier = Modifier.weight(1f),
                label = { Text("Or new exercise") },
                singleLine = true,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
            SmallNumberField("Rep min", repMin, { repMin = it }, Modifier.weight(1f))
            SmallNumberField("Rep max", repMax, { repMax = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Weight kg", currentWeight, { currentWeight = it }, Modifier.weight(1f), decimal = true)
            SmallNumberField("Target reps", currentTargetReps, { currentTargetReps = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Increment kg", increment, { increment = it }, Modifier.weight(1f), decimal = true)
            SmallNumberField("Rest sec", restSeconds, { restSeconds = it }, Modifier.weight(1f))
        }
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
                newExerciseName = ""
                selectedExerciseId = null
            },
        ) {
            Text("Add to workout")
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
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
