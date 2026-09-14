@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jupiman.workouttracker.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import com.jupiman.workouttracker.data.local.model.WorkoutTemplateExerciseEditorItem
import com.jupiman.workouttracker.data.repository.formatCentiKg
import com.jupiman.workouttracker.ui.component.WeightAdjuster
import com.jupiman.workouttracker.ui.component.TrackingModePicker
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.repository.trackingText
import com.jupiman.workouttracker.ui.component.toPositiveCentiKgOrDefault
import com.jupiman.workouttracker.ui.filterByExerciseSearchQuery
import com.jupiman.workouttracker.ui.theme.StatusPill
import com.jupiman.workouttracker.ui.theme.WorkoutRadii
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.ui.theme.WorkoutVisualState
import com.jupiman.workouttracker.ui.theme.WorkoutEmptyState
import com.jupiman.workouttracker.ui.theme.WorkoutGlyph
import com.jupiman.workouttracker.ui.theme.WorkoutIcon
import com.jupiman.workouttracker.ui.viewmodel.ProgramViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    var progressExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }

    val selectedProgram = programs.firstOrNull { it.id == selectedProgramId }
    val templatesFlow = remember(selectedProgramId) {
        selectedProgramId?.let(viewModel::workoutTemplates) ?: flowOf(emptyList())
    }
    val templates by templatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedDay = templates.firstOrNull { it.id == selectedDayId }
    val progressExercise = exercises.firstOrNull { it.id == progressExerciseId }

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

    if (progressExercise != null) {
        BackHandler { progressExerciseId = null }
        ExerciseProgressScreen(
            exercise = progressExercise,
            viewModel = viewModel,
            onBack = { progressExerciseId = null },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    onOpenProgress = { progressExerciseId = it },
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
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
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
    var showReorderDaysDialog by rememberSaveable(selectedProgram?.id) { mutableStateOf(false) }
    var showAddExerciseDialog by rememberSaveable(selectedDay?.id) { mutableStateOf(false) }
    var editingExerciseId by rememberSaveable(selectedDay?.id) { mutableStateOf<Long?>(null) }
    var showRemoveDayConfirmation by rememberSaveable(selectedDay?.id) { mutableStateOf(false) }
    val selectedDayExercisesFlow = remember(selectedDay?.id) {
        selectedDay?.let { viewModel.templateExercises(it.id) } ?: flowOf(emptyList())
    }
    val selectedDayExercises by selectedDayExercisesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val editingExercise = editingExerciseId?.let { id ->
        selectedDayExercises.firstOrNull { it.id == id }
    }
    val exerciseBlocks = selectedDayExercises.programExerciseDisplayBlocks()
    val sourceExerciseBlockKeys = exerciseBlocks.map { it.key }
    val exerciseListState = rememberLazyListState()
    val exerciseAutoScroller = rememberReorderAutoScroller(exerciseListState)
    var visualExerciseBlockKeys by remember(selectedDay?.id) { mutableStateOf<List<String>>(emptyList()) }
    var draggedExerciseBlockKey by remember(selectedDay?.id) { mutableStateOf<String?>(null) }
    var draggedExerciseBlockOffset by remember(selectedDay?.id) { mutableStateOf(0f) }
    val reorderThresholdPx = with(LocalDensity.current) { 92.dp.toPx() }
    val visualExerciseBlocks = (
        if (visualExerciseBlockKeys.toSet() == sourceExerciseBlockKeys.toSet()) {
            visualExerciseBlockKeys
        } else {
            sourceExerciseBlockKeys
        }
    ).mapNotNull { key -> exerciseBlocks.firstOrNull { it.key == key } }

    LaunchedEffect(sourceExerciseBlockKeys, draggedExerciseBlockKey) {
        if (draggedExerciseBlockKey == null) {
            visualExerciseBlockKeys = sourceExerciseBlockKeys
        }
    }

    fun startExerciseBlockDrag(key: String) {
        draggedExerciseBlockKey = key
        draggedExerciseBlockOffset = 0f
        visualExerciseBlockKeys = sourceExerciseBlockKeys
        exerciseAutoScroller.start(key) { draggedExerciseBlockOffset }
    }

    fun updateExerciseBlockDrag(key: String, deltaY: Float) {
        if (draggedExerciseBlockKey != key) return
        var offset = draggedExerciseBlockOffset + deltaY
        val keys = (
            if (visualExerciseBlockKeys.toSet() == sourceExerciseBlockKeys.toSet()) {
                visualExerciseBlockKeys
            } else {
                sourceExerciseBlockKeys
            }
        ).toMutableList()
        var index = keys.indexOf(key)
        if (index == -1) return

        while (offset >= reorderThresholdPx && index < keys.lastIndex) {
            keys[index] = keys[index + 1]
            keys[index + 1] = key
            index += 1
            offset -= reorderThresholdPx
        }
        while (offset <= -reorderThresholdPx && index > 0) {
            keys[index] = keys[index - 1]
            keys[index - 1] = key
            index -= 1
            offset += reorderThresholdPx
        }

        visualExerciseBlockKeys = keys
        draggedExerciseBlockOffset = offset
    }

    fun cancelExerciseBlockDrag() {
        exerciseAutoScroller.stop()
        visualExerciseBlockKeys = sourceExerciseBlockKeys
        draggedExerciseBlockKey = null
        draggedExerciseBlockOffset = 0f
    }

    fun finishExerciseBlockDrag(block: ProgramExerciseDisplayBlock) {
        val key = block.key
        val sourceIndex = sourceExerciseBlockKeys.indexOf(key)
        val targetIndex = visualExerciseBlockKeys.indexOf(key)
        val offset = targetIndex - sourceIndex
        val selectedDayId = selectedDay?.id
        exerciseAutoScroller.stop()
        draggedExerciseBlockKey = null
        draggedExerciseBlockOffset = 0f
        if (sourceIndex != -1 && targetIndex != -1 && offset != 0 && selectedDayId != null) {
            when (block) {
                is ProgramExerciseDisplayBlock.SingleExercise -> {
                    viewModel.moveTemplateExercise(selectedDayId, block.exercise.item.id, offset)
                }
                is ProgramExerciseDisplayBlock.Superset -> {
                    viewModel.moveSupersetGroup(selectedDayId, block.groupId, offset)
                }
            }
        }
    }

    LazyColumn(
        state = exerciseListState,
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

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
                            exerciseCount = selectedDayExercises.size,
                            onRename = { showRenameDayDialog = true },
                            onReorder = { showReorderDaysDialog = true },
                            onRemove = { showRemoveDayConfirmation = true },
                        )
                    }
                    item {
                        Button(
                            onClick = { showAddExerciseDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add exercise")
                        }
                    }
                    if (selectedDayExercises.isEmpty()) {
                        item {
                            Text("No exercises in this day.", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        visualExerciseBlocks.forEachIndexed { blockIndex, block ->
                            item(key = block.key) {
                                val isDraggedBlock = draggedExerciseBlockKey == block.key
                                val blockModifier = Modifier
                                    .zIndex(if (isDraggedBlock) 1f else 0f)
                                    .animateItem()
                                    .graphicsLayer {
                                        if (isDraggedBlock) {
                                            translationY = draggedExerciseBlockOffset
                                        }
                                    }
                                when (block) {
                                    is ProgramExerciseDisplayBlock.SingleExercise -> {
                                        CompactTemplateExerciseCard(
                                            item = block.exercise.item,
                                            isFirst = blockIndex == 0,
                                            isLast = blockIndex == visualExerciseBlocks.lastIndex,
                                            viewModel = viewModel,
                                            modifier = blockModifier,
                                            dragVisualActive = isDraggedBlock,
                                            onEdit = { editingExerciseId = block.exercise.item.id },
                                            commitOnRelease = false,
                                            onDragStart = { startExerciseBlockDrag(block.key) },
                                            onDragDelta = { deltaY -> updateExerciseBlockDrag(block.key, deltaY) },
                                            onDragCancel = ::cancelExerciseBlockDrag,
                                            onDragEnd = { finishExerciseBlockDrag(block) },
                                            onDragStep = {
                                            },
                                        )
                                    }
                                    is ProgramExerciseDisplayBlock.Superset -> {
                                        ProgramSupersetGroup(
                                            block = block,
                                            lastIndex = visualExerciseBlocks.lastIndex,
                                            viewModel = viewModel,
                                            modifier = blockModifier,
                                            onEdit = { editingExerciseId = it },
                                            isFirstBlock = blockIndex == 0,
                                            isLastBlock = blockIndex == visualExerciseBlocks.lastIndex,
                                            dragVisualActive = isDraggedBlock,
                                            commitOnRelease = false,
                                            onDragStart = { startExerciseBlockDrag(block.key) },
                                            onDragDelta = { deltaY -> updateExerciseBlockDrag(block.key, deltaY) },
                                            onDragCancel = ::cancelExerciseBlockDrag,
                                            onDragEnd = { finishExerciseBlockDrag(block) },
                                            onDragStep = {
                                            },
                                        )
                                    }
                                }
                            }
                        }
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

        if (showReorderDaysDialog) {
            ReorderTrainingDaysDialog(
                programId = program.id,
                templates = templates,
                selectedDay = selectedDay,
                viewModel = viewModel,
                onSelectDay = onSelectDay,
                onDismiss = { showReorderDaysDialog = false },
            )
        }
    }

    selectedDay?.let { day ->
        if (showAddExerciseDialog) {
            AddExerciseDialog(
                workoutTemplateId = day.id,
                exercises = exercises,
                viewModel = viewModel,
                onDismiss = { showAddExerciseDialog = false },
            )
        }
    }

    editingExercise?.let { item ->
        TemplateExerciseEditorDialog(
            item = item,
            isFirst = selectedDayExercises.firstOrNull()?.id == item.id,
            isLast = selectedDayExercises.lastOrNull()?.id == item.id,
            viewModel = viewModel,
            onDismiss = { editingExerciseId = null },
        )
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

        if (showRemoveDayConfirmation) {
            ConfirmationDialog(
                title = "Remove training day?",
                body = "This removes '${day.name}' from the program. Existing workout history stays unchanged.",
                confirmLabel = "Remove",
                onDismiss = { showRemoveDayConfirmation = false },
                onConfirm = {
                    viewModel.deleteWorkoutTemplate(day.id)
                    showRemoveDayConfirmation = false
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
                        Text(
                            text = selectedProgram?.name ?: "Select program",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
            WorkoutGlyph(WorkoutIcon.Add, contentDescription = "Add training day")
        }
    }
}

@Composable
private fun SelectedDayHeader(
    day: WorkoutTemplateEntity,
    exerciseCount: Int?,
    onRename: () -> Unit,
    onReorder: () -> Unit,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                TextButton(onClick = onReorder) {
                    Text("Reorder days")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun ReorderTrainingDaysDialog(
    programId: Long,
    templates: List<WorkoutTemplateEntity>,
    selectedDay: WorkoutTemplateEntity?,
    viewModel: ProgramViewModel,
    onSelectDay: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sourceDayKeys = templates.map { it.id }
    val dayListState = rememberLazyListState()
    val dayAutoScroller = rememberReorderAutoScroller(dayListState)
    var visualDayKeys by remember(programId) { mutableStateOf<List<Long>>(emptyList()) }
    var draggedDayKey by remember(programId) { mutableStateOf<Long?>(null) }
    var draggedDayOffset by remember(programId) { mutableStateOf(0f) }
    val reorderThresholdPx = with(LocalDensity.current) { 76.dp.toPx() }
    val visualTemplates = (
        if (visualDayKeys.toSet() == sourceDayKeys.toSet()) {
            visualDayKeys
        } else {
            sourceDayKeys
        }
    ).mapNotNull { id -> templates.firstOrNull { it.id == id } }

    LaunchedEffect(sourceDayKeys, draggedDayKey) {
        if (draggedDayKey == null) {
            visualDayKeys = sourceDayKeys
        }
    }

    fun startDayDrag(dayId: Long) {
        draggedDayKey = dayId
        draggedDayOffset = 0f
        visualDayKeys = sourceDayKeys
        dayAutoScroller.start(dayId) { draggedDayOffset }
    }

    fun updateDayDrag(dayId: Long, deltaY: Float) {
        if (draggedDayKey != dayId) return
        var offset = draggedDayOffset + deltaY
        val keys = (
            if (visualDayKeys.toSet() == sourceDayKeys.toSet()) {
                visualDayKeys
            } else {
                sourceDayKeys
            }
        ).toMutableList()
        var index = keys.indexOf(dayId)
        if (index == -1) return

        while (offset >= reorderThresholdPx && index < keys.lastIndex) {
            keys[index] = keys[index + 1]
            keys[index + 1] = dayId
            index += 1
            offset -= reorderThresholdPx
        }
        while (offset <= -reorderThresholdPx && index > 0) {
            keys[index] = keys[index - 1]
            keys[index - 1] = dayId
            index -= 1
            offset += reorderThresholdPx
        }

        visualDayKeys = keys
        draggedDayOffset = offset
    }

    fun cancelDayDrag() {
        dayAutoScroller.stop()
        visualDayKeys = sourceDayKeys
        draggedDayKey = null
        draggedDayOffset = 0f
    }

    fun finishDayDrag(day: WorkoutTemplateEntity) {
        val sourceIndex = sourceDayKeys.indexOf(day.id)
        val targetIndex = visualDayKeys.indexOf(day.id)
        val offset = targetIndex - sourceIndex
        dayAutoScroller.stop()
        draggedDayKey = null
        draggedDayOffset = 0f
        if (sourceIndex != -1 && targetIndex != -1 && offset != 0) {
            viewModel.moveWorkoutTemplate(programId, day.id, offset)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reorder training days") },
        text = {
            LazyColumn(
                state = dayListState,
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = visualTemplates,
                    key = { _, template -> template.id },
                ) { index, template ->
                    val isDraggedDay = draggedDayKey == template.id
                    val rowModifier = Modifier
                        .zIndex(if (isDraggedDay) 1f else 0f)
                        .animateItem()
                        .graphicsLayer {
                            if (isDraggedDay) {
                                translationY = draggedDayOffset
                            }
                        }
                    ReorderTrainingDayRow(
                        day = template,
                        index = index,
                        selected = selectedDay?.id == template.id,
                        isFirst = index == 0,
                        isLast = index == visualTemplates.lastIndex,
                        modifier = rowModifier,
                        dragVisualActive = isDraggedDay,
                        onSelect = { onSelectDay(template.id) },
                        commitOnRelease = false,
                        onDragStart = { startDayDrag(template.id) },
                        onDragDelta = { deltaY -> updateDayDrag(template.id, deltaY) },
                        onDragCancel = ::cancelDayDrag,
                        onDragEnd = { finishDayDrag(template) },
                        onMove = {
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun ReorderTrainingDayRow(
    day: WorkoutTemplateEntity,
    index: Int,
    selected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    dragVisualActive: Boolean = false,
    commitOnRelease: Boolean = true,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onSelect: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val dragScale by animateFloatAsState(
        targetValue = if (dragVisualActive) 1.02f else 1f,
        label = "dayReorderScale",
    )
    val shape = RoundedCornerShape(WorkoutRadii.card)
    val rowModifier = if (dragVisualActive) {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
    }

    Card(
        modifier = rowModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                dragVisualActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
                selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (dragVisualActive) 8.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSelect),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Day ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = day.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            ReorderDragHandle(
                canDragUp = !isFirst,
                canDragDown = !isLast,
                isDragging = dragVisualActive,
                onDraggingChange = {},
                commitOnRelease = commitOnRelease,
                onDragStart = onDragStart,
                onDragDelta = onDragDelta,
                onDragCancel = onDragCancel,
                onDragEnd = onDragEnd,
                onDragStep = onMove,
            )
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
    var editingExerciseId by rememberSaveable(day.id) { mutableStateOf<Long?>(null) }
    val editingExercise = editingExerciseId?.let { id ->
        templateExercises.firstOrNull { it.id == id }
    }

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
                        CompactTemplateExerciseCard(
                            item = block.exercise.item,
                            isFirst = block.exercise.index == 0,
                            isLast = block.exercise.index == templateExercises.lastIndex,
                            viewModel = viewModel,
                            onEdit = { editingExerciseId = block.exercise.item.id },
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
                            onEdit = { editingExerciseId = it },
                            onDragStep = { offset ->
                                viewModel.moveSupersetGroup(day.id, block.groupId, offset)
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

    editingExercise?.let { item ->
        TemplateExerciseEditorDialog(
            item = item,
            isFirst = templateExercises.firstOrNull()?.id == item.id,
            isLast = templateExercises.lastOrNull()?.id == item.id,
            viewModel = viewModel,
            onDismiss = { editingExerciseId = null },
        )
    }
}

@Composable
private fun ExerciseLibraryScreen(
    exercises: List<ExerciseEntity>,
    viewModel: ProgramViewModel,
    onOpenProgress: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var exerciseToRename by rememberSaveable { mutableStateOf<Long?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredExercises = exercises.filterByExerciseSearchQuery(searchQuery)
    val renameExercise = exercises.firstOrNull { it.id == exerciseToRename }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create exercise")
            }
        }
        if (exercises.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search exercises") },
                        singleLine = true,
                    )
                    if (searchQuery.isNotBlank()) {
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("Clear search")
                        }
                    }
                }
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
        } else if (filteredExercises.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No matching exercises",
                    body = "Clear the search or create a custom exercise.",
                    actionLabel = "Create exercise",
                    onAction = { showCreateDialog = true },
                )
            }
        } else {
            filteredExercises.forEach { exercise ->
                item(key = exercise.id) {
                    ExerciseLibraryRow(
                        exercise = exercise,
                        onRename = { exerciseToRename = exercise.id },
                        onProgress = { onOpenProgress(exercise.id) },
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
    onProgress: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onProgress) {
                    Text("Progress")
                }
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
    WorkoutEmptyState(
        title = title,
        body = body,
        actionLabel = actionLabel,
        onAction = onAction,
    )
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
private fun ConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
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
                        onDuplicate = { viewModel.duplicateWorkoutTemplate(template.id) },
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
    onDuplicate: () -> Unit,
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
                MaterialTheme.colorScheme.surfaceContainerLow
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
                TextButton(onClick = onDuplicate) {
                    Text("Duplicate")
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
                        TextButton(onClick = { viewModel.duplicateWorkoutTemplate(day.id) }) {
                            Text("Duplicate")
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
                                onDragStep = { offset ->
                                    viewModel.moveSupersetGroup(day.id, block.groupId, offset)
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
    modifier: Modifier = Modifier,
    isFirstBlock: Boolean? = null,
    isLastBlock: Boolean? = null,
    dragVisualActive: Boolean = false,
    onEdit: (Long) -> Unit = {},
    commitOnRelease: Boolean = true,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragStep: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val isFirst = isFirstBlock ?: (block.exercises.firstOrNull()?.index == 0)
    val isLast = isLastBlock ?: (block.exercises.lastOrNull()?.index == lastIndex)
    val dragScale by animateFloatAsState(
        targetValue = if (dragVisualActive) 1.01f else 1f,
        label = "supersetReorderScale",
    )
    val groupModifier = if (dragVisualActive) {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
    }

    Column(
        modifier = groupModifier
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f), shape)
            .padding(WorkoutSpacing.card),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
            }
            ReorderDragHandle(
                canDragUp = !isFirst,
                canDragDown = !isLast,
                isDragging = dragVisualActive,
                onDraggingChange = {},
                commitOnRelease = commitOnRelease,
                onDragStart = onDragStart,
                onDragDelta = onDragDelta,
                onDragCancel = onDragCancel,
                onDragEnd = onDragEnd,
                onDragStep = onDragStep,
            )
        }
        block.exercises.forEachIndexed { index, exercise ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            }
            CompactTemplateExerciseCard(
                item = exercise.item,
                isFirst = exercise.index == 0,
                isLast = exercise.index == lastIndex,
                viewModel = viewModel,
                showSupersetLabel = false,
                onEdit = { onEdit(exercise.item.id) },
                showDragHandle = false,
                onDragStep = {},
            )
        }
    }
}

@Composable
private fun CompactTemplateExerciseCard(
    item: WorkoutTemplateExerciseEditorItem,
    isFirst: Boolean,
    isLast: Boolean,
    viewModel: ProgramViewModel,
    modifier: Modifier = Modifier,
    showSupersetLabel: Boolean = true,
    showDragHandle: Boolean = true,
    dragVisualActive: Boolean = false,
    onEdit: () -> Unit,
    commitOnRelease: Boolean = true,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragStep: (Int) -> Unit,
) {
    val setTargetsFlow = remember(item.id) { viewModel.templateSetTargets(item.id) }
    val setTargets by setTargetsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val warmupSetsFlow = remember(item.id) { viewModel.templateWarmupSets(item.id) }
    val warmupSets by warmupSetsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val dragScale by animateFloatAsState(
        targetValue = if (dragVisualActive) 1.02f else 1f,
        label = "exerciseReorderScale",
    )
    val cardShape = RoundedCornerShape(WorkoutRadii.card)
    val cardModifier = if (dragVisualActive) {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
            .border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
    } else {
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
            }
    }

    Card(
        modifier = cardModifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (dragVisualActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (dragVisualActive) 8.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WorkoutSpacing.card),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.plannedWorkingSets} x " + trackingText(item.trackingMode,
                        item.currentWeightCentiKg, item.currentTargetReps, item.targetDurationSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when (item.trackingMode) {
                        TrackingMode.WEIGHT_REPS -> "+${formatCentiKg(item.incrementCentiKg)} kg · "
                        TrackingMode.REPS -> "Reps ${item.repMin}-${item.repMax} · "
                        TrackingMode.DURATION -> "+${item.durationIncrementSeconds} sec · "
                    } + "Rest ${item.restSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showSupersetLabel && item.supersetGroupId != null) {
                        StatusPill(
                            text = "Superset",
                            state = WorkoutVisualState.Current,
                        )
                    }
                    if (warmupSets.isNotEmpty()) {
                        StatusPill(
                            text = "Warm-up",
                            state = WorkoutVisualState.Rest,
                        )
                    }
                    if (setTargets.isNotEmpty()) {
                        StatusPill(
                            text = "Custom sets",
                            state = WorkoutVisualState.Pending,
                        )
                    }
                    if (item.setupNote.isNotBlank()) {
                        StatusPill(
                            text = "Setup",
                            state = WorkoutVisualState.Ready,
                        )
                    }
                }
            }
            TextButton(onClick = { viewModel.duplicateTemplateExercise(item.id) }) {
                Text("Duplicate")
            }
            if (showDragHandle) {
                ReorderDragHandle(
                    canDragUp = !isFirst,
                    canDragDown = !isLast,
                    isDragging = dragVisualActive,
                    onDraggingChange = {},
                    commitOnRelease = commitOnRelease,
                    onDragStart = onDragStart,
                    onDragDelta = onDragDelta,
                    onDragCancel = onDragCancel,
                    onDragEnd = onDragEnd,
                    onDragStep = onDragStep,
                )
            }
        }
    }
}

@Composable
private fun TemplateExerciseEditorDialog(
    item: WorkoutTemplateExerciseEditorItem,
    isFirst: Boolean,
    isLast: Boolean,
    viewModel: ProgramViewModel,
    onDismiss: () -> Unit,
) {
    var showRemoveConfirmation by rememberSaveable(item.id) { mutableStateOf(false) }
    var showUnsavedChangesConfirmation by rememberSaveable(item.id) { mutableStateOf(false) }
    var currentDraft by remember(item) { mutableStateOf(item.toEditorDraft()) }
    val hasUnsavedChanges = currentDraft != item.toEditorDraft()

    fun saveCurrentDraft() {
        viewModel.updateTemplateExercise(
            item = item,
            exerciseName = currentDraft.exerciseName,
            sets = currentDraft.sets,
            repMin = currentDraft.repMin,
            repMax = currentDraft.repMax,
            currentWeight = currentDraft.currentWeight,
            currentTargetReps = currentDraft.currentTargetReps,
            increment = currentDraft.increment,
            restSeconds = currentDraft.restSeconds,
            trackingMode = currentDraft.trackingMode,
            durationSeconds = currentDraft.durationSeconds,
            durationIncrementSeconds = currentDraft.durationIncrementSeconds,
            setupNote = currentDraft.setupNote,
        )
    }

    fun requestDismiss() {
        if (hasUnsavedChanges) {
            showUnsavedChangesConfirmation = true
        } else {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = ::requestDismiss,
        title = { Text("Edit exercise") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                TemplateExerciseEditor(
                    item = item,
                    isFirst = isFirst,
                    isLast = isLast,
                    viewModel = viewModel,
                    showDragHandle = false,
                    showSaveButton = false,
                    onRemoveRequest = { showRemoveConfirmation = true },
                    onDraftChange = { currentDraft = it },
                    onDragStep = {},
                )
            }
        },
        confirmButton = {
            Button(onClick = ::requestDismiss) {
                Text("Done")
            }
        },
    )

    if (showUnsavedChangesConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesConfirmation = false },
            title = { Text("Save changes?") },
            text = { Text("You changed '${item.exerciseName}'. Save those changes before closing?") },
            confirmButton = {
                Button(
                    onClick = {
                        saveCurrentDraft()
                        showUnsavedChangesConfirmation = false
                        onDismiss()
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showUnsavedChangesConfirmation = false
                            onDismiss()
                        },
                    ) {
                        Text("Discard")
                    }
                    TextButton(onClick = { showUnsavedChangesConfirmation = false }) {
                        Text("Keep editing")
                    }
                }
            },
        )
    }

    if (showRemoveConfirmation) {
        ConfirmationDialog(
            title = "Remove exercise?",
            body = "This removes '${item.exerciseName}' from this training day. Existing workout history stays unchanged.",
            confirmLabel = "Remove",
            onDismiss = { showRemoveConfirmation = false },
            onConfirm = {
                viewModel.removeTemplateExercise(item.id)
                showRemoveConfirmation = false
                onDismiss()
            },
        )
    }
}

private data class TemplateExerciseEditorDraft(
    val exerciseName: String,
    val sets: String,
    val repMin: String,
    val repMax: String,
    val currentWeight: String,
    val currentTargetReps: String,
    val increment: String,
    val restSeconds: String,
    val setupNote: String,
    val trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
    val durationSeconds: String = "60",
    val durationIncrementSeconds: String = "0",
)

private fun WorkoutTemplateExerciseEditorItem.toEditorDraft(): TemplateExerciseEditorDraft =
    TemplateExerciseEditorDraft(
        exerciseName = exerciseName,
        sets = plannedWorkingSets.toString(),
        repMin = repMin.toString(),
        repMax = repMax.toString(),
        currentWeight = formatCentiKg(currentWeightCentiKg),
        currentTargetReps = currentTargetReps.toString(),
        increment = formatCentiKg(incrementCentiKg),
        restSeconds = restSeconds.toString(),
        setupNote = setupNote,
        trackingMode = trackingMode,
        durationSeconds = targetDurationSeconds?.toString() ?: "60",
        durationIncrementSeconds = durationIncrementSeconds.toString(),
    )

@Composable
private fun TemplateExerciseEditor(
    item: WorkoutTemplateExerciseEditorItem,
    isFirst: Boolean,
    isLast: Boolean,
    viewModel: ProgramViewModel,
    showSupersetLabel: Boolean = true,
    showDragHandle: Boolean = true,
    showSaveButton: Boolean = true,
    onRemoveRequest: (() -> Unit)? = null,
    onDraftChange: (TemplateExerciseEditorDraft) -> Unit = {},
    onDragStep: (Int) -> Unit,
) {
    var exerciseName by remember(item) { mutableStateOf(item.exerciseName) }
    var sets by remember(item) { mutableStateOf(item.plannedWorkingSets.toString()) }
    var repMin by remember(item) { mutableStateOf(item.repMin.toString()) }
    var repMax by remember(item) { mutableStateOf(item.repMax.toString()) }
    var currentWeight by remember(item) { mutableStateOf(formatCentiKg(item.currentWeightCentiKg)) }
    var currentTargetReps by remember(item) { mutableStateOf(item.currentTargetReps.toString()) }
    var increment by remember(item) { mutableStateOf(formatCentiKg(item.incrementCentiKg)) }
    var restSeconds by remember(item) { mutableStateOf(item.restSeconds.toString()) }
    var setupNote by remember(item) { mutableStateOf(item.setupNote) }
    var trackingMode by remember(item) { mutableStateOf(item.trackingMode) }
    var durationSeconds by remember(item) { mutableStateOf(item.targetDurationSeconds?.toString() ?: "60") }
    var durationIncrementSeconds by remember(item) { mutableStateOf(item.durationIncrementSeconds.toString()) }
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
    val draft = TemplateExerciseEditorDraft(
        exerciseName = exerciseName,
        sets = sets,
        repMin = repMin,
        repMax = repMax,
        currentWeight = currentWeight,
        currentTargetReps = currentTargetReps,
        increment = increment,
        restSeconds = restSeconds,
        setupNote = setupNote,
        trackingMode = trackingMode,
        durationSeconds = durationSeconds,
        durationIncrementSeconds = durationIncrementSeconds,
    )

    LaunchedEffect(draft) {
        onDraftChange(draft)
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
                OutlinedTextField(
                    value = exerciseName,
                    onValueChange = { exerciseName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Exercise name") },
                    singleLine = true,
                )
                if (showDragHandle) {
                    ReorderDragHandle(
                        canDragUp = !isFirst,
                        canDragDown = !isLast,
                        isDragging = isDragging,
                        onDraggingChange = { isDragging = it },
                        onDragStep = onDragStep,
                    )
                }
            }
            Text(
                text = "${item.plannedWorkingSets} x " + trackingText(item.trackingMode, item.currentWeightCentiKg, item.currentTargetReps, item.targetDurationSeconds) + " | Rest ${item.restSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
            )
            TrackingModePicker(mode = trackingMode, onChange = { trackingMode = it })

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
                if (trackingMode == TrackingMode.DURATION) {
                    SmallNumberField("Target seconds", durationSeconds, { durationSeconds = it }, Modifier.weight(1f))
                } else SmallNumberField("Target reps", currentTargetReps, { currentTargetReps = it }, Modifier.weight(1f))
            }
            if (trackingMode != TrackingMode.DURATION) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Rep min", repMin, { repMin = it }, Modifier.weight(1f))
                SmallNumberField("Rep max", repMax, { repMax = it }, Modifier.weight(1f))
            }
            if (trackingMode == TrackingMode.WEIGHT_REPS) WeightAdjuster(
                label = "Weight kg",
                value = currentWeight,
                onValueChange = { currentWeight = it },
                incrementCentiKg = increment.toPositiveCentiKgOrDefault(item.incrementCentiKg),
                modifier = Modifier.fillMaxWidth(),
            )
            if (trackingMode == TrackingMode.DURATION) SmallNumberField(
                "Increment seconds", durationIncrementSeconds, { durationIncrementSeconds = it }, Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trackingMode == TrackingMode.WEIGHT_REPS) SmallNumberField("Increment kg", increment, { increment = it }, Modifier.weight(1f), decimal = true)
                SmallNumberField("Rest sec", restSeconds, { restSeconds = it }, Modifier.weight(1f))
            }
            if (trackingMode == TrackingMode.DURATION) Text(
                "Add these seconds after all planned sets meet the target. Use 0 to keep the target fixed.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = setupNote,
                onValueChange = { setupNote = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Setup note") },
                minLines = 2,
                maxLines = 4,
            )
            if (trackingMode != TrackingMode.DURATION && trackingMode == item.trackingMode) TemplateSetTargetsEditor(
                trackingMode = trackingMode,
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
            if (trackingMode == TrackingMode.WEIGHT_REPS && trackingMode == item.trackingMode) WarmupSchemeEditor(
                warmupSets = warmupSets,
                onEnableDefault = { viewModel.enableDefaultWarmupScheme(item.id) },
                onClear = { viewModel.clearWarmupScheme(item.id) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showSaveButton) {
                    TextButton(
                        onClick = {
                            viewModel.updateTemplateExercise(
                                item = item,
                                exerciseName = exerciseName,
                                sets = sets,
                                repMin = repMin,
                                repMax = repMax,
                                currentWeight = currentWeight,
                                currentTargetReps = currentTargetReps,
                                increment = increment,
                                restSeconds = restSeconds,
                                setupNote = setupNote,
                                trackingMode = trackingMode,
                                durationSeconds = durationSeconds,
                                durationIncrementSeconds = durationIncrementSeconds,
                            )
                        },
                    ) {
                        Text("Save")
                    }
                }
                TextButton(
                    onClick = {
                        if (onRemoveRequest == null) {
                            viewModel.removeTemplateExercise(item.id)
                        } else {
                            onRemoveRequest()
                        }
                    },
                ) {
                    Text("Remove")
                }
                TextButton(
                    onClick = { viewModel.duplicateTemplateExercise(item.id) },
                ) {
                    Text("Duplicate")
                }
                if (item.supersetGroupId == null) {
                    TextButton(
                        onClick = { viewModel.supersetWithPrevious(item.workoutTemplateId, item.id) },
                        enabled = !isFirst,
                    ) {
                        Text("Superset previous")
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.removeFromSuperset(item.id) },
                    ) {
                        Text("Remove superset")
                    }
                }
            }
        }
    }
}

private data class ReorderAutoScroller(
    val start: (Any, () -> Float) -> Unit,
    val stop: () -> Unit,
)

@Composable
private fun rememberReorderAutoScroller(
    listState: LazyListState,
): ReorderAutoScroller {
    val coroutineScope = rememberCoroutineScope()
    val edgeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val maxScrollStepPx = with(LocalDensity.current) { 22.dp.toPx() }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            autoScrollJob?.cancel()
            autoScrollJob = null
        }
    }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun startAutoScroll(itemKey: Any, draggedOffset: () -> Float) {
        stopAutoScroll()
        autoScrollJob = coroutineScope.launch {
            while (isActive) {
                val layoutInfo = listState.layoutInfo
                val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
                if (draggedItem != null) {
                    val draggedTop = draggedItem.offset + draggedOffset()
                    val draggedBottom = draggedTop + draggedItem.size
                    val viewportTop = layoutInfo.viewportStartOffset.toFloat()
                    val viewportBottom = layoutInfo.viewportEndOffset.toFloat()
                    val scrollDelta = when {
                        draggedTop < viewportTop + edgeThresholdPx -> {
                            -((viewportTop + edgeThresholdPx - draggedTop) / edgeThresholdPx)
                                .coerceIn(0f, 1f) * maxScrollStepPx
                        }
                        draggedBottom > viewportBottom - edgeThresholdPx -> {
                            ((draggedBottom - (viewportBottom - edgeThresholdPx)) / edgeThresholdPx)
                                .coerceIn(0f, 1f) * maxScrollStepPx
                        }
                        else -> 0f
                    }
                    if (scrollDelta != 0f) {
                        listState.scrollBy(scrollDelta)
                    }
                }
                delay(16)
            }
        }
    }

    return ReorderAutoScroller(
        start = ::startAutoScroll,
        stop = ::stopAutoScroll,
    )
}

@Composable
private fun ReorderDragHandle(
    canDragUp: Boolean,
    canDragDown: Boolean,
    isDragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    commitOnRelease: Boolean = true,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragStep: (Int) -> Unit,
) {
    val handleColor = when {
        isDragging -> MaterialTheme.colorScheme.onPrimaryContainer
        canDragUp || canDragDown -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    val hapticFeedback = LocalHapticFeedback.current
    var accumulatedDrag by remember { mutableStateOf(0f) }
    var gestureActive by remember { mutableStateOf(false) }
    val currentCanDragUp by rememberUpdatedState(canDragUp)
    val currentCanDragDown by rememberUpdatedState(canDragDown)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragStep by rememberUpdatedState(onDragStep)
    val currentCommitOnRelease by rememberUpdatedState(commitOnRelease)

    DisposableEffect(Unit) {
        onDispose {
            if (gestureActive) {
                gestureActive = false
                accumulatedDrag = 0f
                currentOnDraggingChange(false)
                currentOnDragCancel()
            }
        }
    }

    Canvas(
        modifier = Modifier
            .size(44.dp)
            .pointerInput(thresholdPx) {
                detectDragGestures(
                    onDragStart = {
                        if (!currentCanDragUp && !currentCanDragDown) return@detectDragGestures
                        gestureActive = true
                        accumulatedDrag = 0f
                        currentOnDraggingChange(true)
                        currentOnDragStart()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = {
                        if (!gestureActive) return@detectDragGestures
                        gestureActive = false
                        accumulatedDrag = 0f
                        currentOnDraggingChange(false)
                        currentOnDragCancel()
                    },
                    onDragEnd = {
                        if (!gestureActive) return@detectDragGestures
                        val dragStep = when {
                            accumulatedDrag <= -thresholdPx && currentCanDragUp -> -1
                            accumulatedDrag >= thresholdPx && currentCanDragDown -> 1
                            else -> 0
                        }
                        accumulatedDrag = 0f
                        gestureActive = false
                        currentOnDraggingChange(false)
                        if (currentCommitOnRelease) {
                            when (dragStep) {
                                -1 -> currentOnDragStep(-1)
                                1 -> currentOnDragStep(1)
                            }
                        }
                        currentOnDragEnd()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        if (!gestureActive) return@detectDragGestures
                        change.consume()
                        accumulatedDrag += dragAmount.y
                        currentOnDragDelta(dragAmount.y)
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
    trackingMode: TrackingMode,
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
                val label = if (trackingMode == TrackingMode.REPS) "Set ${setOrder + 1}: $repsLabel reps" else "Set ${setOrder + 1}: $repsLabel @ ${weightLabel}kg"
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
            if (trackingMode == TrackingMode.WEIGHT_REPS) WeightAdjuster(
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
                    onClick = { onSaveSetTarget(setOrder, if (trackingMode == TrackingMode.REPS) "0" else selectedWeight, selectedReps) },
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
    var exerciseName by rememberSaveable(workoutTemplateId) { mutableStateOf("") }
    var sets by rememberSaveable(workoutTemplateId) { mutableStateOf("3") }
    var repMin by rememberSaveable(workoutTemplateId) { mutableStateOf("8") }
    var repMax by rememberSaveable(workoutTemplateId) { mutableStateOf("12") }
    var currentWeight by rememberSaveable(workoutTemplateId) { mutableStateOf("0") }
    var currentTargetReps by rememberSaveable(workoutTemplateId) { mutableStateOf("8") }
    var increment by rememberSaveable(workoutTemplateId) { mutableStateOf("2.5") }
    var restSeconds by rememberSaveable(workoutTemplateId) { mutableStateOf("180") }
    var trackingMode by rememberSaveable(workoutTemplateId) { mutableStateOf(TrackingMode.WEIGHT_REPS) }
    var durationSeconds by rememberSaveable(workoutTemplateId) { mutableStateOf("60") }
    var durationIncrementSeconds by rememberSaveable(workoutTemplateId) { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            AddExerciseDialogContent(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                exercises = exercises,
                selectedExerciseId = selectedExerciseId,
                onSelectedExerciseChange = {
                    selectedExerciseId = it
                    exerciseName = exercises.firstOrNull { exercise -> exercise.id == it }?.name.orEmpty()
                },
                exerciseName = exerciseName,
                onExerciseNameChange = { name ->
                    exerciseName = name
                    val selectedExercise = exercises.firstOrNull { exercise -> exercise.id == selectedExerciseId }
                    if (selectedExercise?.name != name) selectedExerciseId = null
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
                trackingMode = trackingMode, onTrackingModeChange = { trackingMode = it },
                durationSeconds = durationSeconds, onDurationSecondsChange = { durationSeconds = it },
                durationIncrementSeconds = durationIncrementSeconds, onDurationIncrementSecondsChange = { durationIncrementSeconds = it },
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedExercise = exercises.firstOrNull { exercise -> exercise.id == selectedExerciseId }
                    if (selectedExercise != null && exerciseName.trim() == selectedExercise.name) {
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
                            trackingMode = trackingMode,
                            durationSeconds = durationSeconds,
                            durationIncrementSeconds = durationIncrementSeconds,
                        )
                    } else {
                        viewModel.createExerciseAndAddToWorkout(
                            workoutTemplateId = workoutTemplateId,
                            exerciseName = exerciseName,
                            sets = sets,
                            repMin = repMin,
                            repMax = repMax,
                            currentWeight = currentWeight,
                            currentTargetReps = currentTargetReps,
                            increment = increment,
                            restSeconds = restSeconds,
                            trackingMode = trackingMode,
                            durationSeconds = durationSeconds,
                            durationIncrementSeconds = durationIncrementSeconds,
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
internal fun AddExerciseDialogContent(
    exercises: List<ExerciseEntity>,
    selectedExerciseId: Long?,
    onSelectedExerciseChange: (Long?) -> Unit,
    exerciseName: String,
    onExerciseNameChange: (String) -> Unit,
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
    sessionOnly: Boolean = false,
    trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
    onTrackingModeChange: (TrackingMode) -> Unit = {},
    durationSeconds: String = "60",
    onDurationSecondsChange: (String) -> Unit = {},
    durationIncrementSeconds: String = "0",
    onDurationIncrementSecondsChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dismissedSuggestionQuery by remember { mutableStateOf<String?>(null) }
    var exerciseNameField by remember {
        mutableStateOf(TextFieldValue(text = exerciseName, selection = TextRange(exerciseName.length)))
    }
    LaunchedEffect(exerciseName) {
        if (exerciseName != exerciseNameField.text) {
            exerciseNameField = TextFieldValue(
                text = exerciseName,
                selection = TextRange(exerciseName.length),
            )
        }
    }
    val selectedExercise = exercises.firstOrNull { it.id == selectedExerciseId }
    val selectedExerciseUnmodified = selectedExercise != null && exerciseName == selectedExercise.name
    val matchingExercises = exercises
        .filterByExerciseSearchQuery(exerciseName)
        .take(6)
    val suggestionsExpanded = exerciseName.isNotBlank() &&
        !selectedExerciseUnmodified &&
        matchingExercises.isNotEmpty() &&
        dismissedSuggestionQuery != exerciseName

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = exerciseNameField,
                onValueChange = { value ->
                    dismissedSuggestionQuery = null
                    exerciseNameField = value
                    onExerciseNameChange(value.text)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Exercise name") },
                singleLine = true,
            )
            DropdownMenu(
                expanded = suggestionsExpanded,
                onDismissRequest = { dismissedSuggestionQuery = exerciseName },
                modifier = Modifier.fillMaxWidth(),
                properties = PopupProperties(focusable = false),
            ) {
                matchingExercises.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.name) },
                        onClick = {
                            dismissedSuggestionQuery = null
                            onSelectedExerciseChange(exercise.id)
                        },
                    )
                }
            }
        }
        TrackingModePicker(mode = trackingMode, onChange = onTrackingModeChange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallNumberField("Sets", sets, onSetsChange, Modifier.weight(1f))
            if (trackingMode == TrackingMode.DURATION) {
                SmallNumberField("Target seconds", durationSeconds, onDurationSecondsChange, Modifier.weight(1f))
            } else SmallNumberField("Target reps", currentTargetReps, onCurrentTargetRepsChange, Modifier.weight(1f))
        }
        if (!sessionOnly && trackingMode != TrackingMode.DURATION) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField("Rep min", repMin, onRepMinChange, Modifier.weight(1f))
                SmallNumberField("Rep max", repMax, onRepMaxChange, Modifier.weight(1f))
            }
        }
        if (trackingMode == TrackingMode.WEIGHT_REPS) WeightAdjuster(
            label = "Weight kg",
            value = currentWeight,
            onValueChange = onCurrentWeightChange,
            incrementCentiKg = increment.toPositiveCentiKgOrDefault(250),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!sessionOnly && trackingMode == TrackingMode.DURATION) SmallNumberField(
            "Increment seconds", durationIncrementSeconds, onDurationIncrementSecondsChange, Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!sessionOnly && trackingMode == TrackingMode.WEIGHT_REPS) SmallNumberField("Increment kg", increment, onIncrementChange, Modifier.weight(1f), decimal = true)
            SmallNumberField("Rest sec", restSeconds, onRestSecondsChange, Modifier.weight(1f))
        }
        if (!sessionOnly && trackingMode == TrackingMode.DURATION) Text(
            "Add these seconds after all planned sets meet the target. Use 0 to keep the target fixed.",
            style = MaterialTheme.typography.bodySmall,
        )
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
