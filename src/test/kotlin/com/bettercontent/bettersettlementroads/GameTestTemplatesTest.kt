package com.bettercontent.bettersettlementroads

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameTestTemplatesTest {
    @Test
    fun `class prefixed GameTest templates exist in authored resources`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/com/bettercontent/bettersettlementroads/gametest/SettlementRoadsGameTests.kt"
        ))
        val holder = assertNotNull(Regex("(?m)^object\\s+(\\w+)\\s*\\{").find(source)).groupValues[1]
        val templates = Regex("@GameTest\\(\\s*template\\s*=\\s*\"([^\"]+)\"")
            .findAll(source).map { it.groupValues[1] }.toSet()
        assertTrue(templates.isNotEmpty(), "No GameTest annotations were inspected")
        for (template in templates) {
            val name = "${holder.lowercase(Locale.ROOT)}.$template.snbt"
            val authored = Path.of("src/main/resources/gameteststructures", name)
            assertTrue(Files.isRegularFile(authored), "Missing authored GameTest template: $authored")
            assertTrue(Files.readString(authored).contains("DataVersion:"), "Invalid template: $authored")
        }
    }
}
