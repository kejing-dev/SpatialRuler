package io.embodit.spatialruler.ui.ruler

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copy table of the simplify spec (L) and its acceptance rule (M 1 / M 9), checked on the resource
 * files themselves: every key of the default language exists in zh unless it is marked
 * `translatable="false"`, every description an icon-only control reads is there, and the visible
 * sentences keep to their word budgets (snackbars <= 6 words / <= 12 characters, the coachmark <= 8 / <= 12,
 * the clear dialog <= 6 words in all, the blocked dialog <= 13).
 */
class StringsResourceTest {
    /** Unit symbols are figures, not words (M 1). */
    private val UNITS = setOf("m", "cm", "mm", "ft·in", "ft", "in")

    private val en = parse(File("src/main/res/values/strings.xml"))
    private val zh = parse(File("src/main/res/values-zh/strings.xml"))

    private fun parse(file: File): Map<String, Pair<String, Boolean>> {
        assertTrue("missing ${file.path}", file.isFile)
        val text = file.readText()
        val out = LinkedHashMap<String, Pair<String, Boolean>>()
        val string = Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        for (m in string.findAll(text)) {
            val translatable = !m.groupValues[2].contains("""translatable="false"""")
            out[m.groupValues[1]] = m.groupValues[3].replace("\\'", "'").replace("&gt;", ">") to translatable
        }
        val plurals = Regex("""<plurals name="([^"]+)">""")
        for (m in plurals.findAll(text)) out["plurals:" + m.groupValues[1]] = "" to true
        return out
    }

    private fun words(s: String): Int =
        s.split(Regex("\\s+")).count { w ->
            // figures, marks and symbols do not count as words
            w.isNotEmpty() && w.any { it.isLetter() } && !w.all { it in "▲⦸◆●○±×—′″·" } && w !in UNITS
        }

    /** Chinese budget: characters that are letters (figures, spaces and punctuation do not count, M 1). */
    private fun chars(s: String): Int = s.count { it.isLetter() }

    @Test
    fun everyTranslatableKeyExistsInChinese() {
        val missing = en.filter { (k, v) -> v.second && k !in zh }.keys
        assertEquals("missing in values-zh: $missing", emptySet<String>(), missing)
        val extra = zh.keys - en.keys
        assertEquals("only in values-zh: $extra", emptySet<String>(), extra)
    }

    @Test
    fun theDescriptionsOfIconOnlyControlsAndGlyphsExist() {
        val needed =
            listOf(
                "action_undo", "action_more", "action_snap", "action_close", "menu_exit", "lang_system", "lang_en", "lang_en_short",
                "pin_tune", "pin_remove", "step_left", "step_right", "step_near", "step_far", "step_down", "step_up",
                "tape_retract", "coach_got_it", "lcd_starting", "lcd_armed", "lcd_tuning_plain", "lcd_lost", "lcd_empty",
                "reticle_fine_gear", "reticle_far", "reticle_none", "reticle_mapping", "reticle_gear", "reticle_far_figure",
                "grade_contact", "grade_fine", "grade_coarse", "grade_far", "grade_unsaved", "plurals:count_pins", "plurals:count_tapes",
            )
        val missing = needed.filter { it !in en || (en[it]!!.second && it !in zh) }
        assertEquals(emptyList<String>(), missing)
        needed.filter { !it.startsWith("plurals:") }.forEach { assertTrue(it, en[it]!!.first.isNotBlank()) }
    }

    @Test
    fun removedCopyIsGone() {
        val gone =
            listOf(
                "lcd_scanning", "lcd_ready", "lcd_live", "lcd_last", "lcd_selected", "lcd_tuning", "menu_language", "pin_title",
                "surface_floor", "axis_u", "step_hint", "step_decrease", "step_increase", "grade_snapped_corner", "grade_pending",
                "grade_frozen", "tape_start", "tape_end", "reticle_steady", "reticle_corner", "reticle_contact", "dialog_clear_title",
                "action_clear_all", "snack_restored", "snack_unsaved_desc", "snack_lost_desc", "snack_input", "snack_elsewhere_action",
                "coach_1", "coach_2", "action_set_pin", "menu_snap_on", "menu_snap_off",
            )
        assertEquals(emptyList<String>(), gone.filter { it in en || it in zh })
    }

    @Test
    fun visibleSentencesKeepToTheirBudgets() {
        val snacks = en.keys.filter { it.startsWith("snack_") }
        assertEquals(11, snacks.size)
        for (k in snacks) {
            assertTrue("$k: ${en[k]!!.first}", words(en[k]!!.first) <= 6)
            assertTrue("$k zh: ${zh[k]!!.first}", chars(zh[k]!!.first) <= 12)
        }
        assertTrue(words(en["coach_hint"]!!.first) <= 8)
        assertTrue(chars(zh["coach_hint"]!!.first) <= 12)
        val clear = words(en["dialog_clear_body"]!!.first) + words(en["action_cancel"]!!.first) + words(en["action_clear"]!!.first)
        assertTrue("clear dialog words: $clear", clear <= 6)
        val blocked = words(en["dialog_blocked_title"]!!.first) + words(en["dialog_blocked_body"]!!.first) + words(en["action_exit"]!!.first) + words(en["action_retry"]!!.first)
        assertTrue("blocked dialog words: $blocked", blocked <= 13)
        assertEquals(2, words(en["menu_clear"]!!.first))
        // the figures of the reticle tag are not words
        assertEquals(0, words(en["reticle_gear"]!!.first))
        assertEquals(0, words(en["reticle_far_figure"]!!.first))
    }
}
