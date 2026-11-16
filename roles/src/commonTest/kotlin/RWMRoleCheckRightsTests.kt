package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.RWMRole.AccessRights
import dev.inmo.kroles.roles.rwm.RWMRole.Identifier
import dev.inmo.kroles.roles.rwm.RightsChecker
import dev.inmo.kroles.roles.rwm.AccessChecker
import dev.inmo.kroles.roles.rwm.isAccessAllowed
import dev.inmo.kroles.roles.rwm.isIdentifierAllowed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RWMRoleCheckRightsTests {
    private val availableToRequiredRights = RWMRoleTests.availableToRequiredRights
    private val allRequiredRights = availableToRequiredRights
        .flatMap { it.second }
        .map { AccessRights(it) }
        .distinct()
    private val allAvailableRights = availableToRequiredRights.map { AccessRights(it.first) }
    private val prefix = "groups"
    private val identifier = Identifier("42")

    @Suppress("DEPRECATION")
    @Test
    fun legacyOverloadsAndListHelpersAgreeWithAccessChecker() = runTest {
        listOf("groups.r", "groups.r.42", "groups.r.43", "groups.rw.42", "users.r.42", "groups.").forEach { raw ->
            val role = BaseRole(raw)
            listOf(null, identifier, Identifier("43")).forEach { requestedIdentifier ->
                val granted = AccessRights(read = true)
                val expected = AccessChecker.RWM(prefix, granted, requestedIdentifier).check(role)
                assertEquals(expected, RWMRole.checkRights(role, prefix, requestedIdentifier, RightsChecker(granted)))
                assertEquals(expected, RWMRole.checkRights(role, prefix, granted, requestedIdentifier))
                assertEquals(expected, RWMRole.checkRights(role, prefix, read = true, identifier = requestedIdentifier))
                assertEquals(expected, if (requestedIdentifier == null) {
                    listOf(role).isAccessAllowed(prefix, read = true)
                } else {
                    listOf(role).isIdentifierAllowed(prefix, requestedIdentifier, read = true)
                })
            }
        }
    }

    @Test
    fun checkRightsWithAccessCheckerWorksCorrectly() = runTest {
        allRequiredRights.forEach { grantedRights ->
            allAvailableRights.forEach { requestedRights ->
                val role = RWMRole(prefix, requestedRights, identifier).role
                assertEquals(
                    expected = requestedRights in grantedRights,
                    actual = RWMRole.checkRights(
                        roleToCheck = role,
                        requiredPrefix = prefix,
                        identifier = identifier,
                        accessChecker = RightsChecker { rights ->
                            assertEquals(requestedRights, rights)
                            rights in grantedRights
                        }
                    ),
                    message = "Requested rights '$requestedRights' with granted rights '$grantedRights'"
                )
            }
        }
    }

    @Test
    fun checkRightsRequiresMatchingPrefix() = runTest {
        val role = RWMRole(prefix, AccessRights("rwm")).role
        val checker = RightsChecker { true }

        assertTrue(RWMRole.checkRights(role, prefix, accessChecker = checker))
        assertFalse(RWMRole.checkRights(role, "users", accessChecker = checker))
    }

    @Test
    fun checkRightsRequiresMatchingIdentifierForScopedRole() = runTest {
        val role = RWMRole(prefix, AccessRights("rwm"), identifier).role
        val checker = RightsChecker { true }

        assertTrue(RWMRole.checkRights(role, prefix, identifier, checker))
        assertFalse(RWMRole.checkRights(role, prefix, Identifier("43"), checker))
        assertTrue(RWMRole.checkRights(role, prefix, accessChecker = checker))
    }

    @Test
    fun checkRightsRequiresUnscopedRequestForGlobalRole() = runTest {
        val role = RWMRole(prefix, AccessRights("rwm")).role
        val checker = RightsChecker { true }

        assertFalse(RWMRole.checkRights(role, prefix, identifier, checker))
        assertFalse(RWMRole.checkRights(role, prefix, Identifier("43"), checker))
        assertTrue(RWMRole.checkRights(role, prefix, accessChecker = checker))
    }

    @Test
    fun checkRightsRejectsRolesWithoutRightsSegment() = runTest {
        listOf("groups", "groups.").forEach { rawRole ->
            assertFalse(
                RWMRole.checkRights(
                    roleToCheck = BaseRole(rawRole),
                    requiredPrefix = prefix,
                    accessChecker = RightsChecker { true }
                ),
                message = "Role '$rawRole' must not pass the rights check"
            )
        }
    }
}
