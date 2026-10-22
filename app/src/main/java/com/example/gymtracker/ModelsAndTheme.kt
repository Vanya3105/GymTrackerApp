package com.example.gymtracker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class UserProfile(
    val hasCompletedOnboarding: Boolean = false,
    val age: Int = 0,
    val currentWeightKg: Float = 0f,
    val splitProgram: String = "PPL_ARNOLD",
    val currentStreakWeeks: Int = 0 // Added for consistency streak tracking
)

@Immutable
data class WeightLogEntry(
    val id: Long,
    val dateKey: String,
    val displayDate: String,
    val weightKg: Float
)

@Immutable
data class AppSettings(
    val isDarkMode: Boolean = true,
    val targetFps: Int = 120,
    val hapticsEnabled: Boolean = true,
    val weightStepKg: Float = 2.5f,
    val restTimerSeconds: Int = 90,
    val keepScreenAwake: Boolean = true,
    val restSoundEnabled: Boolean = true,
    val themeChoice: String = "OBSIDIAN"
)

@Immutable
data class CompletedWorkout(
    val id: Long,
    val splitTitle: String,
    val dateKey: String,
    val displayDate: String,
    val durationSeconds: Long,
    val bodyWeightKg: Float,
    val photoPath: String?,
    val skippedExercises: List<String>,
    val exercises: List<ExerciseEntry>,
    val totalVolumeKg: Int = 0, // Added for advanced analytics
    val prsHit: Int = 0         // Added for advanced analytics
)

@Immutable
data class ExerciseEntry(
    val name: String,
    val targetMuscle: String = "",
    val restSeconds: Int = 90,
    val isSkipped: Boolean = false,
    val sets: List<SetEntry> = emptyList(),
    val isSuperset: Boolean = false // Added for future superset grouping
)

@Immutable
data class SetEntry(
    val setNumber: Int,
    val weight: String,
    val reps: String,
    val isWarmup: Boolean = false,
    val intensityType: String = "NORMAL", // NORMAL, WARMUP, DROP, REST_PAUSE
    val isCompleted: Boolean = false,
    val restSeconds: Int = 90
)

@Immutable
data class WorkoutSplit(
    val title: String,
    val subtitle: String,
    val code: String
)

@Immutable
data class SplitProgramInfo(
    val id: String,
    val name: String,
    val tagline: String,
    val weeklyGoal: Int,
    val splits: List<WorkoutSplit>
)

@Immutable
data class WeekSummaryData(
    val weekOffset: Int,
    val titleLabel: String,
    val dateRangeLabel: String,
    val workouts: List<CompletedWorkout>,
    val completedCoreSplits: Set<String>,
    val recommendedSplit: String,
    val reasonText: String,
    val weeklyGoal: Int,
    val fatiguedMuscles: List<String> = emptyList() // Added for muscle recovery tracking
)

@Immutable
data class AiAdvice(
    val message: String,
    val isPositive: Boolean,
    val isWarning: Boolean,
    val suggestedWeight: String
)

@Immutable
data class GymPalette(
    val isDark: Boolean,
    val bgPrimary: Color,
    val bgCard: Color,
    val bgControl: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val accentPrimary: Color,
    val onAccentPrimary: Color,
    val accentSecondary: Color,
    val successSurface: Color,
    val successText: Color,
    val warningSurface: Color,
    val warningText: Color,
    val dangerSurface: Color,
    val dangerText: Color
)

val DarkLuxuryPalette = GymPalette(
    isDark = true,
    bgPrimary = Color(0xFF07080A),
    bgCard = Color(0xFF111318),
    bgControl = Color(0xFF1E222B),
    textPrimary = Color(0xFFFAFAFC),
    textSecondary = Color(0xFF8A93A6),
    borderSubtle = Color(0xFF1E222B),
    borderStrong = Color(0xFF2C3240),
    accentPrimary = Color(0xFFCCFF00),
    onAccentPrimary = Color(0xFF07080A),
    accentSecondary = Color(0xFF00F0FF),
    successSurface = Color(0xFF132418),
    successText = Color(0xFF4ADE80),
    warningSurface = Color(0xFF2B1C10),
    warningText = Color(0xFFFB923C),
    dangerSurface = Color(0xFF2A1015),
    dangerText = Color(0xFFFF453A)
)

val LightEditorialPalette = GymPalette(
    isDark = false,
    bgPrimary = Color(0xFFF4F6F9),
    bgCard = Color(0xFFFFFFFF),
    bgControl = Color(0xFFE6EAF2),
    textPrimary = Color(0xFF0B0F19),
    textSecondary = Color(0xFF5B6478),
    borderSubtle = Color(0xFFD9DFE8),
    borderStrong = Color(0xFFB0B9CB),
    accentPrimary = Color(0xFFFF4500),
    onAccentPrimary = Color(0xFFFFFFFF),
    accentSecondary = Color(0xFF0066FF),
    successSurface = Color(0xFFE0F5E8),
    successText = Color(0xFF166534),
    warningSurface = Color(0xFFFFF0D6),
    warningText = Color(0xFF9A3412),
    dangerSurface = Color(0xFFFFE5E5),
    dangerText = Color(0xFF991B1B)
)

val CyberpunkNeonPalette = GymPalette(
    isDark = true,
    bgPrimary = Color(0xFF090A10),
    bgCard = Color(0xFF131520),
    bgControl = Color(0xFF1D2130),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF94A3B8),
    borderSubtle = Color(0xFF1D2130),
    borderStrong = Color(0xFF333A56),
    accentPrimary = Color(0xFFFF0055),
    onAccentPrimary = Color(0xFFFFFFFF),
    accentSecondary = Color(0xFF00F0FF),
    successSurface = Color(0xFF0D2818),
    successText = Color(0xFF22C55E),
    warningSurface = Color(0xFF2E1A0C),
    warningText = Color(0xFFF97316),
    dangerSurface = Color(0xFF2E0D12),
    dangerText = Color(0xFFEF4444)
)

val MidnightOledPalette = GymPalette(
    isDark = true,
    bgPrimary = Color(0xFF000000),
    bgCard = Color(0xFF0A0A0A),
    bgControl = Color(0xFF171717),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFF737373),
    borderSubtle = Color(0xFF262626),
    borderStrong = Color(0xFF404040),
    accentPrimary = Color(0xFF3B82F6),
    onAccentPrimary = Color(0xFFFFFFFF),
    accentSecondary = Color(0xFFA855F7),
    successSurface = Color(0xFF052E16),
    successText = Color(0xFF4ADE80),
    warningSurface = Color(0xFF451A03),
    warningText = Color(0xFFFB923C),
    dangerSurface = Color(0xFF450A0A),
    dangerText = Color(0xFFF87171)
)

val LocalPalette = staticCompositionLocalOf { DarkLuxuryPalette }