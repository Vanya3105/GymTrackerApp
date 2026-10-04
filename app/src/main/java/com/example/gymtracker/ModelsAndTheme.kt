package com.example.gymtracker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class GymPalette(
    val isDark: Boolean,
    val bgPrimary: Color,
    val bgCard: Color,
    val bgControl: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentPrimary: Color,
    val onAccentPrimary: Color,
    val successText: Color,
    val successSurface: Color,
    val warningText: Color,
    val warningSurface: Color,
    val dangerText: Color,
    val dangerSurface: Color
)

val DarkLuxuryPalette = GymPalette(
    isDark = true,
    bgPrimary = Color(0xFF0F1115),
    bgCard = Color(0xFF181B22),
    bgControl = Color(0xFF222630),
    borderSubtle = Color(0xFF262B36),
    borderStrong = Color(0xFF343B4A),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF8E95A5),
    accentPrimary = Color(0xFF3B82F6),
    onAccentPrimary = Color(0xFFFFFFFF),
    successText = Color(0xFF10B981),
    successSurface = Color(0x1F10B981),
    warningText = Color(0xFFF59E0B),
    warningSurface = Color(0x1FF59E0B),
    dangerText = Color(0xFFEF4444),
    dangerSurface = Color(0x1FEF4444)
)

val LightEditorialPalette = GymPalette(
    isDark = false,
    bgPrimary = Color(0xFFF5F7FA),
    bgCard = Color(0xFFFFFFFF),
    bgControl = Color(0xFFEEF1F6),
    borderSubtle = Color(0xFFE5E9F0),
    borderStrong = Color(0xFFD0D7E2),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF6B7280),
    accentPrimary = Color(0xFF2563EB),
    onAccentPrimary = Color(0xFFFFFFFF),
    successText = Color(0xFF059669),
    successSurface = Color(0x1A059669),
    warningText = Color(0xFFD97706),
    warningSurface = Color(0x1AD97706),
    dangerText = Color(0xFFDC2626),
    dangerSurface = Color(0x1ADC2626)
)

val LocalPalette = staticCompositionLocalOf { DarkLuxuryPalette }

@Immutable
data class AppSettings(
    val isDarkMode: Boolean = true,
    val targetFps: Int = 120,
    val hapticsEnabled: Boolean = true,
    val weightStepKg: Float = 2.5f,
    val restTimerSeconds: Int = 90,
    val keepScreenAwake: Boolean = true,
    val restSoundEnabled: Boolean = true
)

@Immutable
data class SetEntry(
    val setNumber: Int,
    val weight: String = "20",
    val reps: String = "8",
    val isWarmup: Boolean = false,
    val isCompleted: Boolean = false
)

@Immutable
data class ExerciseEntry(
    val name: String,
    val targetMuscle: String = "",
    val sets: List<SetEntry> = listOf(
        SetEntry(1, weight = "20", reps = "8", isWarmup = true),
        SetEntry(2, weight = "40", reps = "8", isWarmup = false),
        SetEntry(3, weight = "40", reps = "8", isWarmup = false)
    )
)

@Immutable
data class WorkoutSplit(
    val title: String,
    val subtitle: String,
    val code: String
)

@Immutable
data class CompletedWorkout(
    val id: Long,
    val splitTitle: String,
    val dateKey: String,
    val displayDate: String,
    val durationSeconds: Long = 0L,
    val photoPath: String? = null,
    val exercises: List<ExerciseEntry>
)

@Immutable
data class AiAdvice(
    val message: String,
    val isPositive: Boolean,
    val isWarning: Boolean,
    val suggestedWeight: String = ""
)

@Immutable
data class WeekSummaryData(
    val weekOffset: Int,
    val titleLabel: String,
    val dateRangeLabel: String,
    val workouts: List<CompletedWorkout>,
    val completedCoreSplits: Set<String>,
    val recommendedSplit: String,
    val reasonText: String
)