package de.psmobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

/**
 * Zeigt ein Original-Icon von PrusaSlicer.
 *
 * Die SVGs liegen unveraendert in assets/psui/icons und stammen aus
 * resources/icons. Bewusst keine nachgezeichneten Material-Icons -
 * siehe docs/decisions.md, E-12.
 */
@Composable
fun PsIcon(
    name: String,
    modifier: Modifier = Modifier,
    tint: ColorFilter? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    AsyncImage(
        model = "file:///android_asset/${PsUi.iconAsset(name)}",
        contentDescription = contentDescription,
        imageLoader = loader,
        colorFilter = tint,
        modifier = modifier,
    )
}
