package com.reqlab.ui.shared.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * All colors used by the ReqLab UI, switchable per theme.
 */
data class AppColorPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderLight: Color,

    val primary: Color,
    val primaryContainer: Color,
    val onPrimary: Color,

    val secondary: Color,
    val secondaryContainer: Color,
    val tertiary: Color,

    val error: Color,

    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onSurfaceDim: Color,

    val methodGet: Color,
    val methodPost: Color,
    val methodPut: Color,
    val methodPatch: Color,
    val methodDelete: Color,
    val methodOptions: Color,
    val methodHead: Color,

    val statusSuccess: Color,
    val statusRedirect: Color,
    val statusClientError: Color,
    val statusServerError: Color,

    val hoverOverlay: Color,
    val selectedItem: Color,
)

val DarkAppColors = AppColorPalette(
    background        = Color(0xFF191A2A),
    surface           = Color(0xFF1E1F32),
    surfaceVariant    = Color(0xFF252640),
    surfaceContainer  = Color(0xFF2A2B45),
    surfaceHigh       = Color(0xFF30314D),
    border            = Color(0xFF383952),
    borderLight       = Color(0xFF45466A),

    primary           = Color(0xFF7B8DEF),
    primaryContainer  = Color(0xFF3D4580),
    onPrimary         = Color(0xFFFFFFFF),

    secondary          = Color(0xFF4EC9B0),
    secondaryContainer = Color(0xFF2A5C50),
    tertiary           = Color(0xFFE5C07B),

    error = Color(0xFFE06C75),

    onBackground      = Color(0xFFE4E4EF),
    onSurface         = Color(0xFFD4D4E4),
    onSurfaceVariant  = Color(0xFF9191A8),
    onSurfaceDim      = Color(0xFF6C6C85),

    methodGet     = Color(0xFF4EC9B0),
    methodPost    = Color(0xFFE5C07B),
    methodPut     = Color(0xFF6C8EEF),
    methodPatch   = Color(0xFFC678DD),
    methodDelete  = Color(0xFFE06C75),
    methodOptions = Color(0xFF8B8B9E),
    methodHead    = Color(0xFF8B8B9E),

    statusSuccess     = Color(0xFF4EC9B0),
    statusRedirect    = Color(0xFFE5C07B),
    statusClientError = Color(0xFFE5C07B),
    statusServerError = Color(0xFFE06C75),

    hoverOverlay = Color(0x14FFFFFF),   // 8 % white
    selectedItem = Color(0x1A7B8DEF),   // 10 % primary
)

val LightAppColors = AppColorPalette(
    // Pure airy whites with the faintest blue-sky tint
    background        = Color(0xFFF8FAFC),   // almost pure white, whisper of sky
    surface           = Color(0xFFFFFFFF),   // pure white cards
    surfaceVariant    = Color(0xFFF1F5F9),   // barely-there cool tint
    surfaceContainer  = Color(0xFFE9EEF5),   // very light blue-gray
    surfaceHigh       = Color(0xFFDDE4EE),   // soft cool divider
    border            = Color(0xFFCED6E3),   // light, airy border
    borderLight       = Color(0xFFE2E8F2),   // near-invisible separator

    // Primary — vivid but not heavy, sky-leaning blue
    primary           = Color(0xFF4B6BF5),   // bright periwinkle-blue
    primaryContainer  = Color(0xFFEAEEFF),   // airy lavender tint
    onPrimary         = Color(0xFFFFFFFF),

    // Secondary — fresh mint teal
    secondary          = Color(0xFF0FA88E),   // bright, light teal
    secondaryContainer = Color(0xFFDEF7F3),   // very light mint wash

    tertiary           = Color(0xFFB07D10),   // warm amber, lighter
    error              = Color(0xFFCF3141),   // lighter, less harsh red

    // Text — deep but not black; feels lighter overall
    onBackground      = Color(0xFF1A2030),   // deep navy-black
    onSurface         = Color(0xFF1E2535),   // slightly lighter
    onSurfaceVariant  = Color(0xFF5A657A),   // medium blue-gray
    onSurfaceDim      = Color(0xFF96A0B4),   // light, airy hint text

    // HTTP Methods — brighter, lighter variants
    methodGet     = Color(0xFF0FA88E),       // fresh teal
    methodPost    = Color(0xFFB07D10),       // warm amber
    methodPut     = Color(0xFF4B6BF5),       // periwinkle
    methodPatch   = Color(0xFF8B44CC),       // lighter purple
    methodDelete  = Color(0xFFCF3141),       // lighter red
    methodOptions = Color(0xFF6E7A91),       // cool slate
    methodHead    = Color(0xFF6E7A91),

    // Status
    statusSuccess     = Color(0xFF0FA88E),
    statusRedirect    = Color(0xFFB07D10),
    statusClientError = Color(0xFFB07D10),
    statusServerError = Color(0xFFCF3141),

    hoverOverlay = Color(0x094B6BF5),        // 3.5% primary tint on hover
    selectedItem = Color(0x154B6BF5),        // 8% primary for selection
)

/** Composition local providing the active [AppColorPalette]. Defaults to dark. */
val LocalAppColors = compositionLocalOf<AppColorPalette> { DarkAppColors }
