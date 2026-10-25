package com.alexis.hifiplayer.ui.icons

import androidx.annotation.DrawableRes
import com.alexis.hifiplayer.R

/**
 * Iconos Material Symbols (Vector Drawable) usados en la app.
 *
 * Centraliza las referencias a recursos para que las pantallas no dependan de la
 * librería de iconos de Compose (`material-icons-extended`), que está descontinuada.
 * Se consumen con `androidx.compose.ui.res.painterResource`.
 */
object AppIcons {
    @DrawableRes val ArrowBack: Int = R.drawable.ic_arrow_back
    @DrawableRes val MenuBook: Int = R.drawable.ic_menu_book
    @DrawableRes val Home: Int = R.drawable.ic_home
    @DrawableRes val Settings: Int = R.drawable.ic_settings
    @DrawableRes val PlayArrow: Int = R.drawable.ic_play_arrow
    @DrawableRes val Pause: Int = R.drawable.ic_pause
    @DrawableRes val SkipPrevious: Int = R.drawable.ic_skip_previous
    @DrawableRes val SkipNext: Int = R.drawable.ic_skip_next
    @DrawableRes val MusicNote: Int = R.drawable.ic_music_note
    @DrawableRes val Speaker: Int = R.drawable.ic_speaker
    @DrawableRes val Error: Int = R.drawable.ic_error
}
