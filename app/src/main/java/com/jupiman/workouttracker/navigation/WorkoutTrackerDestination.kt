package com.jupiman.workouttracker.navigation

enum class WorkoutTrackerDestination(
    val route: String,
    val label: String,
) {
    Workout("workout", "Workout"),
    Program("program", "Program"),
    History("history", "History"),
}

