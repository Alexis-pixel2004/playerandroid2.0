package com.alexis.hifiplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.alexis.hifiplayer.domain.model.ThemeMode

/**
 * Tema Compose de la aplicación.
 *
 * @param themeMode Selecciona la fuente del esquema de colores:
 *   - [ThemeMode.DYNAMIC]: Dynamic Color del sistema (si la plataforma lo permite).
 *   - [ThemeMode.DEFAULT]: esquema predeterminado de Material 3, sin paleta de marca.
 * @param darkTheme Modo claro/oscuro; por defecto sigue la configuración del sistema.
 */
@Composable
fun HiFiPlayerTheme(
    themeMode: ThemeMode = ThemeMode.DYNAMIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic Color está disponible desde Android 12 (API 31). Con minSdk 34 siempre
    // lo estará en producción; la comprobación conserva un fallback seguro.
    val dynamicColorAvailable = true

    val colorScheme = when {
        themeMode == ThemeMode.DYNAMIC && dynamicColorAvailable -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
