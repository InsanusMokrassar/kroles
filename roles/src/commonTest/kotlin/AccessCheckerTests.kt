package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.AccessChecker
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.RWMRole.AccessRights
import dev.inmo.kroles.roles.rwm.RWMRole.Identifier
import dev.inmo.kroles.roles.rwm.RightsChecker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class AccessCheckerTests {
    private val role = RWMRole("groups", AccessRights("rw"), Identifier("42"))
    private val rights = listOf("", "r", "w", "m", "rw", "rm", "wm", "rwm")

    private suspend fun assertChecks(expected: Boolean, checker: AccessChecker, checked: RWMRole = role) {
        assertEquals(expected, checker.check(checked))
        assertEquals(expected, checker.check(checked.role))
        assertEquals(expected, checker.check(checked.role.plain))
    }

    @Test
    fun defaultOverloadsAndCallbacksDelegateToBaseRole() = runTest {
        val received = mutableListOf<BaseRole>()
        val callback: suspend (BaseRole) -> Boolean = { received.add(it); true }
        listOf(AccessChecker(callback), AccessChecker.Callback(callback)).forEach { checker ->
            received.clear()
            assertChecks(true, checker)
            assertEquals(List(3) { role.role }, received)
        }
    }

    @Test
    fun callbacksHandleEachRepresentationIndependently() = runTest {
        val received = mutableListOf<Any>()
        val checker = AccessChecker.Callback(
            byRole = { received.add(it); false },
            byRWMRole = { received.add(it); true },
            byStringRole = { received.add(it); false }
        )
        assertFalse(checker.check(role.role))
        assertTrue(checker.check(role))
        assertFalse(checker.check(role.role.plain))
        assertEquals(listOf(role.role, role, role.role.plain), received)
    }

    @Test
    fun rwmConstructorsCheckEveryRightsCombination() = runTest {
        rights.forEach { configured ->
            val flags = AccessRights(configured)
            val checkers = listOf(
                AccessChecker.RWM("groups", flags),
                AccessChecker.RWM("groups", flags.r, flags.w, flags.m)
            )
            rights.forEach { checked ->
                val checkedRole = RWMRole("groups", AccessRights(checked))
                val expected = checked.all { it in configured }
                checkers.forEach { checker ->
                    // Empty rights cannot be parsed back from a BaseRole or string.
                    assertEquals(expected, checker.check(checkedRole), "$configured / $checked")
                    assertEquals(expected && checked.isNotEmpty(), checker.check(checkedRole.role))
                    assertEquals(expected && checked.isNotEmpty(), checker.check(checkedRole.role.plain))
                }
            }
        }
    }

    @Test
    fun rwmMatchesPrefixAndOptionalIdentifier() = runTest {
        assertChecks(true, AccessChecker.RWM(prefix = "groups"))
        assertChecks(false, AccessChecker.RWM(prefix = "users"))
        assertChecks(false, AccessChecker.RWM())
        assertChecks(true, AccessChecker.RWM(prefix = "groups", identifier = Identifier("42")))
        assertChecks(false, AccessChecker.RWM(prefix = "groups", identifier = Identifier("43")))
        val globalRole = RWMRole("groups", AccessRights("rw"))
        assertChecks(true, AccessChecker.RWM(prefix = "groups"), globalRole)
        assertChecks(false, AccessChecker.RWM(prefix = "groups", identifier = Identifier("42")), globalRole)
    }

    @Test
    fun rwmRejectsUnparseableRoles() = runTest {
        val checker = AccessChecker.RWM(prefix = "groups")
        listOf("groups", "groups.").forEach {
            assertFalse(checker.check(it))
            assertFalse(checker.check(BaseRole(it)))
        }
    }

    @Test
    fun rwmRightsOnlyChecksEveryRightsCombination() = runTest {
        rights.forEach { available ->
            val checker = AccessChecker.RWMRightsOnly(RightsChecker(available))
            rights.forEach { required ->
                val checkedRole = RWMRole("groups", AccessRights(required), Identifier("42"))
                assertChecks(required.all { it in available }, checker, checkedRole)
            }
        }
    }

    @Test
    fun rwmRightsOnlyIgnoresPrefixAndIdentifier() = runTest {
        val checker = AccessChecker.RWMRightsOnly(RightsChecker("r"))
        listOf("groups", "users").forEach { prefix ->
            listOf(null, Identifier("42"), Identifier("43")).forEach { identifier ->
                assertChecks(true, checker, RWMRole(prefix, AccessRights("r"), identifier))
                assertChecks(false, checker, RWMRole(prefix, AccessRights("rw"), identifier))
            }
        }
    }

    @Test
    fun rwmRightsOnlyDelegatesToAccessRightsOverload() = runTest {
        val checkedRole = RWMRole("groups", AccessRights("wrx"), Identifier("42"))
        listOf(false, true).forEach { expected ->
            val received = mutableListOf<AccessRights>()
            val checker = AccessChecker.RWMRightsOnly(object : RightsChecker {
                override suspend fun invoke(rights: AccessRights): Boolean {
                    received.add(rights)
                    return expected
                }

                override suspend fun invoke(rights: String): Boolean = fail("Expected the AccessRights overload")
            })

            assertChecks(expected, checker, checkedRole)
            assertEquals(List(3) { checkedRole.rights }, received)
        }
    }

    @Test
    fun rwmRightsOnlyRejectsUnparseableRolesWithoutInvokingChecker() = runTest {
        val checker = AccessChecker.RWMRightsOnly(RightsChecker { fail("No parsed rights to check") })

        listOf("groups", "groups.", "groups.   ").forEach {
            assertFalse(checker.check(it))
            assertFalse(checker.check(BaseRole(it)))
        }
    }

    @Test
    fun rwmRightsOnlyChecksTypedRolesWithoutReparsing() = runTest {
        val checker = AccessChecker.RWMRightsOnly(RightsChecker(""))
        val emptyRole = RWMRole("groups", AccessRights(""))

        assertTrue(checker.check(emptyRole))
        assertFalse(checker.check(emptyRole.role))
        assertFalse(checker.check(emptyRole.role.plain))
    }

    @Test
    fun operatorsPreserveOverloadsAndShortCircuit() = runTest {
        listOf(false, true).forEach { first ->
            listOf(false, true).forEach { second ->
                for (representation in 0..2) {
                    val calls = mutableListOf<String>()
                    fun checker(name: String, result: Boolean) = AccessChecker.Callback(
                        byRole = { calls.add("$name:base"); result },
                        byRWMRole = { calls.add("$name:rwm"); result },
                        byStringRole = { calls.add("$name:string"); result }
                    )
                    val left = checker("left", first)
                    val right = checker("right", second)
                    val suffix = listOf("base", "rwm", "string")[representation]
                    suspend fun verify(combined: AccessChecker, expected: Boolean, visitsRight: Boolean) {
                        calls.clear()
                        val actual = when (representation) {
                            0 -> combined.check(role.role)
                            1 -> combined.check(role)
                            else -> combined.check(role.role.plain)
                        }
                        assertEquals(expected, actual)
                        assertEquals(
                            if (visitsRight) listOf("left:$suffix", "right:$suffix") else listOf("left:$suffix"),
                            calls
                        )
                    }
                    verify(left + right, first || second, !first)
                    verify(left * right, first && second, first)
                    verify(!left, !first, false)
                    verify(-left, !first, false)
                    verify(left - right, first || !second, !first)
                }
            }
        }
    }
}
