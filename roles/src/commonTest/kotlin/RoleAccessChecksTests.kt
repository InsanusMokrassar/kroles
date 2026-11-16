package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.AccessChecker
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.RWMRole.AccessRights
import dev.inmo.kroles.roles.rwm.RWMRole.Identifier
import dev.inmo.kroles.roles.rwm.RightsChecker
import dev.inmo.kroles.roles.rwm.isAccessAllowed
import dev.inmo.kroles.roles.rwm.isIdentifierAllowed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class RoleAccessChecksTests {
    @Test
    fun accessCheckerReceivesWholeRolesAndStopsAfterFirstMatch() = runTest {
        val visited = mutableListOf<BaseRole>()
        val checker = AccessChecker {
            visited.add(it)
            it == BaseRole("editor")
        }

        assertFalse(emptyList<BaseRole>().isAccessAllowed(checker))
        assertTrue(visited.isEmpty())
        assertTrue(listOf(BaseRole("reader"), BaseRole("editor"), BaseRole("owner")).isAccessAllowed(checker))
        assertEquals(listOf(BaseRole("reader"), BaseRole("editor")), visited)
        assertFalse(listOf(BaseRole("reader")).isAccessAllowed(checker))
    }

    @Test
    fun stringAndFlagOverloadsAgreeWithAccessCheckerForEveryRightsCombination() = runTest {
        val rights = listOf("", "r", "w", "m", "rw", "rm", "wm", "rwm")
        val identifier = Identifier("42")
        rights.forEach { available ->
            val flags = AccessRights(available)
            rights.forEach { required ->
                val roles = listOf(RWMRole("groups", AccessRights(required), identifier).role)
                val expected = required.all { it in available }
                val message = "Available '$available', required '$required'"

                assertEquals(expected, roles.isAccessAllowed(AccessChecker.RWM("groups", flags, identifier)), message)
                assertEquals(expected, roles.isIdentifierAllowed("groups", identifier, available), message)
                assertEquals(expected, roles.isIdentifierAllowed("groups", identifier, flags.r, flags.w, flags.m), message)
                assertEquals(expected, roles.isAccessAllowed("groups", available), message)
                assertEquals(expected, roles.isAccessAllowed("groups", flags.r, flags.w, flags.m), message)
            }
        }
    }

    @Test
    fun legacyRightsCheckerKeepsStringDispatchAndRunsOnlyAfterScopeMatches() = runTest {
        val visited = mutableListOf<String>()
        val checker = object : RightsChecker {
            override suspend fun invoke(rights: AccessRights): Boolean = fail("Expected the String overload")
            override suspend fun invoke(rights: String): Boolean {
                visited.add(rights)
                return rights == "wr"
            }
        }
        val roles = listOf(
            "plain", "groups.", "users.r.42", "groups.r", "groups.r.43",
            "groups.w.42", "groups.wr.42", "groups.m.42"
        ).map { BaseRole(it) }

        assertTrue(roles.isIdentifierAllowed("groups", Identifier("42"), checker))
        assertEquals(listOf("w", "wr"), visited)
        visited.clear()
        assertTrue(roles.isAccessAllowed("groups", checker))
        assertEquals(listOf("r", "r", "w", "wr"), visited)
        visited.clear()
        assertFalse(roles.isIdentifierAllowed("groups", Identifier("44"), checker))
        assertTrue(visited.isEmpty())
    }
}
