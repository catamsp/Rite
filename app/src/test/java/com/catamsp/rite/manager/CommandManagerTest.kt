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
}
