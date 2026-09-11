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
- wearable integration
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

# 54. Development rules for the coding agent

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

# 55. Definition of MVP complete

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