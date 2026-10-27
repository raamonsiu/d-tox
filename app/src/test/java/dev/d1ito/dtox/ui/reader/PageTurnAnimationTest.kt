package dev.d1ito.dtox.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reader's setting and the system "Remove animations" setting combined (RF-12). */
class PageTurnAnimationTest {
    @Test
    fun settingOn_andSystemAnimations_slides() {
        assertTrue(pageTurnAnimates(pageAnimationSetting = true, systemAnimatorScale = 1f))
        assertTrue(pageTurnAnimates(pageAnimationSetting = true, systemAnimatorScale = 0.5f))
    }

    @Test
    fun settingOff_isInstant_evenWithSystemAnimations() {
        assertFalse(pageTurnAnimates(pageAnimationSetting = false, systemAnimatorScale = 1f))
    }

    @Test
    fun systemRemoveAnimations_isInstant_evenWithTheSettingOn() {
        assertFalse(pageTurnAnimates(pageAnimationSetting = true, systemAnimatorScale = 0f))
    }
}
