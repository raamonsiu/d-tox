package dev.d1ito.dtox.ui.reader

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Animator duration scale when the system does not set one (animations at normal speed). */
private const val DEFAULT_ANIMATOR_SCALE = 1f

/**
 * Whether page turns slide (RF-12): only when the reader's "Animación al pasar página" setting is
 * on and the system "Remove animations" setting has not set the animator scale to 0. Either one
 * turns the animation off.
 *
 * @param pageAnimationSetting the reader's own setting.
 * @param systemAnimatorScale `Settings.Global.ANIMATOR_DURATION_SCALE`.
 */
internal fun pageTurnAnimates(
    pageAnimationSetting: Boolean,
    systemAnimatorScale: Float,
): Boolean = pageAnimationSetting && systemAnimatorScale > 0f

/** [pageTurnAnimates] with the current system animator scale. */
@Composable
internal fun rememberPageTurnAnimates(pageAnimationSetting: Boolean): Boolean {
    val contentResolver = LocalContext.current.contentResolver
    val systemAnimatorScale =
        remember(contentResolver) {
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                DEFAULT_ANIMATOR_SCALE,
            )
        }
    return pageTurnAnimates(pageAnimationSetting, systemAnimatorScale)
}

/** The desktop-like slide of docs/04-design.md, or an instant change without animations. */
internal fun pageTurnSpec(animates: Boolean): AnimationSpec<Float> =
    if (animates) tween(PAGE_TURN_MILLIS, easing = FastOutSlowInEasing) else snap()
