package com.jupiman.workouttracker.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

internal object DefaultExerciseSeeder {
    val exerciseNames = listOf(
        "Bench Press",
        "Incline Bench Press",
        "Dumbbell Bench Press",
        "Incline Dumbbell Press",
        "Machine Chest Press",
        "Pec Deck",
        "Cable Fly",
        "Push-Up",
        "Lat Pulldown",
        "Pull-Up",
        "Chin-Up",
        "Seated Cable Row",
        "Machine Row",
        "Barbell Row",
        "Dumbbell Row",
        "Straight-Arm Pulldown",
        "Face Pull",
        "Overhead Press",
        "Dumbbell Shoulder Press",
        "Machine Shoulder Press",
        "Dumbbell Lateral Raise",
        "Cable Lateral Raise",
        "Rear Delt Fly",
        "Barbell Curl",
        "Dumbbell Curl",
        "Hammer Curl",
        "Cable Curl",
        "Preacher Curl",
        "Triceps Pushdown",
        "Overhead Triceps Extension",
        "Skull Crusher",
        "Dip",
        "Close-Grip Bench Press",
        "Squat",
        "Front Squat",
        "Hack Squat",
        "Leg Press",
        "Leg Extension",
        "Leg Curl",
        "Romanian Deadlift",
        "Deadlift",
        "Hip Thrust",
        "Bulgarian Split Squat",
        "Walking Lunge",
        "Standing Calf Raise",
        "Seated Calf Raise",
        "Cable Crunch",
        "Hanging Leg Raise",
        "Crunch",
        "Plank",
    )

    fun seed(db: SupportSQLiteDatabase) {
        val createdAt = System.currentTimeMillis()
        exerciseNames.forEach { name ->
            db.execSQL(
                "INSERT INTO exercises (name, archived, createdAt) VALUES (?, 0, ?)",
                arrayOf(name, createdAt),
            )
        }
    }
}

