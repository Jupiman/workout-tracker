# Workout Companion — Android MVP Implementation Specification

## 1. Goal

Build a simple native Android workout companion focused on strength/hypertrophy training.

This is primarily a single-user personal application. The main priorities are:

1. Extremely fast workout logging.
2. Automatic double-progression tracking.
3. Independent progression for the same exercise used in different workouts.
4. Reliable workout history.
5. Simple workout-program management.
6. Rest timer notifications.
7. Supersets.
8. Ad-hoc normal sets, AMRAP sets and drop sets.
9. A lightweight Wear OS companion that mirrors the phone workout and supports fast set completion.

Do NOT add unnecessary fitness functionality.

This application is not intended to be a comprehensive fitness platform.

Current product name: `Workout Companion`.

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
- standalone wearable workout tracking
- watch-side program editing, history editing, or independent progression logic
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

Use a clean Material 3 interface with a restrained, dark-first identity.

The app should feel focused and calm rather than decorative.

Prefer:

- a dark-first palette with a lavender/purple accent
- simple cards and surfaces with consistent radius and spacing
- clear typography with large values only where they matter
- large numeric values
- strong distinction between current/actionable, pending, completed, skipped, rest, ready, and disabled states
- restrained use of animations

Functionality and speed are more important than visual decoration.

Support system light/dark mode.

Workout screen visual priorities:

- make the current actionable set obvious
- keep completed sets visually quieter than pending/current sets
- keep skipped sets distinct without making the whole screen feel like an error state
- keep the rest timer visible at the bottom while scrolling
- use subtle motion only to clarify state changes, expansion, reordering, or completion

Program, Exercise editor, History, and Wear OS should use the same visual language, spacing, state colors, and surface hierarchy.

Do not add gradients, glass effects, noise overlays, dashboards, or branding-heavy screens.

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

The add-exercise flow should present the primary fields in this order:

- exercise name
- sets
- target reps
- rep minimum
- rep maximum
- remaining details such as weight, increment, rest, and setup/warm-up options

Do not navigate to a separate full-screen Exercise Library page merely to add an exercise.

### Exercise editor

Tapping an exercise card opens a Material 3 `ModalBottomSheet` or equivalent focused editor.

Only one exercise should be edited at a time.

The editor contains the existing configuration controls, including where applicable:

- exercise name
- sets
- target reps
- rep minimum
- rep maximum
- current/progression weight
- increment
- rest
- per-set target configuration
- warm-up configuration
- superset membership/actions

The Program exercise editor should use the same primary field order as the add-exercise flow: name, sets, target reps, rep minimum, rep maximum, then remaining details.

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
- keep the bottom timer display-only during workout logging
- display overtime as a negative timer once the rest deadline has passed
- do not also show a bottom snackbar when rest runs out

The timer should not depend on the user being scrolled to the top of the workout.

Phase 54.8 implementation:

- the Workout screen renders the active persisted rest deadline in a Scaffold bottom bar
- the timer remains visible while scrolling the workout content
- the timer counts into negative time after rest expires
- the bottom timer no longer exposes `+30 sec` or `Skip`
- rest completion no longer creates an in-app bottom snackbar
- existing sound/vibration/background Android notification scheduling remains unchanged

---

## 54.9 Wear OS companion

Wear OS support is implemented as a companion surface, not as a standalone tracker.

The phone remains authoritative for:

- workout/session persistence
- progression logic
- rest deadline persistence
- program and exercise editing
- history

Wear OS goals:

- show the current exercise
- show the current set label
- show prescribed weight and reps
- complete the current set with one tap
- show rest countdown and ready state
- show disconnected/pending states clearly
- avoid watch-side database or progression calculations

Wear implementation:

- the repository contains a `:wear` application module and a shared `:wear-protocol` module
- the phone projects active workout state through the Wear Data Layer
- the watch displays active, no-active, complete, unavailable, disconnected, pending, rest, and ready states
- the watch can complete the current set through a phone-authoritative command
- the watch does not maintain a workout database or independent timer state
- phone notifications and rest alerts remain available alongside the companion app

---

## 54.10 Last Time workout context

The Workout screen should show compact read-only context from the previous time the same exercise instance was logged.

Matching rules:

- match by `sourceWorkoutTemplateExerciseId`
- do not match only by exercise name
- do not mix the same exercise used in different training days or different template instances
- use completed and partial historical sessions
- use the latest matching historical snapshot
- do not write this context back into active workout state
- do not alter progression logic, history snapshots, or Room schema

UI rules:

- show the context inside the exercise card
- label it as `Last time`
- show the historical workout date/name and the logged non-warm-up set results
- keep it compact and secondary to the current actionable set
- hide it when no previous matching snapshot exists

Phase 54.10 implementation:

- `HomeViewModel` derives last-time context by combining the active workout with read-only history snapshots
- the Workout screen renders the context under each exercise prescription
- unit tests cover same-instance matching, different-instance isolation, and latest-snapshot selection

---

## 54.11 Local backup, restore, and export

The app should support manual local backups without adding accounts, cloud sync, or a backend.

Backup/export rules:

- export a user-selected local file through Android's document picker
- use a human-readable JSON backup format
- include programs, exercises, templates, progression state, active workout state, completed history, supersets, warm-ups, per-set targets, and session sets
- include metadata for app name, backup format version, schema version, and export timestamp
- do not expose backup files automatically to network services
- do not add cloud storage, authentication, or scheduled sync

Restore rules:

- restore from a user-selected file through Android's document picker
- require an explicit confirmation before choosing the restore file
- replace the current local app data with the backup content
- restore only backups with the matching app/schema version
- resync rest timer alarms and workout notifications after restore
- do not change progression rules or apply progression during restore
- do not run a Room migration solely for this feature

UI rules:

- keep controls secondary and out of the workout logging path
- History may contain a compact Backup card until a dedicated settings/menu surface exists
- use clear labels: `Export` and `Restore`

Phase 54.11 implementation:

- `DataBackupRepository` exports/imports the current Room tables as a versioned JSON document
- `WorkoutTrackerApp` owns the Android create/open document launchers
- the History screen exposes a compact Backup card and restore confirmation dialog

---

## 54.12 History calendar navigation

The History screen should not become an unbounded chronological scroll as workout history grows.

Calendar rules:

- show a month calendar before the workout list
- mark days that contain logged workouts with an explicit green square or circle indicator, not only a number
- show the workout count on days with one or more workouts
- selecting a date filters the visible history list to that date
- if two or more workouts are logged on the same date, show all of them under that selected date
- use the user's local date based on `completedAt` when available, otherwise `startedAt`
- keep historical session details read-only
- do not add graphs, analytics, or volume dashboards

Phase 54.12 implementation:

- the History list defaults to the latest logged workout date
- month navigation uses simple previous/next controls
- the selected-day summary shows `No workouts`, `1 workout`, or `N workouts`
- the existing session detail view remains unchanged

---

## 54.13 Exercise setup notes

Exercise setup notes should help with repeatable setup details such as seat height, rack pin, grip marker, cable attachment, or machine settings.

Data rules:

- notes belong to `WorkoutTemplateExercise`, not the global Exercise library
- the same Exercise used in different training days may have different setup notes
- starting a workout snapshots the note into `SessionExercise`
- completed history keeps the snapshotted note even if the template note changes later
- setup notes do not affect progression, rest timers, set targets, or workout recommendation

UI rules:

- edit the setup note inside the exercise configuration editor
- show a compact setup indicator in Program exercise cards when a note exists
- show the note inside active workout exercise cards, near the exercise prescription
- show the snapshotted note in History detail
- hide empty notes

Phase 54.13 implementation:

- Room schema version 5 adds `setupNote` to `workout_template_exercises`
- Room schema version 5 adds `setupNoteSnapshot` to `session_exercises`
- `ProgramViewModel` and `ProgramRepository` save notes through the existing exercise editor flow
- `WorkoutSessionRepository.startWorkout` snapshots setup notes into active session history

---

## 54.14 Wear OS polish

Wear OS should remain a fast companion surface for active workouts, with the phone still authoritative for all workout state.

Wear polish rules:

- show phone connection state on every watch screen
- show pending set-completion commands clearly after the user taps complete
- show command rejection or send failure messages without adding watch-side retry state
- make rest and overtime states glanceable during an active workout
- keep the active Wear screen awake while a workout is active
- keep the active workout screen focused on exercise name, target weight/reps, set label, and one-tap completion
- do not add watch-side persistence, program editing, history, or progression logic
- do not change the shared protocol unless the phone needs to send new workout data

Phase 54.14 implementation:

- the active Wear screen uses compact status chips for phone connection, pending sync, and superset position
- the prescribed target and set label are grouped in a single high-contrast panel
- rest countdown and overtime states use pill styling for quicker scanning
- long exercise names are truncated before they can push the complete button off-screen
- the active Wear screen is scrollable on small round displays so rest state and complete action do not overlap
- the active workout screen requests keep-screen-on while visible
- rest completion triggers a clear one-time Wear haptic alert
- pending complete-set commands show a disabled `SENT` button state with progress feedback
- no-active and complete screens show phone connection status and transient command messages
- no database, protocol, or phone-authoritative workflow changes are introduced

---

## 54.15 Replace for today

Active workouts may need a session-only exercise substitution when equipment is unavailable.

Replacement rules:

- the replacement applies only to the current `WorkoutSession`
- the source `WorkoutTemplateExercise` and future workouts are not modified
- the active `SessionExercise` keeps its current set structure, prescribed weights, reps, rest, and superset placement for today
- the active `SessionExercise` changes its exercise name snapshot to the selected Exercise Library item
- the active `SessionExercise` clears `sourceWorkoutTemplateExerciseId`
- all existing sets for the replaced session exercise stop counting for progression
- replacing an exercise clears the setup note snapshot because the note belonged to the original exercise instance
- completed history shows the replacement exercise name performed that day
- last-time lookup and progression do not treat the replacement as the original exercise instance

Phase 54.15 implementation:

- active workout exercise cards expose `Replace for today`
- the replacement picker lists active Exercise Library entries and explains the session-only behavior
- `WorkoutSessionRepository.replaceExerciseForToday` updates only the active session snapshot
- no database migration is required
- Wear and phone notification state refresh from the updated active session

---

## 54.16 Duplicate training day and exercise configuration

Program editing should support duplicating existing configuration without sharing mutable progression state.

Duplicate Training Day rules:

- create a new `WorkoutTemplate` in the same program
- default the copied name to `<source name> copy`
- append the copy at the end of the program's training days
- copy all exercises, ordering, set configuration, rest, setup notes, warm-up schemes, per-set targets, and superset grouping
- create new `WorkoutTemplateExercise` rows
- create new `ProgressionState` rows initialized from the source values
- create new `SupersetGroup` rows for copied supersets
- do not modify workout history

Duplicate Exercise rules:

- create a new `WorkoutTemplateExercise` in the same training day
- append the copy at the end of the day
- copy exercise library reference, set configuration, rest, setup note, warm-up scheme, per-set targets, and current progression values
- create a new independent `ProgressionState`
- do not copy the source exercise into the original superset group; the duplicate starts standalone
- do not modify workout history

Phase 54.16 implementation:

- Program training-day rows and the edit-day header expose `Duplicate`
- Program exercise cards and exercise editor controls expose `Duplicate`
- `ProgramRepository.duplicateWorkoutTemplate` performs a transactional deep copy
- `ProgramRepository.duplicateTemplateExercise` performs a transactional standalone exercise copy
- no database migration is required

---

## 54.17 Exercise rename access

Exercise names are global Exercise Library names, but users must be able to rename them from the place where they notice the problem.

Rename rules:

- the Exercise Library subtab exposes rename for reusable exercises
- the Program exercise editor exposes exercise name as an editable field alongside sets, reps, weight, rest, and setup notes
- editing the Program exercise name re-links only that `WorkoutTemplateExercise` to the named Exercise Library record
- if the typed exercise name does not exist, the save creates a new Exercise Library record and links only the edited row to it
- if the typed exercise name already exists, the save links only the edited row to that existing exercise
- duplicated exercises initially share the source exercise reference, but editing the duplicate name must not rename the source exercise
- logged workout history keeps its existing exercise name snapshots
- blank-name validation is enforced before saving a Program exercise name
- Exercise Library rename still uses `ExerciseRepository.renameExercise` for duplicate-name and blank-name validation

Superset action rules:

- exercises that are not in a superset show `Superset previous`
- exercises already in a superset show `Remove superset`
- the two actions should not be shown together for the same exercise

---

## 54.18 Seeded Exercise Library

Fresh installs should start with a small useful Exercise Library without turning the app into a large exercise database.

Seed rules:

- seed only when Room creates a genuinely new application database
- use Room's database creation callback rather than checking whether the Exercise table is empty at startup
- do not seed during normal app startup, restore, migration, or after the user intentionally empties the library
- do not add Exercise metadata such as category, muscle group, equipment, source, or default flags
- seeded exercises are ordinary `Exercise` rows and can be used, renamed, archived, and selected exactly like user-created exercises
- no Room schema change or migration is required

The default Exercise Library contains these canonical names:

- Bench Press
- Incline Bench Press
- Dumbbell Bench Press
- Incline Dumbbell Press
- Machine Chest Press
- Pec Deck
- Cable Fly
- Push-Up
- Lat Pulldown
- Pull-Up
- Chin-Up
- Seated Cable Row
- Machine Row
- Barbell Row
- Dumbbell Row
- Straight-Arm Pulldown
- Face Pull
- Overhead Press
- Dumbbell Shoulder Press
- Machine Shoulder Press
- Dumbbell Lateral Raise
- Cable Lateral Raise
- Rear Delt Fly
- Barbell Curl
- Dumbbell Curl
- Hammer Curl
- Cable Curl
- Preacher Curl
- Triceps Pushdown
- Overhead Triceps Extension
- Skull Crusher
- Dip
- Close-Grip Bench Press
- Squat
- Front Squat
- Hack Squat
- Leg Press
- Leg Extension
- Leg Curl
- Romanian Deadlift
- Deadlift
- Hip Thrust
- Bulgarian Split Squat
- Walking Lunge
- Standing Calf Raise
- Seated Calf Raise
- Cable Crunch
- Hanging Leg Raise
- Crunch
- Plank

Exercise search rules:

- the Exercise Library screen exposes simple local search
- the Add Exercise flow uses one `Exercise name` field for both custom names and existing-exercise search
- typing in the Add Exercise field filters existing exercises immediately
- matching existing exercises appear as an overlay/autocomplete list and must not push the remaining configuration fields down
- selecting an existing exercise fills the same field
- editing the filled field after selection treats the typed value as a custom exercise name
- search uses case-insensitive substring matching
- search works the same for seeded and custom exercises
- no fuzzy search, tags, aliases, categories, ranking, FTS, or external database is added

Backup/restore rules:

- restore replaces the Exercise Library with the backup contents
- default exercises must not be appended after restore
- restart after restore must not trigger default seeding

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

---

# 57. Post-MVP Roadmap

Workout Companion is past the original MVP stage. Preserve all completed requirements and historical sections above. This roadmap is deliberately focused rather than a feature-heavy fitness platform.

Priorities: workout logging speed, correctness of stored data, progression correctness, real gym usability, simple architecture, visual quality, then feature richness.

Delivery gate: this entire roadmap must exist in SPEC.md before any Phase 1 production changes. Report the specification addition, inspect the relevant implementation, briefly state the approach, implement Phase 1 only, add/update tests, run relevant Gradle tests/builds, fix regressions, summarize, and STOP. Phase 2 requires explicit instruction.

For every later phase: reread its SPEC section, inspect existing implementation, produce a short implementation plan, implement only that phase, add/update tests, run relevant Gradle builds/tests, fix regressions, summarize, and STOP for approval. Never implement multiple phases at once.
## 57.1 Active workout ergonomics

Add four session-level features:

1. Skip exercise
2. Add exercise for today
3. Do later
4. Undo recently completed set

The principle for this phase is:

Actions performed during an active workout should not modify the permanent Program unless explicitly requested.

------------------------------------------
1A — SKIP EXERCISE
------------------------------------------

During an ACTIVE workout, allow the user to skip the remaining work for an exercise.

Expose a compact action such as:

Exercise overflow menu
→ Skip exercise

Behavior:

- already COMPLETED sets remain completed
- already SKIPPED sets remain skipped
- all remaining PENDING sets for that SessionExercise become SKIPPED
- warm-up and working sets that are still pending should be handled consistently
- the Program / WorkoutTemplateExercise is not modified
- future workouts are unchanged
- history records the skipped sets
- progression must follow the existing rule:
  a skipped progression-relevant planned set prevents automatic progression for that exercise

Require confirmation if accidental skipping would be costly.

Example:

Bench Press

Set 1 COMPLETED
Set 2 COMPLETED
Set 3 PENDING

Skip exercise

Result:

Set 1 COMPLETED
Set 2 COMPLETED
Set 3 SKIPPED

Do not create fake completed sets.

------------------------------------------
1B — ADD EXERCISE FOR TODAY
------------------------------------------

Allow adding an exercise only to the current WorkoutSession.

This is the opposite of Replace for today.

Possible entry points:

- workout-level "Add exercise for today"
- exercise overflow → "Add exercise after this"

Use whichever integrates cleanly with the current UI.

Flow:

Add exercise for today
→ choose/search Exercise Library
→ configure today's sets / reps / weight / rest as appropriate
→ add to active workout

Rules:

- do NOT modify WorkoutTemplate
- do NOT modify future workouts
- create a session-only SessionExercise
- it must not have a source WorkoutTemplateExercise progression relationship
- session-only sets must NOT affect automatic progression
- history must store the exercise exactly as performed
- Wear OS must receive the new actionable exercise when appropriate
- app restart during the workout must preserve it

Prefer reusing existing Program/session editor controls rather than creating an unrelated editor implementation.

If a new exercise has no permanent progression track, all of its sets should be considered session-only for progression purposes.

------------------------------------------
1C — DO LATER
------------------------------------------

Allow an exercise to be postponed inside the ACTIVE workout.

Use case:

The machine is occupied.

The user does not want to:

- skip the exercise
- replace it
- modify the permanent program

Action:

Do later

Behavior:

- move the exercise later in the active workout execution order
- preferably to the end of the remaining actionable exercises
- do not alter permanent WorkoutTemplate sortOrder
- future workouts keep their normal order
- active-session order must survive application restart
- phone and Wear must agree on the same next actionable set

Do not silently change superset membership.

If the exercise belongs to a superset:

do not invent complicated partial-superset behavior.

Either:

- move the entire superset group together

or:

- disable Do later for an individual superset member and explain why

Choose the simplest behavior consistent with the current architecture.

Persist session-level ordering if required.

A Room migration is acceptable if there is a genuine persistence need.

Do not abuse transient UI state for this.

------------------------------------------
1D — UNDO RECENTLY COMPLETED SET
------------------------------------------

Make accidental set completion easy to reverse.

After completing a set on the phone, provide a short-lived Undo affordance.

Example:

Set 2 completed                         UNDO

This may use an appropriate snackbar or equivalent lightweight Material interaction.

Behavior:

- restore the just-completed set to its previous editable/pending state
- do not undo an arbitrary old set through this action
- only undo the most recently completed set associated with that UI action
- do not allow Undo to accidentally revert a different subsequent set

If completing that set started the current rest timer:

Undo should cancel that rest timer if the timer belongs to the completion being undone.

If another action has since superseded the timer/state, do not destroy unrelated state.

Existing manual editing of completed sets should remain available.

Wear:

Do not add complicated watch-side Undo in this phase.

A set completed from Wear may still surface the phone-side Undo affordance if this integrates cleanly with the existing state flow.

Phone remains authoritative.

------------------------------------------
PHASE 1 UI
------------------------------------------

Exercise secondary actions should become coherent.

Conceptually:

[exercise] ⋮

Replace for today
Do later
Skip exercise
Add exercise after this

Do not expose actions that are invalid for the current exercise/state.

Do not clutter every card with permanent text buttons.

Use overflow/menu actions where appropriate.

Acceptance criteria:

- Skip exercise affects current session only
- Add exercise for today affects current session only
- Do later affects current session order only
- Undo safely restores the latest accidental completion
- active workout survives process restart correctly
- Wear follows updated actionable state
- progression remains correct
- permanent Program remains unchanged
- History accurately records what happened

STOP after Phase 1.

### Phase 1 implementation decisions

- Exercise overflow contains Replace for today, Do later (labelled Do superset later for grouped exercises), and Skip exercise. Skip confirms the number of remaining sets, including warm-ups.
- A workout-level Add exercise for today dialog reuses the Program add-exercise search and numeric controls. It selects an existing library exercise and configures today's sets, reps, weight, and rest; no future progression controls are shown.
- Session-only exercises append to the active workout, have no source template-exercise ID, and have no progression-relevant sets. Their planned working sets still count toward workout completion and remain in History.
- Do later moves the entire superset when applicable. It writes the existing session `sortOrderSnapshot` fields transactionally, preserving membership and member order. No schema migration or backup version change is needed.
- Undo uses a short-lived phone snackbar and an identity-bound completion receipt. It restores pending/editable state with the entered values, clears the completion timestamp, and only cancels rest owned by that completion. Later completions (including Wear), manual set edits, and finalized sessions cannot be reverted using a stale receipt. Extended or unrelated rest is preserved.
- The phone current-set indicator uses the same actionable-set projection as notification/Wear command validation. Skipping a superset member allows the remaining round to start rest when its actionable work is complete.
- Room instrumentation covers session-only changes, immutable history, progression isolation, closing/reopening the database, superset order/rest, stale Wear commands, Undo ownership, and input validation. Phase 2 remains unimplemented.

### Wear finish action

- When no actionable sets remain, keep the Wear completion screen visible until the phone reports the session finalized; do not dismiss it on a timer.
- Offer Finish workout, disabled while disconnected or while a command is pending. Send a session-specific command to the authoritative phone and show pending/error feedback, with a timeout allowing retry.
- The phone validates the session and completion state inside the existing finish transaction. Stale/duplicate commands must not finish another session or apply progression twice.
- Workouts needing partial-finish confirmation or changed-target progression review remain on the phone; return a clear instruction to finish there. Normal completed workouts finish directly using the existing progression/history logic.
- This focused companion action does not implement the Phase 2 completion summary.

## 57.2 Workout completion summary

After finishing a workout, do not immediately discard all context and return straight to the normal home state.

Show a concise Workout Complete summary.

This is NOT an analytics dashboard.

The goal is to answer:

"What did I just do and what changed for next time?"

Example:

WORKOUT COMPLETE

Chest + Shoulders
57 min
18 / 19 working sets

Progression
────────────────

Bench Press
70 kg × 10
→ 70 kg × 11

Incline Dumbbell Press
30 kg × 12
→ 32.5 kg × 8

Cable Lateral Raise
No change

1 skipped set

[ Done ]

Show where relevant:

- workout / Training Day name
- duration
- completed working set count
- skipped set count
- exercise progression result
- old target → new target
- unchanged progression when useful

Do not add:

- PR scoring
- 1RM
- calorie estimates
- muscle-volume scores
- achievements
- workout rating
- social sharing

Automatic progression and explicit per-set target decisions must remain authoritative.

The summary must report what actually happened after finish logic; it must not independently recalculate progression using separate rules.

Prefer having finish logic return or expose a clear summary result instead of duplicating progression logic in the UI.

Partial workout behavior must be represented correctly.

A session-only / Replace-for-today exercise should not claim permanent progression.

Acceptance:

- summary matches persisted progression state
- completed and skipped counts are correct
- session-only exercises do not report fake progression
- leaving the summary does not mutate workout data
- History remains unchanged/read-only

STOP after Phase 2.

### Phase 2 implementation decisions

- Finish returns a completion summary assembled inside its existing Room transaction. Effective future per-set targets are read before and after the authoritative finish logic; the summary never runs its own progression algorithm.
- The phone shows the summary until Done, including after a Wear finish. It reports the snapshotted Training Day name, elapsed minutes, completed/total WORKING sets (including today's added exercises), partial status, and all skipped sets including warm-ups. EXTRA, AMRAP, DROP, and WARMUP sets are excluded from the working-set fraction.
- Each exercise reports actual future target changes or No change. Per-set overrides are displayed individually when needed. Exercises without an available permanent track do not claim progression.
- The repository retains this read-only result in process memory for navigation/configuration changes. Done only dismisses presentation state. A process restart returns to normal persisted History/home behavior; it does not reconstruct past progression from the current Program. No Room migration or backup format change is needed.
- Regression coverage checks automatic weight progression, partial counts, explicit per-set and whole-exercise decisions, No change, Wear completion, session-only exercises, dismissal immutability, and independence from later Program edits.
- Phase 3 remains unimplemented.

## 57.3 Settings

Add a small Settings surface.

Do NOT add a fourth bottom-navigation destination.

Access Settings from a small top-level menu / icon.

Move configuration that does not belong in normal workout workflow into Settings.

Initial Settings scope:

DATA

- Export backup
- Restore backup

ABOUT

- app name
- version if easily available

Move Backup / Restore out of History once Settings exists.

Do not duplicate them in both places permanently.

Preserve all existing backup/restore behavior and compatibility.

Design Settings so future preferences can be added cleanly.

Future, NOT IMPLEMENTED NOW:

- kg / lb unit preference
- notification preferences
- optional vibration preferences

Do NOT add the unit toggle yet.

Do not add placeholder or disabled UI merely to advertise future functionality.

Document future unit support in SPEC only.

Acceptance:

- Backup and Restore work exactly as before
- History is simplified
- Settings does not become another primary navigation destination
- no data migration solely for moving UI

STOP after Phase 3.

### Phase 3 implementation decisions

- Settings opens from the top app menu on every primary destination, including when no Program exists. It is a separate back-stack page and adds no bottom-navigation item. Back returns to the previous screen.
- Data contains Export backup and Restore backup. The restore confirmation text, document picker contracts, backup repository, supported format, error/success feedback, and post-restore timer/notification synchronization remain unchanged.
- History no longer contains backup controls or restore confirmation.
- About shows Workout Companion and the installed package version when available. No future preference placeholders, unit toggle, schema migration, or backup version change is introduced.
- Backup regression coverage verifies that export/restore preserves completed History, an active workout, progression, templates, and persisted rest scheduling. Phase 4 remains unimplemented.

## 57.4 Exercise tracking modes

The current kg × reps model is not ideal for every exercise.

Examples:

Bench Press:
70 kg × 10

Pull-Up:
8 reps

Plank:
60 sec

Add support for three exercise tracking modes:

WEIGHT_REPS
REPS
DURATION

Do not add more modes in this phase.

IMPORTANT:

Inspect the current data model before deciding where tracking mode belongs.

Prefer a model that allows the same reusable Exercise name to be configured differently in different Training Day instances when that is useful.

For example:

Pull-Up may be bodyweight/reps-only in one context but weighted in another.

Therefore strongly consider storing tracking mode on WorkoutTemplateExercise rather than making the global Exercise Library definition unnecessarily restrictive.

Snapshot the relevant mode into SessionExercise so History remains immutable.

------------------------------------------
WEIGHT_REPS
------------------------------------------

Existing behavior.

Example:

Bench Press
70 kg × 10

Preserve current double-progression behavior completely.

No regression is acceptable.

------------------------------------------
REPS
------------------------------------------

Example:

Pull-Up
10 reps

Do not show meaningless:

0 kg × 10

Weight input is hidden/not required.

Sets still contain actual reps.

Progression:

keep it deliberately simple.

If the current architecture can safely use the existing rep-range target logic:

- successful planned sets may advance target reps through the configured rep range
- once repMax is reached, do NOT invent a weight increase
- target may remain at repMax until the user changes configuration

Do not invent advanced bodyweight progression.

------------------------------------------
DURATION
------------------------------------------

Example:

Plank
60 sec

Use seconds as the stored workout duration target.

UI may display:

45 sec
1:00
1:30

using sensible formatting.

The user should be able to enter/edit actual duration.

Do not implement automatic rep counting, motion detection, or timer-based exercise detection.

Keep automatic progression for duration conservative.

It is acceptable for DURATION to use a manually configured future target rather than inventing a new automatic progression algorithm.

If a clean duration increment model naturally fits the existing progression architecture, explain it before implementing it.

Do not shoehorn seconds into a field named reps just to avoid a schema change.

Use explicit semantics.

------------------------------------------
TRACKING MODE UX
------------------------------------------

Program exercise configuration should allow selecting:

Tracking:

Weight + reps
Reps
Duration

Only show controls relevant to that mode.

Workout and History must also display the mode correctly.

Wear OS must display the correct target format.

Examples:

Bench Press
70 kg × 10

Pull-Up
10 reps

Plank
60 sec

Backup/restore and Program export/import introduced later must preserve tracking mode.

Existing databases must migrate safely with existing exercises defaulting to WEIGHT_REPS.

Acceptance:

- existing workouts behave identically after migration
- REPS does not display fake 0 kg
- DURATION does not pretend seconds are reps
- History snapshots preserve historical tracking semantics
- Wear renders all supported modes
- progression for WEIGHT_REPS is unchanged

STOP after Phase 4.

### Phase 4 implementation decisions

- Added `TrackingMode` to each WorkoutTemplateExercise and snapshotted it into SessionExercise. Existing rows migrate to `WEIGHT_REPS`; the Room schema is version 7.
- `WEIGHT_REPS` keeps the existing double-progression algorithm. `REPS` stores no logged weight, advances reps through the configured range, then holds at repMax. `DURATION` stores explicit target and actual seconds without using reps as a substitute. At the user's request, duration has a configurable nonnegative increment in seconds: all planned working sets must complete at or above their prescribed seconds to advance the next target once at finish. Zero (the default) keeps the target fixed. Skips/misses hold the target; session-only exercises do not update a Program. A target edited in the Program during the session is preserved.
- Program Add and Edit, and Add exercise for today, expose a Tracking picker. Duration hides rep min/max and weight, uses Target seconds and Increment seconds (Program only); reps-only hides weight and weight increment. Warm-ups and drop sets require weight + reps; reps supports per-set rep targets; duration supports extra timed sets. Switching a template's mode clears incompatible warm-ups and per-set overrides while preserving active and historical snapshots.
- Active set logging, collapsed sets, Last Time, History, completion summaries, notifications, and Wear render the snapshot's units. Phone logging accepts actual seconds; notification/Wear completion uses prescribed seconds. Wear state format 2 includes tracking mode and seconds; its reader also accepts legacy state format 1. Command formats are unchanged.
- Database migrations 5→6 (tracking) and 6→7 (duration increments) preserve existing installations. Backup format version 3/schema 7 preserves all new fields. Format 1/schema 5 restores with WEIGHT_REPS defaults; format 2/schema 6 retains its tracking data and defaults duration increment to zero.
- No additional tracking modes, motion detection, automatic rep counting, or timer-based duration detection were added. Phase 5 remains unimplemented.

### 57.4.1 Duration set countdown

A pending actionable `DURATION` set is executable with a persisted countdown on phone and Wear OS. `WEIGHT_REPS` and `REPS` behavior remains unchanged.

Before timing, the primary duration action is `Start set`; manual duration entry remains available as a secondary fallback. Starting persists ownership of the exact active WorkoutSession and SessionSet plus absolute start and end timestamps. Only one duration timer may exist in the active workout. The default preparation period is three seconds and is represented by a future start timestamp; preparation time is excluded from the performed duration.

During preparation, phone and Wear show `3`, `2`, `1`, then `GO`, with `Cancel` as the action. Cancel clears the persisted timer and deadline alarm, leaves the set `PENDING`, writes no actual duration, and starts no rest. At `GO`, Wear emits one short haptic where supported.

During execution, both surfaces derive the live remaining time from the persisted end timestamp. They do not persist a decrementing counter and the phone does not publish per-second Wear updates. Phone and Wear show a large countdown, target, set label, and `Stop set`. The watch may continue rendering and alerting from the last synchronized timestamps while temporarily disconnected, but phone state remains authoritative and communication-dependent actions are disabled.

At the deadline, the phone alerts the user and automatically completes the owned set through the normal set-completion path with `actualDurationSeconds` equal to the prescribed target. This preserves current actionable-set sequencing, progression evaluation, notifications, Wear projection, and normal/superset rest rules. An expired persisted timer is reconciled exactly once on the next relevant repository or application synchronization if Android delays the deadline callback.

Stopping after the timed portion begins calculates whole elapsed seconds from the persisted timestamps, completes the exact owned set, and clears the timer. Early elapsed time is floored conservatively and capped below the target until the deadline is actually reached, so a stop at 59.1 seconds for a 60-second target cannot count as 60. The existing completed-set correction and phone Undo flows remain available. Manual completion, early stop, and automatic completion all use the same `SessionSet.actualDurationSeconds` persistence semantics.

Starting a duration set clears the previous rest deadline and alarm. Completion reuses the existing rest decision, including waiting until the final member of a superset round. Starting another timer or mutating its exercise/order while one is preparing or running is rejected. Finishing a workout requires the timer to be stopped or cancelled first. Discarding a workout clears its timer and alarm without inventing a result.

The duration timer survives recomposition, screen-off, process death, app reopening, Wear disconnection, and phone/watch resynchronization. Important ownership and timestamps are stored in Room rather than ViewModel/UI state. Migration defaults existing sessions to no active duration timer and does not rewrite historical sets. Full backup/restore preserves a valid active timer, reschedules its deadline, and immediately reconciles an already-expired restored timer. Older supported backups remain importable.

Wear state includes the authoritative duration start/end timestamps and increments its state format while retaining v1/v2 decoding. Start, stop, and cancel commands identify the session, exact set, command, and observed state as appropriate. The phone rejects stale ownership and non-current sets. Duplicate start does not reset a running timer; duplicate stop cannot complete the next set. At zero, Wear emits one clear completion haptic and converges to the next phone-projected state.

The implementation adds no pause/resume, time adjustment, configurable preparation setting, sensing, voice control, multiple timers, interval mode, custom sounds, watch database, or Phase 5 progress work.

## 57.5 Exercise progress and graph

Add a useful progress view for a specific exercise progression track.

This may contain a graph.

Do NOT create a general analytics dashboard.

The progress view should answer:

"How has this exercise been progressing?"

Matching must respect WorkoutTemplateExercise identity.

Do not mix:

Day A Bench Press

with:

Day C Bench Press

just because both reference "Bench Press".

If accessed from the global Exercise Library and one Exercise exists in multiple Training Day instances:

allow the user to select the relevant progression track rather than silently combining them.

Example:

Bench Press

Chest + Shoulders
Current target:
72.5 kg × 9
3 sets · 8–12

Progress

[ line chart ]

Recent sessions

14 Sep
72.5 × 8
72.5 × 8
72.5 × 8

08 Sep
70 × 12
70 × 12
70 × 12

etc.

------------------------------------------
GRAPH
------------------------------------------

Use a simple line chart.

Avoid adding a large charting dependency unless there is a compelling reason.

Prefer a lightweight Compose implementation if practical.

For WEIGHT_REPS:

primary graph:
working weight over time

A sensible historical metric such as top completed progression-relevant working weight per session is acceptable.

Exclude:

- warm-ups
- drop sets
- AMRAP
- unrelated EXTRA sets

Be explicit in the UI about what is graphed if ambiguity exists.

For REPS:

graph an understandable rep-performance metric such as best completed planned-set reps.

For DURATION:

graph completed duration.

Keep the detailed historical sets below the chart so the graph is not the only source of truth.

No:

- estimated 1RM
- strength score
- volume score
- trend prediction
- AI analysis
- muscle analytics

Support an empty state for exercises with insufficient history.

Acceptance:

- correct template-instance isolation
- graph derives only from immutable History snapshots
- editing the current Program does not rewrite graph history
- recent-session list agrees with History
- visually useful in light/dark themes

STOP after Phase 5.

### Phase 5 implementation decisions

Progress is opened from the global Exercise Library and remains scoped to one exact `WorkoutTemplateExercise` identity. When a library exercise belongs to multiple training days, the progress screen presents each Program and Training Day as a separate selectable track. The current target comes from that track's live progression state.

The chart is implemented with Compose drawing primitives and adds no chart dependency. It shows at most the 12 most recent qualifying points for the track's current tracking mode: top completed progression-eligible planned working-set weight for WEIGHT_REPS, best completed progression-eligible planned working-set reps for REPS, and best completed planned working-set duration for DURATION. Warm-up, extra, AMRAP, drop, skipped, pending, and other non-qualifying sets do not affect the graph.

Progress history is derived from completed and partial session snapshots by exact `sourceWorkoutTemplateExerciseId`. Each recent-session card retains its snapshot tracking mode and shows the same historical sets stored for History, so changing the current Program configuration does not alter or hide older session detail. Historical points from a previous tracking mode remain in the detail list but are not mixed into a graph with a different unit. No database schema or backup-format change is required.

## 57.6 Visual polish

Perform a screen-by-screen visual quality pass.

This phase has broad permission to improve visual presentation.

It does NOT have permission to redesign core workflows or business logic.

Review:

- Workout
- Program
- Exercise Library
- dialogs
- bottom sheets
- History
- Progress
- Settings
- Workout Complete summary
- empty states
- error states
- Wear OS

Improve where appropriate:

- typography hierarchy
- spacing
- alignment
- component density
- card hierarchy
- surface hierarchy
- iconography
- app bars
- dialogs
- bottom sheets
- touch target consistency
- active/current states
- completed/skipped states
- superset presentation
- visual grouping
- text truncation
- long-name handling
- empty states
- confirmation dialogs
- animation
- haptic feedback
- scrolling behavior
- edge-to-edge/system bars
- dark/light theme consistency

Continue using the Workout Companion identity:

- dark-first
- restrained
- lavender/purple accent
- focused utility
- calm
- clean

Avoid:

- gradients unless there is a very strong reason
- glassmorphism
- neon/gaming aesthetic
- bodybuilding clichés
- excessive animation
- decorative dashboards

------------------------------------------
APP ICON
------------------------------------------

Finish Android launcher-icon integration properly.

Review:

- adaptive icon foreground
- adaptive icon background
- safe zones
- round launcher presentation
- monochrome/themed icon support
- phone launcher
- Wear launcher

Use the accepted Workout Companion visual identity.

Do not rename applicationId/package/Room identifiers solely for branding.

------------------------------------------
MOTION
------------------------------------------

Use subtle motion when it communicates:

- set completion
- expansion/collapse
- reorder
- current exercise transition
- dialog/sheet state
- rest → ready
- workout completion

Never make a frequently used action slower for decorative animation.

------------------------------------------
ACCESSIBILITY
------------------------------------------

Maintain:

- readable contrast
- proper touch targets
- readable text
- state not conveyed only through color

Before changing UI:

perform a short visual audit and document the main inconsistencies you intend to fix.

Then implement them consistently rather than applying isolated cosmetic tweaks.

Acceptance:

- application feels visually coherent
- phone and Wear feel like the same product
- no workout workflow becomes slower
- no business behavior changes
- icon works correctly with modern Android themed icons

STOP after Phase 6.

## 57.7 First-run onboarding

Improve first-run experience without creating a tutorial slideshow.

Do NOT build:

- multi-page marketing carousel
- feature-tour popup sequence
- mandatory tutorial
- account creation

The seeded Exercise Library already exists.

The first-run goal is therefore:

help the user create the first usable Program quickly.

Preferred experience:

No Program exists

Workout Companion

Create your first program to get started.

[ Create program ]

Then guide naturally through existing UI:

Create program
→ create Training Day
→ add exercises
→ activate program
→ start workout

Use contextual empty states and focused calls to action.

Do not create a separate duplicate onboarding implementation of the Program builder.

Reuse the real Program UI.

Optional short explanatory copy is fine.

Do not automatically create a sample program unless explicitly requested later.

Backup restore must remain easily accessible for a user reinstalling the app who wants to restore instead of creating a new program.

Settings / restore should therefore remain reachable even without an existing Program.

Acceptance:

- fresh user understands the next action
- no mandatory tutorial
- seeded exercises are immediately useful
- creating the first program uses normal production flows
- restore remains possible without completing onboarding

STOP after Phase 7.

## 57.8 Program export / import

Add portable export/import for ONE training Program.

This is separate from full application backup.

Purpose:

- copy a Program between installations
- share a Program
- duplicate/transfer Program configuration without transferring History

Use a versioned JSON format.

Example conceptual metadata:

{
    "format": "workout-companion-program",
    "formatVersion": 1,
    "exportedAt": "...",
    "program": ...
}

Export:

include the selected Program and everything needed to reconstruct it:

- Program name
- Training Days
- Day ordering
- exercise references/names
- exercise ordering
- tracking mode
- planned sets
- rep configuration
- duration configuration where applicable
- weight
- increment
- rest
- setup notes
- warm-up configuration
- per-set targets
- supersets
- current configured progression targets

Do NOT include:

- WorkoutSession history
- historical SessionExercises
- historical SessionSets
- unrelated Programs
- active workout
- application settings

Import:

- create a NEW Program
- do not overwrite an existing Program automatically
- create new template IDs
- create independent progression state
- create new superset IDs
- imported Program should not become active automatically unless the user explicitly chooses it

Exercise Library handling:

If an imported exercise name already exists:

reuse the existing Exercise where safe.

If it does not exist:

create a new Exercise.

Prefer case-insensitive exact-name matching.

Do not create duplicate library exercises unnecessarily.

Program-name conflict:

handle safely.

For example:

Push Pull Legs
→ Push Pull Legs imported

or ask for a new name.

Do not overwrite silently.

Use Android document picker.

No filesystem permission.

No cloud backend.

No sharing service dependency.

Backup/restore remains the mechanism for cloning the complete application including History.

Program export/import is intentionally narrower.

Acceptance:

- export one Program
- import into a clean installation
- Program configuration is reconstructed accurately
- no History is imported
- source and imported Program do not share mutable progression IDs
- existing Programs remain untouched
- repeated import does not corrupt existing data

STOP after Phase 8.

## 57.9 Deferred / non-goals

Document these in SPEC as deferred or explicitly out of scope.

DO NOT IMPLEMENT THEM as part of this roadmap.

Deferred:

- workout/session free-text notes
- kg / lb unit switching
  - future Settings preference
  - architecture should not unnecessarily block it
  - do not implement yet
- plate calculator

Not planned:

- automatic rep counting
- motion-based rep detection
- per-exercise temporary rest override

Continue to avoid unless explicitly requested later:

- estimated 1RM
- generic PR scoring
- workout volume dashboards
- weekly muscle-volume analysis
- muscle maps
- bodyweight tracking
- calories
- nutrition
- AI coaching
- social features
- accounts
- cloud backend
- achievements
- streaks
- gamification
- Health Connect

ARCHITECTURE RULES

Phone remains authoritative.

Wear OS is a companion.

Room remains the source of truth.

Historical Session data remains immutable after completion.

Snapshots remain authoritative for History.

Do not retroactively reconstruct historical data from current Program configuration.

Avoid adding schema fields simply because they may be useful someday.

Schema migrations are acceptable when required by a real feature.

Every migration must preserve existing user data.

Backup format changes must be versioned if schema additions require them.

Program import format must also be versioned.

Do not silently change progression semantics.

QUALITY RULES

For every phase:

- preserve existing tests
- add tests for new business rules
- run unit tests
- run relevant instrumentation tests where practical
- build :app
- build :wear when shared state/protocol/UI is affected
- do not leave TODO implementations
- do not leave dead duplicate code
- do not introduce a dependency when a small existing/native implementation is sufficient

When requirements conflict, prefer:

1. historical data correctness
2. progression correctness
3. active-workout persistence
4. workout logging speed
5. simple architecture
6. visual polish

