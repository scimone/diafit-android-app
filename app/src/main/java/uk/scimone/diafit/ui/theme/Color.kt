package uk.scimone.diafit.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Light Theme Colors: cool, neutral and calm. Content sits on white cards over a faint cool-grey page, and the
// accent is the same blue family as dark mode (deeper, so it holds contrast on white).
val LightPrimary = Color(0xFF2563C9)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDCE8FB)
val LightOnPrimaryContainer = Color(0xFF0B2A57)

val LightSecondary = Color(0xFF566579)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD9E5F8)   // selected chips, nav indicator
val LightOnSecondaryContainer = Color(0xFF12294D)

val LightTertiary = Color(0xFF7448B0)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFEBDFFA)
val LightOnTertiaryContainer = Color(0xFF2B0F52)

val LightBackground = Color(0xFFF3F5F9)
val LightOnBackground = Color(0xFF151A21)

val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF151A21)
val LightSurfaceVariant = Color(0xFFE4E8EF)
val LightOnSurfaceVariant = Color(0xFF4E5865)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFFFFF)     // cards
val LightSurfaceContainer = Color(0xFFECEFF5)        // navigation bar, tonal chips
val LightSurfaceContainerHigh = Color(0xFFE5E9F0)    // pills, selected-off tiles
val LightSurfaceContainerHighest = Color(0xFFDCE1EA)
val LightOutline = Color(0xFF7B8594)
val LightOutlineVariant = Color(0xFFD5DBE5)          // hairline borders

val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFBE3E0)
val LightOnErrorContainer = Color(0xFF410E0B)

// Dark Theme Colors

// Core Neutrals
val DarkBackground = Color(0xFF0c0d11)         // Deep charcoal background
val DarkOnBackground = Color(0xFFE0E0E0)        // Light gray text
val DarkSurface = Color(0xFF18191d)             // Slightly lighter surface
val DarkOnSurface = Color(0xFFE0E0E0)           // Light gray text on surface
val DarkSurfaceVariant = Color(0xFF2C2C2C)      // Muted gray for variant surfaces
val DarkOnSurfaceVariant = Color(0xFFB0B0B0)    // Light gray text on variants

// Primary Palette
val DarkPrimary = Color(0xFF4A90E2)             // Cool blue accent
val DarkOnPrimary = Color(0xFFFFFFFF)           // White text on primary
val DarkPrimaryContainer = Color(0xFF003366)    // Deep blue container
val DarkOnPrimaryContainer = Color(0xFFFFFFFF)  // White text on container

// Secondary Palette
val DarkSecondary = Color(0xFF7F8C8D)           // Cool gray
val DarkOnSecondary = Color(0xFFFFFFFF)         // White text on secondary
val DarkSecondaryContainer = Color(0xFF2C3E50)  // Dark gray container
val DarkOnSecondaryContainer = Color(0xFFFFFFFF) // White text on container

// Tertiary Palette
val DarkTertiary = Color(0xFF9B59B6)            // Soft purple
val DarkOnTertiary = Color(0xFFFFFFFF)          // White text on tertiary
val DarkTertiaryContainer = Color(0xFF8E44AD)   // Deep purple container
val DarkOnTertiaryContainer = Color(0xFFFFFFFF) // White text on container

// Error Palette
val DarkError = Color(0xFFE74C3C)               // Vivid red
val DarkOnError = Color(0xFFFFFFFF)             // White text on error
val DarkErrorContainer = Color(0xFF7F8C8D)      // Muted gray container
val DarkOnErrorContainer = Color(0xFFFFFFFF)    // White text on error container

// Diabetes Specific Colors
// Dark values are the original palette; light values are deeper so lines, bubbles and coloured figures hold their
// contrast on white. They are read as properties (not constants), so charts follow the theme.
private var darkPalette by mutableStateOf(false)

/** True while the dark palette is active (for the few drawings that tint differently per theme). */
val isDarkPalette: Boolean get() = darkPalette

/** Called by [DiafitTheme]; every data colour below follows it. */
internal fun applyPalette(dark: Boolean) { if (darkPalette != dark) darkPalette = dark }

val InRange: Color get() = if (darkPalette) Color(0xFF2ECC9B) else Color(0xFF14A077)
val BelowRange: Color get() = if (darkPalette) Color(0xFFEF6C5B) else Color(0xFFDC4A3C)
val AboveRange: Color get() = if (darkPalette) Color(0xFF9AA5FF) else Color(0xFF5662D8)
val Carbs: Color get() = if (darkPalette) Color(0xFFFFD54F) else Color(0xFFF0A30A)
val Bolus: Color get() = if (darkPalette) Color(0xFF42A5F5) else Color(0xFF2B8CE6)
val Basal: Color get() = if (darkPalette) Color(0xFF3585E6) else Color(0xFF0F4FA8)  // dark blue for basal insulin everywhere
val Activity: Color get() = if (darkPalette) Color(0xFFE0489E) else Color(0xFFD42E86)
val Warning: Color get() = if (darkPalette) Color(0xFFF59E0B) else Color(0xFFD97706)
val Device: Color get() = if (darkPalette) Color(0xFF90A4AE) else Color(0xFF6B8190)

/** Meal-type tiles, protein/fat and the neutral glucose measures (were hard-coded pastels). */
val MealBreakfast: Color get() = if (darkPalette) Color(0xFFFFB74D) else Color(0xFFEE9A14)
val MealLunch: Color get() = if (darkPalette) Color(0xFF4DB6AC) else Color(0xFF1F9A8E)
val MealDinner: Color get() = if (darkPalette) Color(0xFF7986CB) else Color(0xFF5560BE)
val MealSnack: Color get() = if (darkPalette) Color(0xFFFF8A65) else Color(0xFFEE6A3F)
val ProteinColor: Color get() = if (darkPalette) Color(0xFFEF9A9A) else Color(0xFFD65A5A)
val FatColor: Color get() = if (darkPalette) Color(0xFFFFB74D) else Color(0xFFE8921A)
val GlucoseNeutralColor: Color get() = if (darkPalette) Color(0xFF4DD0E1) else Color(0xFF1597AD)


// Sleep stages (hypnogram), all indigo-ish except awake so a night reads as one family
val SleepAwake: Color get() = if (darkPalette) Color(0xFFFFB74D) else Color(0xFFF0A040)
val SleepRem: Color get() = if (darkPalette) Color(0xFFB39DDB) else Color(0xFF9378CC)
val SleepLight: Color get() = if (darkPalette) Color(0xFF7E8CE0) else Color(0xFF6672D4)
val SleepDeep: Color get() = if (darkPalette) Color(0xFF5C6BC0) else Color(0xFF3949AB)
val Sleep: Color get() = if (darkPalette) Color(0xFF7986CB) else Color(0xFF5E6BC0)         // generic sleep colour (wash, labels, sleep without stage data)

// Additional Colors
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)

/** Shared fill of the glucose target-range band on every chart (Home and History); kept very subtle. */
@androidx.compose.runtime.Composable
fun targetRangeBandColor(): Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = if (darkPalette) 0.05f else 0.045f)
