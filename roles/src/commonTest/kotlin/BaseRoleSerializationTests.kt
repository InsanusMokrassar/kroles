package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.RWMRole
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class BaseRoleSerializationTests {
    @Test
    fun rolesPreserveThePrimitiveStringFormat() {
        assertEquals(PrimitiveKind.STRING, BaseRole.serializer().descriptor.kind)
        listOf("", "groups.rw.42", "роль.\"\\\n").forEach { plain ->
            val encoded = Json.encodeToString(BaseRole.serializer(), BaseRole(plain))
            assertEquals(Json.encodeToString(String.serializer(), plain), encoded)
            assertEquals(BaseRole(plain), Json.decodeFromString(BaseRole.serializer(), encoded))
        }
        val role = requireNotNull(RWMRole("groups.rw.42"))
        assertEquals("\"groups.rw.42\"", Json.encodeToString(RWMRole.serializer(), role))
        assertEquals(role, Json.decodeFromString(RWMRole.serializer(), "\"groups.rw.42\""))
    }
}
