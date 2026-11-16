package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.AccessChecker
import dev.inmo.kroles.roles.rwm.RWMRole.AccessRights
import dev.inmo.kroles.roles.rwm.RWMRole.Identifier
import dev.inmo.kroles.roles.rwm.RightsChecker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessRightsSerializationTests {
    @Test
    fun rightsPreserveThePrimitiveStringFormat() {
        val serializer = AccessRights.serializer()
        assertEquals(PrimitiveKind.STRING, serializer.descriptor.kind)
        assertEquals("dev.inmo.kroles.roles.rwm.RWMRole.AccessRights", serializer.descriptor.serialName)

        listOf("", "r", "w", "m", "rw", "wr", "rrw", "wrx", "x", "rwm", "права.\"\\\n").forEach { raw ->
            val encoded = Json.encodeToString(serializer, AccessRights(raw))
            assertEquals(Json.encodeToString(String.serializer(), raw), encoded)
            // Equal flags can have different raw strings; verify that the exact representation survives.
            assertEquals(raw, Json.decodeFromString(serializer, encoded).string)
        }
    }

    @Test
    fun rwmCheckerPreservesItsStringRightsField() = runTest {
        val checker = AccessChecker.RWM("groups", AccessRights("wrx"), Identifier("42"))
        val json = """{"prefix":"groups","accessRights":"wrx","identifier":"42"}"""

        assertEquals(Json.parseToJsonElement(json), Json.encodeToJsonElement(AccessChecker.RWM.serializer(), checker))
        val restored = Json.decodeFromString(AccessChecker.RWM.serializer(), json)
        assertEquals("wrx", restored.accessRights?.string)
        assertTrue(restored.check("groups.rw.42"))
        assertFalse(restored.check("groups.m.42"))
    }

    @Test
    fun defaultRightsCheckerPreservesThePrimitiveStringFormat() = runTest {
        val checker = RightsChecker.Default(AccessRights("wrx"))
        val serializer = RightsChecker.Default.serializer()

        assertEquals("\"wrx\"", Json.encodeToString(serializer, checker))
        val restored = Json.decodeFromString(serializer, "\"wrx\"")
        assertEquals("\"wrx\"", Json.encodeToString(serializer, restored))
        assertTrue(restored("rw"))
        assertFalse(restored("m"))
    }
}
