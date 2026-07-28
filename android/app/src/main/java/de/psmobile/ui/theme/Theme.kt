package de.psmobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Orange = Color(0xFFFF6B35)      // an die Prusa-Farbwelt angelehnt
private val OrangeDark = Color(0xFFCC4E1E)

private val LightScheme = lightColorScheme(primary = Orange, secondary = OrangeDark)
private val DarkScheme = darkColorScheme(primary = Orange, secondary = OrangeDark)

@Composable
fun PSMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You ab Android 12 - der Nutzer erwartet auf dem Tablet,
    // dass sich die App ins System einfuegt.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
