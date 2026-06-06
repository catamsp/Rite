package com.catamsp.rite.manager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.catamsp.rite.model.Command
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommandManagerTest {

    private lateinit var context: Context
    private lateinit var manager: CommandManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = CommandManager(context)
    }

    @Test
    fun getTriggerPrefix_default_returnsQuestionMark() {
        assertEquals("?", manager.getTriggerPrefix())
    }

    @Test
    fun setTriggerPrefix_validSymbol_returnsTrue() {
        assertTrue(manager.setTriggerPrefix("#"))
        assertEquals("#", manager.getTriggerPrefix())
    }

    @Test
    fun setTriggerPrefix_letter_returnsFalse() {
        assertFalse(manager.setTriggerPrefix("a"))
    }

    @Test
    fun setTriggerPrefix_digit_returnsFalse() {
        assertFalse(manager.setTriggerPrefix("1"))
    }

    @Test
    fun setTriggerPrefix_space_returnsFalse() {
        assertFalse(manager.setTriggerPrefix(" "))
    }

    @Test
    fun setTriggerPrefix_migratesCustomCommands() {
        manager.addCustomCommand(Command("?custom", "Custom prompt", false))
        manager.setTriggerPrefix("#")
        val commands = manager.getCommands().filter { !it.isBuiltIn }
        assertEquals(1, commands.size)
        assertEquals("#custom", commands[0].trigger)
    }

    @Test
    fun addCustomCommand_commandAppearsInList() {
        manager.addCustomCommand(Command("?mycommand", "My prompt", false))
        val commands = manager.getCommands().filter { !it.isBuiltIn }
        assertEquals(1, commands.size)
        assertEquals("?mycommand", commands[0].trigger)
    }

    @Test
    fun removeCustomCommand_commandRemoved() {
        manager.addCustomCommand(Command("?temp", "Temp prompt", false))
        manager.removeCustomCommand("?temp")
        val commands = manager.getCommands().filter { !it.isBuiltIn }
        assertEquals(0, commands.size)
    }

    @Test
    fun findCommand_exactTrigger_returnsCommand() {
        val result = manager.findCommand("hello ?fix")
        assertNotNull(result)
        assertEquals("?fix", result?.trigger)
    }

    @Test
    fun findCommand_noSpaceBeforeTrigger_returnsNull() {
        val result = manager.findCommand("x?fix")
        assertNull(result)
    }

    @Test
    fun findCommand_modePrefix_returnsCommand() {
        val append = manager.findCommand("hello !fix")
        assertNotNull(append)
        assertEquals("!fix", append?.trigger)

        val prepend = manager.findCommand("hello +fix")
        assertNotNull(prepend)
        assertEquals("+fix", prepend?.trigger)
    }

    @Test
    fun findCommand_dynamicTranslate_returnsCommand() {
        val result = manager.findCommand("hello ?translate:es")
        assertNotNull(result)
        assertEquals("?translate:es", result?.trigger)
    }

    @Test
    fun findCommand_intentCommand_returnsCommand() {
        val result = manager.findCommand("?app:com.whatsapp")
        assertNotNull(result)
        assertTrue(result?.prompt?.contains("com.whatsapp") == true)
    }

    @Test
    fun importCommands_validLines_importsCorrectly() {
        val lines = listOf(
            "?greet, Greet the user warmly",
            "?farewell, Say goodbye"
        )
        val result = manager.importCommands(lines, emptySet())
        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
    }

    @Test
    fun importCommands_duplicateTrigger_skips() {
        val existing = setOf("?greet")
        val lines = listOf("?greet, Already exists")
        val result = manager.importCommands(lines, existing)
        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("?greet", result.skippedTriggers[0])
    }

    @Test
    fun importCommands_commentLine_skips() {
        val lines = listOf("# This is a comment", "?valid, Valid command")
        val result = manager.importCommands(lines, emptySet())
        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
    }

    // ── Custom command edge cases (triggerLastChars bug regression) ──

    @Test
    fun findCommand_customCommandEndingWithUncommonLetter_findsIt() {
        // Letters NOT in built-in triggers' last chars: a, b, f, g, h, j, k, q, s, u, v, w, z
        manager.addCustomCommand(Command("?web", "Open web browser", false))
        val result = manager.findCommand("search ?web")
        assertNotNull(result)
        assertEquals("?web", result?.trigger)
        assertEquals("Open web browser", result?.prompt)
    }

    @Test
    fun findCommand_customCommandEndingWithB_findsIt() {
        manager.addCustomCommand(Command("?grab", "Grab text", false))
        val result = manager.findCommand("hello ?grab")
        assertNotNull(result)
        assertEquals("?grab", result?.trigger)
    }

    @Test
    fun findCommand_customCommandEndingWithS_findsIt() {
        manager.addCustomCommand(Command("?ask", "Ask AI", false))
        val result = manager.findCommand("question ?ask")
        assertNotNull(result)
        assertEquals("?ask", result?.trigger)
    }

    @Test
    fun findCommand_customCommandEndingWithW_findsIt() {
        manager.addCustomCommand(Command("?wow", "Add wow effect", false))
        val result = manager.findCommand("text ?wow")
        assertNotNull(result)
        assertEquals("?wow", result?.trigger)
    }

    @Test
    fun findCommand_customCommandEndingWithZ_findsIt() {
        manager.addCustomCommand(Command("?jazz", "Make it jazzy", false))
        val result = manager.findCommand("text ?jazz")
        assertNotNull(result)
        assertEquals("?jazz", result?.trigger)
    }

    @Test
    fun findCommand_customIntentCommand_findsIt() {
        manager.addCustomCommand(Command("?wp", "app:com.whatsapp", false))
        val result = manager.findCommand("?wp")
        assertNotNull(result)
        assertEquals("?wp", result?.trigger)
        assertEquals("app:com.whatsapp", result?.prompt)
    }

    @Test
    fun findCommand_customTelIntentCommand_findsIt() {
        manager.addCustomCommand(Command("?call", "tel:+1234567890", false))
        val result = manager.findCommand("hello ?call")
        assertNotNull(result)
        assertEquals("?call", result?.trigger)
        assertEquals("tel:+1234567890", result?.prompt)
    }

    @Test
    fun findCommand_customUrlIntentCommand_findsIt() {
        manager.addCustomCommand(Command("?google", "https://google.com", false))
        val result = manager.findCommand("?google")
        assertNotNull(result)
        assertEquals("?google", result?.trigger)
        assertEquals("https://google.com", result?.prompt)
    }

    @Test
    fun findCommand_customCommandWithTextBefore_findsIt() {
        manager.addCustomCommand(Command("?summarize", "Summarize this text", false))
        val result = manager.findCommand("Here is my long text ?summarize")
        assertNotNull(result)
        assertEquals("?summarize", result?.trigger)
    }

    @Test
    fun invalidateCache_causesReloadFromPrefs() {
        manager.addCustomCommand(Command("?test1", "Test prompt 1", false))
        // Verify it's there
        assertNotNull(manager.findCommand("?test1"))
        // Invalidate cache
        manager.invalidateCache()
        // Should still be there (reloaded from prefs)
        assertNotNull(manager.findCommand("?test1"))
        val commands = manager.getCommands().filter { !it.isBuiltIn }
        assertEquals(1, commands.size)
        assertEquals("?test1", commands[0].trigger)
    }

    @Test
    fun addCustomCommand_afterInvalidateCache_isFound() {
        // Simulate what happens when service's cache is invalidated
        manager.invalidateCache()
        manager.addCustomCommand(Command("?newcmd", "New command", false))
        val result = manager.findCommand("?newcmd")
        assertNotNull(result)
        assertEquals("New command", result?.prompt)
    }
}
