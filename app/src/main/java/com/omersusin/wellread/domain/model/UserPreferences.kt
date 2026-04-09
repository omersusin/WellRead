package com.omersusin.wellread.domain.model

data class UserPreferences(
    val isDarkMode: Boolean = true,
    val useDynamicColor: Boolean = true,
    val defaultWpm: Int = 300,
    val defaultMode: ReadingMode = ReadingMode.BIONIC,
    val fontSize: Float = 16f,
    val fontFamily: String = "Inter",
    val dailyGoalMinutes: Int = 20,
    val hapticFeedback: Boolean = true,
    val bionicFixationStrength: Float = 0.5f,
    val flashChunkSize: Int = 1
)
