package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.RWMRole.AccessRights
import dev.inmo.kroles.roles.rwm.RWMRole.Identifier
import dev.inmo.kroles.roles.rwm.RightsChecker
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
    fun checkRightsWithAccessRightsWorksCorrectly() = runTest {
        allRequiredRights.forEach { requiredRights ->
            allAvailableRights.forEach { availableRights ->
                val role = RWMRole(prefix, availableRights, identifier).role
                assertEquals(
                    expected = availableRights in requiredRights,
                    actual = RWMRole.checkRights(
                        roleToCheck = role,
                        requiredPrefix = prefix,
                        requiredRight = requiredRights,
                        identifier = identifier
                    ),
                    message = "Requested rights '$availableRights' with granted rights '$requiredRights'"
                )
            }
        }
    }

    @Test
    fun checkRightsWithBooleanFlagsWorksCorrectly() = runTest {
        allRequiredRights.forEach { requiredRights ->
            allAvailableRights.forEach { availableRights ->
                val role = RWMRole(prefix, availableRights, identifier).role
                assertEquals(
                    expected = availableRights in requiredRights,
                    actual = RWMRole.checkRights(
                        roleToCheck = role,
                        requiredPrefix = prefix,
                        read = requiredRights.r,
                        write = requiredRights.w,
                        manage = requiredRights.m,
                        identifier = identifier
                    ),
                    message = "Requested rights '$availableRights' with granted rights '$requiredRights'"
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
        assertFalse(RWMRole.checkRights(role, prefix, accessChecker = checker))
    }

    @Test
    fun checkRightsAcceptsAnyIdentifierForGlobalRole() = runTest {
        val role = RWMRole(prefix, AccessRights("rwm")).role
        val checker = RightsChecker { true }

        assertTrue(RWMRole.checkRights(role, prefix, identifier, checker))
        assertTrue(RWMRole.checkRights(role, prefix, Identifier("43"), checker))
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
