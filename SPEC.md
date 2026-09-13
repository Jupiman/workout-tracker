# Workout Tracker — Android MVP Implementation Specification

## 1. Goal

Build a simple native Android workout tracking application focused on strength/hypertrophy training.

This is primarily a single-user personal application. The main priorities are:

1. Extremely fast workout logging.
2. Automatic double-progression tracking.
3. Independent progression for the same exercise used in different workouts.
4. Reliable workout history.
5. Simple workout-program management.
6. Rest timer notifications.
7. Supersets.
8. Ad-hoc normal sets, AMRAP sets and drop sets.

Do NOT add unnecessary fitness functionality.

This application is not intended to be a comprehensive fitness platform.

---

# 2. Non-goals

Do NOT implement these features unless explicitly requested later:

- 1RM calculation or estimation
- RPE / RIR tracking
- body weight tracking
- body measurements
- calories
- workout volume analytics
- graphs or statistics dashboards
- muscle-group analytics
- social features
- leaderboards
- achievements
- AI coaching
- cloud accounts
- authentication
- online backend
- exercise videos
- workout recommendations
- scheduling workouts to particular weekdays
- Apple / Google Health integration
- wearable integration in the MVP; keep future wearable support documented as a TODO
- nutrition tracking

Keep the application focused.

---

# 3. Technology

Use:

- Kotlin
- Jetpack Compose
- Material 3
- Room / SQLite
- Navigation Compose
- ViewModel
- Kotlin Coroutines / Flow

Architecture:

- single-activity Compose app
- MVVM
- repository layer between Room and ViewModels
- local-first / offline-only
- no network dependency

Avoid unnecessary frameworks.

Dependency injection frameworks such as Hilt are not necessary for this project. Simple application-level dependency construction is sufficient.

Minimum Android SDK: 26.

The project must compile and run in Android Studio.

---

# 4. Product model

The hierarchy is:

Program  
→ Workout Template  
→ Exercise Instance  
→ Progression State

Example:

Program: `Current Program`

Workout templates:

- Day A
- Day B
- Day C

Day A might contain:

- Bench Press
- Lat Pulldown
- Cable Lateral Raise

Day C might also contain:

- Bench Press
- Machine Row
- Cable Lateral Raise

The important rule is:

The same exercise used in multiple workout templates must have independent progression.

Example:

Day A:

`Bench Press — 70 kg — 8-12 reps`

Day C:

`Bench Press — 55 kg — 12-15 reps`

These are independent progression tracks even though both reference the same `Bench Press` exercise.

Progression therefore belongs to the exercise instance inside a workout template, NOT globally to the exercise.

---

# 5. Exercise library

Maintain a simple reusable Exercise library.

Exercise fields:

- id
- name
- archived

Example:

- Bench Press
- Incline Dumbbell Press
- Lat Pulldown
- Machine Row

No muscle groups, equipment categories or other metadata are required.

The user must be able to:

- create an exercise
- rename an exercise
- reuse an exercise in multiple workout templates
- archive an exercise

When adding an exercise to a workout, allow:

- selecting an existing exercise
- creating a new exercise directly from that flow

Renaming an Exercise may change the name shown in current workout templates.

It must NOT retroactively change names stored in completed workout history.

---

# 6. Programs

Support multiple programs, but only one program can be active at a time.

Program fields:

- id
- name
- active
- archived
- createdAt

Example:

`Current Cut Program`

A program contains an ordered collection of workout templates.

Workout templates are NOT attached to weekdays.

Examples:

- Day A — Back + Biceps
- Day B — Chest + Shoulders
- Day C — Legs

The user progresses through them cyclically.

Example:

A → B → C → A → B → C

The application should recommend the next workout based on the most recently completed workout from the active program.

If the most recently completed workout was Day B:

Next recommended workout = Day C.

If it was the final template:

Next recommended workout = first template.

The user must still be able to manually start any workout template.

Changing the active program must not alter historical sessions.

If a newly activated program has no previous completed sessions, recommend its first workout.

---

# 7. Workout template

Each WorkoutTemplate contains ordered WorkoutTemplateExercise entries.

Fields:

WorkoutTemplate:

- id
- programId
- name
- sortOrder

WorkoutTemplateExercise:

- id
- workoutTemplateId
- exerciseId
- sortOrder
- plannedWorkingSets
- repMin
- repMax
- increment
- restSeconds
- optional supersetGroupId

Example:

Bench Press

- 3 working sets
- 8-12 reps
- current weight: 70 kg
- current target reps: 8
- increment: 2.5 kg
- rest: 180 seconds

---

# 8. Weight representation

For MVP, use kilograms only.

Do not use Float or Double for stored weight values.

Store weight internally as integer hundredths of kilograms.

Examples:

`70 kg = 7000`

`72.5 kg = 7250`

`1.25 kg = 125`

Display them to the user as normal kg values.

This avoids floating-point comparison problems.

The UI should accept decimal weights.

---

# 9. Progression state

Progression must be stored separately from the static WorkoutTemplateExercise configuration.

Create a one-to-one entity:

ProgressionState

Fields:

- workoutTemplateExerciseId
- currentWeight
- currentTargetReps
- updatedAt

Example:

WorkoutTemplateExercise:

Bench Press

Configuration:

- sets = 3
- repMin = 8
- repMax = 12
- increment = 2.5 kg

ProgressionState:

- currentWeight = 70 kg
- currentTargetReps = 10

This separation is important.

Workout configuration describes the progression rules.

ProgressionState describes where the user currently is inside those rules.

---

# 10. Progression algorithm

Use double progression.

Example configuration:

- 3 working sets
- 8-12 reps
- 70 kg
- increment 2.5 kg

Initial state:

`3 × 8 @ 70 kg`

The workout session creates three planned working sets:

Set 1: 8 × 70  
Set 2: 8 × 70  
Set 3: 8 × 70

If all three sets are successfully completed:

Next workout:

`3 × 9 @ 70 kg`

Then:

`3 × 10 @ 70 kg`

Then:

`3 × 11 @ 70 kg`

Then:

`3 × 12 @ 70 kg`

If all three sets of 12 are successfully completed:

Next workout becomes:

`3 × 8 @ 72.5 kg`

The weight was increased by the configured increment and target reps reset to repMin.

---

# 11. Definition of successful progression

An exercise progresses only if ALL planned progression-relevant working sets were successfully completed.

For each planned set:

actual reps must be >= prescribed reps

AND

actual weight must equal prescribed weight.

Example:

Target:

3 × 10 @ 70 kg

Actual:

10 × 70  
10 × 70  
10 × 70

Result:

SUCCESS

Next target:

3 × 11 @ 70 kg

Example:

10 × 70  
10 × 70  
9 × 70

Result:

FAIL

Next target remains:

3 × 10 @ 70 kg

Example:

10 × 70  
10 × 67.5  
10 × 70

Result:

FAIL

Automatic progression must not try to interpret manually changed weights.

Hold the same progression state.

The user can manually modify current progression values from the program editor if necessary.

---

# 12. More reps than required

Doing more reps than prescribed still counts as successfully completing the set.

Example:

Target:

10 reps

Actual:

12 reps

Result:

successful set.

However progression advances by only ONE progression step.

Do not skip directly from 10 to 12.

---

# 13. Planned sets versus extra sets

Progression is based ONLY on the planned working sets generated from the workout template.

Example:

Planned:

3 working sets.

During the workout the user adds a fourth normal set.

That fourth set:

- is stored in history
- is visible in the workout
- does NOT influence progression

Likewise:

- AMRAP sets
- drop sets
- extra normal sets

do not influence automatic progression.

Only originally planned working sets count.

---

# 14. Starting a workout

Starting a WorkoutTemplate creates a WorkoutSession.

The session must snapshot the current workout state.

Do NOT simply reference the live program and later reconstruct history from it.

At workout start:

For every WorkoutTemplateExercise:

1. Read its current ProgressionState.
2. Create a SessionExercise snapshot.
3. Create its planned SessionSet entries.
4. Store all relevant metadata.

For example:

Current progression:

Bench Press

70 kg  
target = 9 reps  
3 sets

Create:

SessionExercise:

Bench Press snapshot

SessionSets:

9 × 70  
9 × 70  
9 × 70

These prescribed values must never change even if the user later edits the program.

---

# 15. Workout session states

WorkoutSession status:

- ACTIVE
- COMPLETED
- PARTIAL

Only one ACTIVE workout may exist at a time.

If the application is closed while a workout is active, reopening the application must offer:

`Resume workout`

The active session must persist in Room.

Do not keep important session state only in ViewModel memory.

---

# 16. Finishing a workout

If all planned sets have been completed:

Finish normally as COMPLETED.

If some planned sets are incomplete:

Ask:

`Some planned sets are incomplete. Finish workout anyway?`

If confirmed:

store session as PARTIAL.

For progression:

Evaluate progression independently for each exercise.

If all planned sets belonging to an exercise were completed, that exercise may progress even if the overall workout was PARTIAL.

Exercises with missing planned sets do not progress.

Progression updates must happen transactionally when the session is finished.

Progression must only be applied once.

Store an appropriate flag such as:

`progressionApplied = true`

to prevent accidental duplicate application.

---

# 17. Discarding a workout

Provide:

`Discard workout`

Require confirmation.

Discard removes the current ACTIVE session and its associated session data.

No progression changes are applied.

---

# 18. Workout logging UI

The workout screen is the most important UI in the application.

Prioritize speed over visual complexity.

Each exercise should be displayed as a card.

Example:

Bench Press

Target:

3 × 10 @ 70 kg

Rest: 3:00

Sets:

1. 70 kg × 10   [✓]
2. 70 kg × 10   [✓]
3. 70 kg × 10   [✓]

For each pending planned set, values are pre-filled from the prescription.

If the user performed the set exactly as prescribed:

ONE TAP on the completion button must log the set.

This should be the normal interaction.

---

# 19. Logging a failed or modified set

If the user did not perform the prescribed reps:

they should modify the reps first and then press complete.

Example:

Target:

10 reps

Actual:

8

Interaction:

tap reps → change 10 to 8 → complete.

Likewise the weight must be editable if the actual weight differed.

Do not require navigating to another full-screen page merely to edit a set.

Prefer inline editing or a small dialog/bottom sheet.

---

# 20. Editing completed sets during an active workout

While the WorkoutSession is ACTIVE:

the user must be able to edit previously completed sets.

Allow changing:

- actual reps
- actual weight

The user must also be able to mark the set incomplete again if it was accidentally confirmed.

Once the entire workout is finalized and stored in History, treat it as read-only in MVP.

Historical session editing is outside MVP because it creates complicated progression rollback/recalculation behavior.

---

# 21. Additional normal set

Inside an active workout provide:

`Add set`

Options:

- Normal set
- AMRAP
- Drop set

Normal extra set defaults to:

- current prescribed weight
- current prescribed reps

It is marked:

`isPlanned = false`

`countsForProgression = false`

The user may modify weight/reps before completing it.

---

# 22. AMRAP set

AMRAP = As Many Reps As Possible.

When adding AMRAP:

Default weight should be the current exercise weight.

Reps are not pre-filled as a target.

The user performs the set and enters the actual number of reps.

Store:

setType = AMRAP

AMRAP sets:

- appear in workout history
- do not affect automatic progression

Example history:

AMRAP — 70 kg × 14

---

# 23. Drop set

A Drop Set is another session-only set type.

When adding a drop set:

default its initial weight from the previous set, but require the user to adjust it as needed.

Store:

setType = DROP

It records:

- actual weight
- actual reps

Drop sets:

- appear in history
- do not affect progression

A drop set should visually appear connected to the preceding normal set where practical.

Avoid implementing complex multi-stage drop-set programming in MVP.

A drop set is simply another logged set marked as DROP.

---

# 24. Skipping a planned set

Provide an option:

`Skip set`

This marks the planned set as SKIPPED.

Skipped planned sets cause progression to fail for that exercise.

The set remains visible in the workout history as skipped.

---

# 25. Supersets

Exercises may be grouped into a SupersetGroup.

Example:

A1 Cable Lateral Raise  
A2 Triceps Pushdown

Execution:

A1 set 1  
A2 set 1  
REST

A1 set 2  
A2 set 2  
REST

A1 set 3  
A2 set 3  
REST

The rest timer must NOT start after A1.

It starts after the final exercise in the current superset round.

---

# 26. Superset configuration

Create:

SupersetGroup

Fields:

- id
- workoutTemplateId
- restSeconds

WorkoutTemplateExercise may contain:

`supersetGroupId`

Exercises in the same group execute according to their normal sort order.

For MVP, require all exercises inside one superset group to have the same number of planned working sets.

Prevent invalid configurations in the program editor.

This significantly simplifies workout sequencing.

---

# 27. Superset editor UX

Keep creation simple.

For an exercise in the workout editor provide an action such as:

`Superset with previous exercise`

If the previous exercise does not belong to a superset:

create a new SupersetGroup containing both.

If the previous exercise already belongs to a SupersetGroup:

add the current exercise to that group.

Allow removing an exercise from the superset.

Show grouped exercises visually together.

---

# 28. Rest timer

Normal exercise:

Completing a working set starts that exercise's configured rest timer.

Superset:

Do not start the timer until the last exercise of the current superset round has been completed.

Then start SupersetGroup.restSeconds.

Display a persistent countdown while the workout screen is open.

Example:

REST 01:42

Provide simple actions:

- +30 sec
- Skip

No additional timer analytics are required.

---

# 29. Rest timer persistence

Do not implement the timer only as a decrementing in-memory counter.

Store a target timestamp:

`restEndsAt`

Remaining time should always be calculated as:

`restEndsAt - currentTime`

This makes the timer survive:

- screen rotation
- Compose recomposition
- application backgrounding
- process recreation

If the timer has already expired when the app resumes, display it as finished.

---

# 30. Rest timer notification

When the rest period ends:

- play a notification sound
- vibrate if allowed
- show an Android notification when the app is in the background

Request notification permission when required on modern Android versions.

Use Android-supported scheduling mechanisms so that the timer can notify the user when the screen is off.

Avoid continuously running polling loops.

The notification should clearly say something like:

`Rest finished`

Do not build a complex notification system.

---

# 31. Program editing

The user must be able to:

- create program
- rename program
- activate program
- archive program
- create workout template
- rename workout template
- reorder workout templates
- add exercise
- remove exercise
- reorder exercises
- change working set count
- change rep range
- change rest time
- change increment
- change current progression weight
- change current target reps
- create/remove supersets

Changes apply only to FUTURE workouts.

They must never modify completed historical sessions.

---

# 32. Changing progression configuration

If repMin or repMax changes:

If currentTargetReps remains inside the new range:

keep it.

Otherwise clamp it into the new range.

Example:

Old range:

8-12

Current target:

11

New range:

6-10

New target:

10

The user must still be able to manually change currentTargetReps.

Changing increment does not change current weight.

Changing current weight manually updates ProgressionState.currentWeight.

---

# 33. History

Create a History screen.

Show completed workout sessions in reverse chronological order.

Example:

10 Sep 2026  
Day B — Chest + Shoulders

8 Sep 2026  
Day A — Back + Biceps

Selecting a workout opens full details.

---

# 34. Historical workout detail

Display the exact session snapshot.

Example:

Day B — Chest + Shoulders  
10 Sep 2026  
09:02–10:05

Bench Press

70 kg × 10  
70 kg × 10  
70 kg × 9

Incline Dumbbell Press

30 kg × 10  
30 kg × 10  
30 kg × 10

Cable Lateral Raise

12.5 kg × 12  
12.5 kg × 12  
DROP 8 kg × 15

AMRAP sets must be labelled.

Drop sets must be labelled.

Skipped sets must be labelled.

History does NOT need graphs or analytics.

---

# 35. Historical data must be snapshots

This is a critical requirement.

Completed workout history must remain accurate even if the user later:

- renames the exercise
- deletes or archives the exercise
- changes weight
- changes target reps
- changes rep range
- changes number of sets
- changes exercise order
- changes rest time
- changes supersets
- edits the workout template
- archives the program
- switches programs

Therefore WorkoutSession data must contain snapshots.

Never reconstruct old workouts from current program configuration.

---

# 36. Database schema

Use Room.

Recommended entities:

## Exercise

- id
- name
- archived
- createdAt

## Program

- id
- name
- active
- archived
- createdAt

## WorkoutTemplate

- id
- programId
- name
- sortOrder

## SupersetGroup

- id
- workoutTemplateId
- restSeconds

## WorkoutTemplateExercise

- id
- workoutTemplateId
- exerciseId
- sortOrder
- plannedWorkingSets
- repMin
- repMax
- incrementCentiKg
- restSeconds
- supersetGroupId nullable

## ProgressionState

Primary key:

- workoutTemplateExerciseId

Fields:

- currentWeightCentiKg
- currentTargetReps
- updatedAt

## WorkoutSession

- id
- sourceWorkoutTemplateId nullable
- sourceProgramId nullable
- programNameSnapshot
- workoutNameSnapshot
- startedAt
- completedAt nullable
- status
- restEndsAt nullable
- progressionApplied

## SessionExercise

- id
- sessionId
- sourceWorkoutTemplateExerciseId nullable
- exerciseNameSnapshot
- sortOrderSnapshot
- plannedSetCountSnapshot
- repMinSnapshot
- repMaxSnapshot
- targetRepsSnapshot
- prescribedWeightCentiKgSnapshot
- incrementCentiKgSnapshot
- restSecondsSnapshot
- supersetGroupSnapshot nullable
- supersetRestSecondsSnapshot nullable

## SessionSet

- id
- sessionExerciseId
- setOrder
- setType
- isPlanned
- countsForProgression
- prescribedWeightCentiKg nullable
- prescribedReps nullable
- actualWeightCentiKg nullable
- actualReps nullable
- status
- completedAt nullable

Enums:

WorkoutSessionStatus:

- ACTIVE
- COMPLETED
- PARTIAL

SessionSetStatus:

- PENDING
- COMPLETED
- SKIPPED

SetType:

- WORKING
- EXTRA
- AMRAP
- DROP

---

# 37. Relationship rules

Program:

1 → many WorkoutTemplates

WorkoutTemplate:

1 → many WorkoutTemplateExercises

Exercise:

1 → many WorkoutTemplateExercises

WorkoutTemplateExercise:

1 → 1 ProgressionState

WorkoutSession:

1 → many SessionExercises

SessionExercise:

1 → many SessionSets

Historical entities must survive removal or archiving of their source template entities.

Source IDs in history are convenience references only.

Snapshots are authoritative.

---

# 38. Workout creation transaction

When starting a workout:

Create WorkoutSession.

For each WorkoutTemplateExercise:

Create SessionExercise snapshot.

Then generate N planned SessionSets where:

N = plannedWorkingSets

Each planned set gets:

- type = WORKING
- isPlanned = true
- countsForProgression = true
- prescribedWeight = ProgressionState.currentWeight
- prescribedReps = ProgressionState.currentTargetReps
- status = PENDING

Perform workout creation transactionally.

---

# 39. Progression update transaction

When finishing a workout:

For each SessionExercise:

Retrieve only sets where:

`countsForProgression == true`

If any are PENDING or SKIPPED:

do not progress.

Otherwise check every set.

Successful set requires:

actualWeight == prescribedWeight

AND

actualReps >= prescribedReps.

If every progression-relevant set succeeds:

If currentTargetReps < repMax:

`currentTargetReps += 1`

Else:

`currentWeight += increment`

`currentTargetReps = repMin`

If any set fails:

do not change ProgressionState.

Then mark:

`progressionApplied = true`

The progression application and session completion must be done transactionally.

---

# 40. Home screen

The main screen should be extremely simple.

If an ACTIVE session exists:

show a prominent:

`Resume workout`

Otherwise display:

Active Program

Next Workout:

`Day B — Chest + Shoulders`

Large button:

`START WORKOUT`

Also provide:

`Choose another workout`

The app should normally be usable from launch to starting the expected workout in one tap.

---

# 41. Main navigation

Use three primary destinations:

- Workout
- Program
- History

Settings can be accessed from a small top-level menu rather than requiring a fourth primary tab.

Keep navigation minimal.

---

# 42. Program screen

Show:

active program name

then its ordered workout templates.

Example:

Current Program

1. Back + Biceps
2. Chest + Shoulders
3. Legs

Allow:

- add workout
- edit workout
- reorder workouts

Inside each workout:

show ordered exercises and their important configuration.

Example:

Bench Press  
3 × 8-12  
70 kg → +2.5 kg  
Rest 3:00

---

# 43. Workout screen usability

During workout execution:

- use large tap targets
- avoid tiny buttons
- keep the completion button easy to reach
- minimize text input
- keep weight/reps visible
- do not require opening a new screen for every set
- show the current rest timer without hiding workout contents
- visually distinguish completed and pending sets
- visually group supersets

The user should be able to operate the app while physically training without excessive interaction.

---

# 44. Data persistence requirements

The application must work entirely offline.

All data must survive application restart.

Persist:

- programs
- workouts
- exercises
- progression state
- workout history
- active workout
- completed sets
- current rest timer deadline

Room is the authoritative storage layer.

Do not rely on transient UI state for important data.

---

# 45. Application restart during workout

Scenario:

The user completes two sets.

Android kills the process.

The user reopens the app.

Expected behavior:

The active WorkoutSession still exists.

The two completed sets are still completed.

The remaining sets are still pending.

If a rest timer was active, remaining time is recalculated from the stored deadline.

The user can continue normally.

---

# 46. Deletion and archiving

Prefer archiving over destructive deletion for entities that may have historical relationships.

Historical sessions must never disappear just because:

- a program is archived
- a workout template is changed
- an exercise is archived

History is independent.

---

# 47. History immutability

For MVP:

Completed workout sessions are read-only.

Do not implement historical editing.

The user may correct mistakes before finishing the workout.

This prevents complicated automatic progression rollback logic.

---

# 48. Validation

Prevent invalid configurations.

Examples:

`repMin > repMax` → invalid

`plannedWorkingSets < 1` → invalid

`increment <= 0` → invalid

`restSeconds < 0` → invalid

`currentTargetReps` outside repMin–repMax → invalid or automatically clamped

Exercises inside one superset must have matching plannedWorkingSets.

Exercise name cannot be empty.

Workout name cannot be empty.

Program name cannot be empty.

---

# 49. Important edge cases

Handle the following:

### Same exercise in two workouts

Progress independently.

### Same exercise twice in the SAME workout

Still treat each WorkoutTemplateExercise instance independently.

Example:

Bench Press heavy  
Bench Press back-off

They may reference the same Exercise but have different ProgressionState entities.

### Miss one set

No progression.

### Do more reps than required

Progress only one step.

### Extra set

Does not influence progression.

### AMRAP

Does not influence progression.

### Drop set

Does not influence progression.

### Skipped planned set

No progression.

### Partial workout

Fully completed exercises may progress.

Incomplete exercises do not.

### Program edited after workout

History remains unchanged.

### Exercise renamed

History keeps old snapshot name.

### Workout reordered

History remains unchanged.

### Application closed mid-workout

Resume active session.

---

# 50. Suggested visual style

Use a clean Material 3 interface.

No elaborate branding is required.

Prefer:

- dark-mode support from the beginning
- simple cards
- clear typography
- large numeric values
- strong distinction between pending/completed sets
- restrained use of animations

Functionality and speed are more important than visual decoration.

Support system light/dark mode.

---

# 51. Testing requirements

Create unit tests for the progression engine.

At minimum test:

1. 8 reps success → 9 reps.
2. 9 reps success → 10 reps.
3. repMax success → weight increment + repMin.
4. one failed set → no progression.
5. skipped set → no progression.
6. additional normal set ignored.
7. AMRAP ignored.
8. drop set ignored.
9. same exercise in two templates progresses independently.
10. two instances of same exercise inside one template progress independently.
11. partial workout updates only fully completed exercises.
12. progression cannot be applied twice.

Also test critical Room relationships where appropriate.

---

# 52. Acceptance scenarios

## Scenario A — normal progression

Configuration:

Bench Press

3 sets  
8-12 reps  
70 kg  
increment 2.5 kg

Workout:

8 / 8 / 8 @ 70

Next workout:

3 × 9 @ 70

PASS.

---

## Scenario B — failed set

Target:

3 × 9 @ 70

Actual:

9  
9  
8

Next workout:

3 × 9 @ 70

PASS.

---

## Scenario C — weight increase

Target:

3 × 12 @ 70

Actual:

12  
12  
12

Next workout:

3 × 8 @ 72.5

PASS.

---

## Scenario D — AMRAP ignored

Target:

3 × 10 @ 70

Actual planned sets:

10  
10  
10

Additional AMRAP:

14 × 70

Next workout:

3 × 11 @ 70

PASS.

AMRAP does not cause larger progression.

---

## Scenario E — same exercise in two workouts

Day A Bench Press:

70 kg  
8-12

Day C Bench Press:

55 kg  
12-15

Completing Day A changes only Day A progression.

Day C remains unchanged.

PASS.

---

## Scenario F — history snapshots

September 10 workout:

Bench Press

70 × 10  
70 × 10  
70 × 10

Later user renames exercise:

`Barbell Bench Press`

and changes template weight to:

75 kg.

September 10 History must still display:

`Bench Press`

70 × 10  
70 × 10  
70 × 10

PASS.

---

## Scenario G — superset

Superset:

Lateral Raise  
Triceps Pushdown

After completing Lateral Raise set 1:

NO REST TIMER.

Focus moves to Triceps Pushdown set 1.

After completing Triceps Pushdown set 1:

REST TIMER STARTS.

PASS.

---

# 53. Implementation strategy

Do not attempt to build every screen at once.

Implement in approximately this order:

Phase 1:

- project skeleton
- Room entities
- DAOs
- repositories
- database
- navigation

Phase 2:

- exercise library
- program creation
- workout templates
- progression state editing

Phase 3:

- starting workout
- workout snapshot generation
- set logging
- active session persistence

Phase 4:

- progression engine
- progression tests

Phase 5:

- rest timer
- timer persistence
- notifications

Phase 6:

- supersets
- AMRAP
- drop sets
- extra sets

Phase 7:

- history
- historical workout details

Phase 8:

- polish
- validation
- edge cases
- dark mode

---

# 54. Planned UI and Logic Updates

These updates should be implemented after the MVP foundation is stable.

Treat them as a focused follow-up plan, not as broad product expansion.

Prioritize:

1. fast workout logging
2. progression correctness
3. clear workout editing
4. readable superset/drop-set grouping
5. preserving historical snapshots

Implementation phases:

Phase 54A - workout logging cleanup:

- collapse completed sets in the active workout
- collapse superset rounds only after the whole round is complete
- mark pending sets as skipped when a partial workout is finished
- keep history read-only

Phase 54B - weight adjustment controls:

- add compact weight steppers to active workout set rows
- add matching weight controls to program exercise editing
- keep typed numeric entry available

Phase 54C - clearer grouping:

- improve superset grouping in active workouts
- improve drop-set visual hierarchy
- keep rest behavior aligned with superset rounds

Phase 54D - per-set targets and finish decisions:

- add per-set planned targets to the template/session model
- support changed-set finish choices
- replace the current progression review wording with the new explicit options

Phase 54E - warm-up schemes:

- add warm-up scheme configuration
- generate warm-up session sets from percentages
- snapshot warm-ups into workout history

Phase 54F - program builder UX and reorder polish:

- replace the staged Program -> Training Day -> Edit Day navigation with an in-place builder inside the Program destination
- split program management and the exercise library into `Programs` and `Exercises` subtabs
- use a program selector plus horizontally scrollable Training Day tabs instead of `Open` / `Back` navigation
- show exercises as compact summary cards and edit one exercise at a time in a modal bottom sheet
- replace Up/Down controls and long-press reorder behavior with direct drag-handle reordering and animated item movement

Phase 54G - wearable readiness:

- document future Wear OS integration points
- keep phone UI and repository APIs decoupled enough for a future watch surface

---

## 54.1 Program builder UX redesign

The Program destination must feel like a focused builder, not like a chain of form pages or one long administration screen.

The existing staged navigation:

`Programs list -> Open program -> Training Days list -> Edit day -> Back`

must be replaced for normal editing.

The primary goals are:

- eliminate routine `Open` / `Back` navigation while building a program
- minimize vertical scrolling
- avoid showing editable text fields for every exercise at the same time
- make the current Program and Training Day context obvious
- keep common actions fast while moving destructive or uncommon actions out of the main visual hierarchy
- preserve all existing business rules, persistence, progression behavior, and historical snapshot behavior

### Program destination information architecture

Keep the existing bottom navigation:

- `Workout`
- `Program`
- `History`

Inside the `Program` destination, add two top-level subtabs:

1. `Programs`
2. `Exercises`

Use a Material 3 tab/segmented control appropriate for two sibling views.

Do not create another bottom-navigation destination for the exercise library.

The selected subtab should remain selected while the user stays in the Program destination.

### Programs subtab

The Programs subtab is the main program builder.

At the top, show a compact program selector for all non-archived programs.

Example:

`[ Test v ]    Active`

Requirements:

- the active program should be clearly marked
- selecting a program only selects it for editing; it must NOT implicitly make it active
- provide program-level actions through a nearby overflow/menu or compact secondary action:
  - create new program
  - rename selected program
  - set selected program active
  - archive selected program
- creating or renaming a program should use a small dialog or modal bottom sheet
- do not keep a permanent `Program name` TextField visible on the main builder screen
- do not require an `Open` button

When entering the Programs subtab:

- select the active program by default if one exists
- otherwise select the first available non-archived program
- if no program exists, show a clear empty state with a primary `Create program` action

### Training Day selector

Below the program selector, show the selected program's Training Days as a horizontally scrollable tab/chip row.

Use the user-facing term `Day` or `Training Day`.

Do NOT expose the internal term `Workout Template` in the UI.

Example:

`[ Day 1 ] [ Day 2 ] [ Day 3 ] [ + ]`

Requirements:

- tapping a Day selects it and immediately changes the editor content below
- no `Open day` action
- no `Back` button is needed to move between Days
- the final `+` action creates a new Training Day
- creating a Day should use a small dialog or modal bottom sheet for its name
- newly created Days should become selected automatically
- if the selected program has no Days, show a clear empty state and `Add training day`

The selected Day should remain selected while switching between `Programs` and `Exercises` and back when practical.

### Training Day header

The selected Day content should start with a compact header.

Show:

- Day name
- optional small edit/rename action
- optional overflow menu for secondary actions

Day-level secondary actions:

- rename
- reorder Days
- remove Day

Do not keep the Day name as a permanently expanded TextField.

Do not show permanent `Save`, `Move up`, `Move down`, or `Remove` text actions in the normal Day header.

Renaming should happen through a small dialog or bottom sheet.

### Reordering Training Days

Do not require Up/Down buttons.

Because Days are displayed as horizontal tabs, do not force complex drag behavior directly on the tabs for v1.1.

Provide a `Reorder days` action from the Day/program overflow menu.

`Reorder days` opens a compact modal bottom sheet containing the Days as a vertical draggable list.

The reorder sheet must use the same animated drag behavior described in Phase 54F:

- drag from a visible handle
- no long press
- surrounding rows animate out of the way
- persist `sortOrder` after drop

After dismissing the reorder sheet, the horizontal Day tabs must reflect the new order.

### Exercise list inside a Training Day

Below the Day header, show a primary `Add exercise` action followed by the ordered exercises.

Do NOT render every exercise as a permanently expanded form.

Each exercise should normally render as a compact summary card.

Example:

`Bench Press`

`3 x 8-12  •  70 kg  •  target 8`

`+2.5 kg  •  Rest 180s`

Possible small badges/metadata:

- `Superset`
- warm-up enabled
- custom per-set targets

Requirements:

- the exercise name must be visually dominant
- important training configuration should be readable without opening the editor
- avoid multiple rows of outlined TextFields on the main Day screen
- tapping the card body opens that exercise's editor
- the drag handle is dedicated to reordering and must not open the editor
- destructive actions should not occupy permanent primary-button space

### Adding an exercise

Tapping `Add exercise` opens a modal bottom sheet or dialog.

The flow must allow:

- selecting an existing Exercise from the exercise library
- creating a new Exercise without leaving the flow

After an Exercise is selected/created:

- show the exercise configuration editor
- save it into the currently selected Training Day
- return to the Day builder with the new exercise visible

Do not navigate to a separate full-screen Exercise Library page merely to add an exercise.

### Exercise editor

Tapping an exercise card opens a Material 3 `ModalBottomSheet` or equivalent focused editor.

Only one exercise should be edited at a time.

The editor contains the existing configuration controls, including where applicable:

- sets
- rep minimum
- rep maximum
- current/progression weight
- target reps
- increment
- rest
- per-set target configuration
- warm-up configuration
- superset membership/actions

Use the weight control defined in section 54.3.

Group related controls visually instead of presenting one uninterrupted stack of unrelated fields.

The editor should have clear `Save` and dismiss/cancel behavior.

Closing the editor without saving must not silently persist partial edits unless that field is intentionally auto-saved.

Removing an exercise should be a secondary/destructive action inside the editor or overflow menu and should require confirmation when accidental removal would be costly.

### Exercise card actions

The normal collapsed card should expose only:

- the card body for Edit
- a visible drag handle
- optionally a compact overflow icon if required

Do not permanently show text actions such as:

- `Save`
- `Up`
- `Down`
- `Remove`
- `Superset previous`
- `Remove superset`

Move uncommon actions to the editor or overflow menu.

### Supersets in the Day builder

Superset exercises must be visually grouped rather than only repeating a text label.

For editing/reordering:

- exercises in the same superset must remain contiguous
- a superset group should render as one visible grouped container
- the group may have its own drag handle so the entire superset can move as one unit
- if individual member reordering is supported, keep members constrained inside the group
- moving an exercise into or out of a superset should happen through an explicit superset action, not accidentally through drag-and-drop

This keeps reordering predictable and avoids silently changing superset semantics.

### Exercises subtab

The `Exercises` subtab contains the reusable exercise library only.

Show a clean list of non-archived exercises with actions to:

- create
- rename
- archive

Do not mix Program creation, Training Day editing, or Day-specific progression controls into this subtab.

Creating and renaming an Exercise should use a dialog or modal bottom sheet rather than permanent inline TextFields for every row.

If the list grows large, a simple search/filter field may be added, but search is not required for v1.1.

### Visual design requirements

The Program builder should look like a consumer mobile application rather than an administration form.

Prefer:

- compact cards
- clear typographic hierarchy
- Material 3 tabs/chips
- icon buttons for secondary actions
- bottom sheets/dialogs for focused editing
- consistent spacing
- subtle tonal surfaces instead of borders around every piece of information
- concise metadata lines
- restrained motion that communicates state changes

Avoid:

- large numbers of permanently visible outlined TextFields
- large empty cards containing only one or two actions
- repetitive `Save` buttons on every row
- `Open` / `Back` buttons for routine navigation between Program and Day
- Up/Down text buttons for ordering

### Navigation and state rules

This redesign is primarily a UI/navigation refactor.

Do NOT change Room entities or business logic solely to support the new Program builder unless a real persistence requirement demands it.

Reuse existing:

- Program entities
- WorkoutTemplate/Training Day entities
- Exercise entities
- ProgressionState
- SupersetGroup
- repositories
- `sortOrder`

The UI may maintain transient state for:

- selected Program
- selected Training Day
- selected Program/Exercises subtab
- currently open editor sheet

Historical session data must remain completely unchanged.

Reordering or renaming Programs, Days, or exercises affects future configuration only and must never rewrite history.

### Program builder acceptance criteria

The redesign is accepted when all of the following are true:

1. From the `Program` bottom-nav destination, the user can switch between `Programs` and `Exercises` without leaving that destination.
2. The user can switch between existing Programs without an `Open` button.
3. The user can switch between Training Days with one tap on a horizontal Day tab/chip.
4. Switching Day does not require pressing `Back`.
5. The main Day view shows compact exercise summaries rather than all fields expanded.
6. Tapping an exercise opens one focused editor surface.
7. Adding an exercise does not require navigating away from the selected Day.
8. Program, Day, and Exercise creation/rename no longer depend on permanent inline creation TextFields.
9. Exercise ordering uses direct drag-handle interaction and animated movement.
10. Training Day ordering is available through a dedicated reorder sheet rather than Up/Down buttons.
11. Existing progression, supersets, per-set targets, warm-ups, history snapshots, and persistence behavior continue to work.
12. No existing user data is lost as part of this UI refactor.

---

## 54.2 Collapsed completed sets

During workout logging, completed sets should collapse automatically.

Collapsed completed set display:

- set number
- logged weight
- logged reps
- set type label when relevant, such as `AMRAP`, `DROP`, or `EXTRA`
- skipped state when relevant

Collapsed completed sets should not show edit buttons.

Tapping a collapsed completed set expands it again so the user can edit:

- weight
- reps
- complete/save
- mark pending
- skip if still applicable

Superset behavior:

- in a superset, completing an earlier exercise should not collapse the whole superset round
- once the last exercise in the superset round is completed, all completed sets in that superset round collapse together
- the user can still tap any collapsed set to expand and correct it

Acceptance criteria:

- one tap still completes a normal prescribed set
- completed sets become visually quieter
- editing a completed set remains possible
- no completed workout history becomes editable

---

## 54.3 Weight adjustment controls

Numeric weight entry should not rely only on typing.

Where weight is edited:

- active workout set rows
- add/edit exercise configuration in the Program tab
- finish-workout progression review when setting a new target

Provide:

- a numeric text field
- stepper buttons or a slider for increasing/decreasing weight
- sensible increments based on the exercise increment where available

Requirements:

- kilograms only for MVP
- decimal weights remain supported
- typed values and stepper/slider values must stay in sync
- prevent negative weights
- keep the control usable with one hand during a workout
- keep slider mapping linear
- default slider upper bound to `150 kg`
- use adaptive upper-bound buckets of `100 kg`, `150 kg`, `200 kg`, `300 kg`, and `500 kg`
- expand to the next upper-bound bucket when a manually entered or stepped weight exceeds the current range
- do not dynamically rescale the slider while the user is dragging it
- keep the numeric field as the authoritative precision input

For working-set logging, prefer compact controls:

- `-`
- weight value
- `+`

A full slider is acceptable in dialogs or program-edit screens where space is less constrained.

Phase 54B implementation:

- active workout rows use a text field, slider, and `-`/`+` buttons for weight
- program exercise rows use the same weight control
- add-exercise dialogs use the same weight control
- controls step by the configured exercise increment where available

---

## 54.4 Pending sets on workout finish

If the user finishes a workout while sets are still pending:

- ask for confirmation as today
- mark those pending sets as skipped before storing history
- store them in history as skipped
- keep the workout status as `PARTIAL`
- do not count skipped sets for progression

This avoids historical details showing unfinished `pending` rows after the workout has been finalized.

---

## 54.5 Per-set targets and finish-time progression decisions

The app currently assumes every planned set in an exercise uses the same prescribed weight and reps.

Add support for planned sets having different targets.

Each planned set should be able to store its own:

- prescribed weight
- prescribed reps
- set order
- progression eligibility

This enables patterns such as:

- top set plus back-off sets
- ramping sets
- fixed reps with changing weight
- fixed weight with changing reps

Program editor UX:

- keep a simple default of same weight/reps for all working sets
- allow switching to per-set editing for an exercise
- consider a compact set-strip UI with one circle/chip per set
- each circle/chip should show the target reps and/or set number
- tapping a circle/chip opens that set's weight/reps target editor

Finish-workout progression review:

When the user changes weight or reps during a workout, the available choices should be:

1. `No change`
2. `Change only this set`
3. `Set this as new target for all sets in this exercise`

Meanings:

`No change`:

- keep the existing future target unchanged for that exercise or set

`Change only this set`:

- update the future target for the matching set order in this exercise instance
- do not update other sets in the exercise

`Set this as new target for all sets in this exercise`:

- update all planned future sets for that exercise instance to the selected weight/reps

If multiple sets in the same exercise were changed:

- show one review group per exercise
- allow decisions per changed set where needed
- provide an exercise-level shortcut to apply one changed set's target to all sets

Progression rules must remain explicit.

Do not silently infer that a changed weight or changed reps should become the new baseline.

Phase 54D implementation:

- add `WorkoutTemplateSetTarget` rows for optional per-set future targets
- keep exercise-level progression state as the default when no per-set target exists
- snapshot per-set targets into `SessionSet.prescribedWeightCentiKg` and `SessionSet.prescribedReps`
- provide a compact Program tab set-target selector where one set target can be edited at a time
- key finish-workout review decisions by changed set
- default changed sets to `No change`
- `Change only this set` stores a target override for the matching future set order
- `Set target for all sets` updates the exercise-level progression target and clears per-set overrides
- unchanged workouts continue using automatic progression

---

## 54.6 Superset and drop-set visualization

Supersets must be visually grouped more clearly than a label on each exercise.

Possible implementation:

- draw a tinted block or bordered container around all exercises in the same superset
- show a `Superset` header above the group
- show the group rest time once for the group
- preserve normal exercise cards inside the group if it helps readability

Workout execution:

- superset exercises should appear together
- the user should clearly see which exercises belong to the same superset
- the rest timer should start only after the last exercise in the superset round

Drop sets:

- indent drop sets under the preceding set
- label them `DROP`
- use subtle background or connector styling so they read as related to the preceding set
- avoid complex multi-stage drop-set programming in MVP

Phase 54C implementation:

- active workout supersets render as one bordered group with a single `Superset` header
- the group header shows exercise count and group rest
- exercise cards inside the group no longer repeat the superset label
- drop sets keep indentation and add a stronger bordered visual cue

---

## 54.7 Warm-up schemes

Add optional warm-up schemes per exercise instance.

Warm-up schemes are based on percentages of the working weight.

Default warm-up scheme:

- `10 reps at 30%`
- `3 reps at 70%`
- `3 reps at 75%`

Example for Bench Press:

- `10 x 20 kg`
- `3 x 45 kg`
- `3 x 50 kg`
- then working sets

Rounding:

- round calculated warm-up weights to practical values ending in `0` or `5`
- choose the nearest valid value unless that would create an obviously heavier-than-intended warm-up
- never exceed the working weight

Warm-up behavior:

- warm-up sets appear before working sets
- warm-up sets do not count for progression
- warm-up sets are stored in workout history
- warm-up sets can be skipped
- warm-up completion should be fast, with the same collapsed-set behavior after completion

Data model:

- add warm-up scheme configuration to the exercise instance/template side
- snapshot generated warm-up sets into the session when starting a workout
- do not reconstruct historical warm-ups from the current template

Phase 54E implementation:

- add `WorkoutTemplateWarmupSet` rows with reps, percentage, and order
- provide a Program tab control to enable the default `10 @ 30%`, `3 @ 70%`, `3 @ 75%` scheme or clear it
- generate `WARMUP` session sets before working sets when a workout starts
- keep working set order stable for per-set targets by ordering warm-ups before zero
- warm-up sets do not count for progression
- pending warm-up sets are stored as skipped when the workout is finished
- completed working sets can still finish as `COMPLETED` even if warm-ups were left pending

Phase 54F implementation:

Program builder navigation:

- implement the `Programs` / `Exercises` subtabs described in section 54.1
- replace the current Program -> Open -> Training Day -> Edit Day -> Back flow with the in-place Program builder
- use a program selector plus horizontally scrollable Day tabs/chips
- keep the currently selected Program and Day as UI state; selecting them must not mutate which Program is active unless the user explicitly chooses `Set active`
- replace always-expanded Program/Day/Exercise form fields with compact summaries plus dialogs or modal bottom sheets for focused editing

Implemented slices:

- `Programs` / `Exercises` subtabs are available inside the Program destination
- Program selection and Training Day selection happen in-place without routine `Open` / `Back` navigation
- Program, Training Day, and Exercise creation/rename use dialogs instead of permanent inline creation fields
- Training Day exercises render as compact summary cards in the main Day view
- tapping an exercise card body opens a focused editor dialog that reuses the existing configuration controls
- drag handles stay separate from card-body edit taps
- exercise and Day reorder handles now start dragging immediately from the handle instead of requiring a long press
- Training Day reordering is available through a dedicated `Reorder days` dialog rather than dragging horizontal Day chips
- supersets render with one group-level drag handle and move as a contiguous block
- individual superset member cards do not expose reorder handles in the Day builder
- active reorder targets use a lifted tonal treatment with subtle scale animation
- exercise and superset reorder gestures now keep the dragged card under the pointer, preview the reordered list while dragging, then persist the final block offset on drop
- reorder handles provide subtle haptic feedback on pickup and drop when supported
- Training Day reorder rows and Day exercise/superset items follow the pointer while dragging, preview surrounding rows/cards moving out of the way, and use keyed lazy placement animation when their order changes
- exercise and Training Day reorder lists autoscroll when the dragged card approaches the top or bottom edge of the visible list
- Training Day removal and Day-exercise removal require confirmation before deleting future program configuration
- focused exercise edit dialogs use `Done` as the close action and prompt to save or discard unsaved exercise field changes instead of showing a separate in-dialog `Save` button

Exercise drag-and-drop:

- show a dedicated drag handle on each standalone exercise card
- dragging must start directly from the handle when the user begins dragging; do NOT require a long press
- while dragging, visually lift the dragged card using elevation/tonal treatment and a subtle scale change where appropriate
- animate surrounding list items smoothly into their new positions while the dragged item moves
- support automatic scrolling when the dragged item approaches the top or bottom edge of a long list
- provide subtle haptic feedback on pickup/drop when supported without introducing a hard dependency
- persist the final order after drop using the existing repository/sortOrder logic
- keep `sortOrder` values compact after reorder
- tapping the card body must continue to open Edit and must not start dragging
- remove Up/Down ordering buttons from exercise cards/editors

Superset drag behavior:

- render a superset as a visually grouped unit
- provide a group drag handle that moves the entire superset as one unit
- do not allow ordinary drag-and-drop to accidentally add an exercise to or remove it from a superset
- if reordering members within a superset is supported, constrain those members to the same group
- preserve group contiguity and existing SupersetGroup semantics

Training Day drag-and-drop:

- Day tabs themselves do not need direct drag support in v1.1
- provide a `Reorder days` action that opens a vertical modal bottom sheet
- each Day row in that sheet has a dedicated drag handle
- use the same immediate-drag, animated-placement, autoscroll, and persistence behavior as exercise reordering
- after the sheet closes, refresh the horizontal Day tab order immediately

Animation rules:

- use motion to communicate reordering, selection, expansion, and sheet transitions
- prefer short Material-style animations rather than decorative motion
- avoid animations that delay taps or make workout/program editing feel slower
- no animation may change business state before the underlying action succeeds

Acceptance criteria:

- no reorder action requires a long press
- an exercise follows the pointer/finger while being dragged
- surrounding cards visibly animate out of the way
- long exercise lists can autoscroll during drag
- dropping an item persists the correct order after app restart
- card taps and drag-handle gestures do not conflict
- superset membership cannot be changed accidentally by reordering
- Day reorder persists and the Day tabs immediately reflect the new order

---

## 54.8 Rest timer in-app feedback

Use a persistent bottom timer for active rest periods.

For rest timing:

- keep existing notification sound/vibration behavior
- keep existing Android notification-area completion behavior
- show the active rest timer at the bottom of the Workout screen while a rest deadline exists
- keep timer controls such as `+30 sec` and `Skip` available in the bottom timer
- do not also show a bottom snackbar when rest runs out

The timer should not depend on the user being scrolled to the top of the workout.

Phase 54.8 implementation:

- the Workout screen renders the active persisted rest deadline in a Scaffold bottom bar
- the timer remains visible while scrolling the workout content
- rest completion no longer creates an in-app bottom snackbar
- existing sound/vibration/background Android notification scheduling remains unchanged

---

## 54.9 Future wearable support TODO

Wearable integration remains outside the MVP.

Keep the codebase ready for future wearable support by avoiding assumptions that workout logging can only happen from the phone UI.

Future wearable goals:

- show the current exercise
- show the current set number
- show prescribed weight
- show prescribed reps
- complete the current set with one tap
- skip the current set
- show rest countdown
- support quick adjustments where practical

Future architecture note:

- expose workout state and set-completion actions through repository/ViewModel APIs that could later be called by a Wear OS surface
- do not add Wear OS modules until explicitly requested

Wear v0.1 implementation:

- no Wear OS app module, Data Layer API, Tiles, complications, watch database, or watch-side timer were added
- active phone workouts project their current persisted state into a standard ongoing Android notification for phone and Wear OS notification bridging
- ongoing notification text is watch-friendly, such as exercise name plus `Set 2/3 • 70 kg x 10`
- `Complete set` notification actions carry explicit session/set IDs and delegate into `WorkoutSessionRepository.completeSetFromNotification`
- duplicate or stale complete actions are idempotent and cannot advance another set accidentally
- rest state uses the persisted `restEndsAt` deadline and exposes `+30 sec` and `Skip rest` actions through existing repository operations
- rest-finished alerts continue to use the high-priority rest notification channel so phone/watch alert behavior follows Android and user device settings
- active workout notifications are rebuilt from Room state on app process start and after workout/session mutations
- workout completion or discard cancels the ongoing workout notification

---

# 55. Development rules for the coding agent

Before implementing a major feature:

1. Inspect the current repository.
2. Understand the existing data model.
3. Do not duplicate existing abstractions.
4. Keep the implementation simple.
5. Do not add features not present in this specification.

After every meaningful implementation step:

- run Gradle build
- run relevant tests
- fix compilation errors
- fix test failures before proceeding

Do not leave placeholder code if the feature can reasonably be completed.

Avoid TODO implementations unless explicitly documented.

If a requirement conflicts with another requirement, prefer:

1. historical data correctness
2. progression correctness
3. fast workout logging
4. simple implementation

in that order.

---

# 56. Definition of MVP complete

The MVP is complete when I can:

1. Install the application on my Android phone.
2. Create exercises.
3. Create a training program.
4. Create multiple workouts inside the program.
5. Add exercises to those workouts.
6. Configure sets, rep range, weight, increment and rest time.
7. Use the same exercise in multiple workouts with independent progression.
8. Start the recommended workout.
9. Complete a normal prescribed set using one tap.
10. Enter fewer reps or different weight when necessary.
11. Add an extra normal set.
12. Add an AMRAP set.
13. Add a drop set.
14. Create and execute supersets.
15. Receive an alert when rest time expires.
16. Close and reopen the app during a workout without losing progress.
17. Finish the workout.
18. Have progression automatically updated correctly.
19. Open History.
20. See exactly what I performed on any previous workout date.
21. Change the current program later without corrupting old history.

Do not add additional product scope until all of the above works reliably.
