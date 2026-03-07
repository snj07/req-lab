package com.reqlab.ui.desktop.theme

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
    background        = Color(0xFFF2F3F8),
    surface           = Color(0xFFFFFFFF),
    surfaceVariant    = Color(0xFFEEEFF5),
    surfaceContainer  = Color(0xFFE5E6EE),
    surfaceHigh       = Color(0xFFD8DAEB),
    border            = Color(0xFFCCCEDF),
    borderLight       = Color(0xFFBBBED5),

    primary           = Color(0xFF4A5FDE),
    primaryContainer  = Color(0xFFDDE2FF),
    onPrimary         = Color(0xFFFFFFFF),

    secondary          = Color(0xFF1F9B84),
    secondaryContainer = Color(0xFFCCF3EC),
    tertiary           = Color(0xFF9A6E00),

    error = Color(0xFFB5232D),

    onBackground      = Color(0xFF1A1B2E),
    onSurface         = Color(0xFF2C2D42),
    onSurfaceVariant  = Color(0xFF5C5D74),
    onSurfaceDim      = Color(0xFF8888A0),

    methodGet     = Color(0xFF1F9B84),
    methodPost    = Color(0xFF9A6E00),
    methodPut     = Color(0xFF3050CC),
    methodPatch   = Color(0xFF7B34B8),
    methodDelete  = Color(0xFFB5232D),
    methodOptions = Color(0xFF6B6B80),
    methodHead    = Color(0xFF6B6B80),

    statusSuccess     = Color(0xFF1F9B84),
    statusRedirect    = Color(0xFF9A6E00),
    statusClientError = Color(0xFF9A6E00),
    statusServerError = Color(0xFFB5232D),

    hoverOverlay = Color(0x0A000000),   // 4 % black
    selectedItem = Color(0x1A4A5FDE),   // 10 % primary
)

/** Composition local providing the active [AppColorPalette]. Defaults to dark. */
val LocalAppColors = compositionLocalOf<AppColorPalette> { DarkAppColors }
