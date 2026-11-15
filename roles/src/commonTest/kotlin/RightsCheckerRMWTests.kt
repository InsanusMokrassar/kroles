package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.RWMRole.AccessRights
import dev.inmo.kroles.roles.rwm.RightsChecker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RightsCheckerRMWTests {
    private val requestedToGrantedRights = RWMRoleTests.availableToRequiredRights
    private val allGrantedRights = requestedToGrantedRights
        .flatMap {
            it
                .second
                .map {
                    AccessRights(it)
                }
        }
        .distinct()
    private val allRequestedRights = requestedToGrantedRights.map { it.first }

    @Test
    fun invokeWithAccessRightsWorksCorrectly() = runTest {
        allGrantedRights.forEach { grantedRights ->
            val checker = RightsChecker.RMW(grantedRights)
            allRequestedRights.forEach { rawRequestedRights ->
                val requestedRights = AccessRights(rawRequestedRights)
                assertEquals(
                    expected = requestedRights in grantedRights,
                    actual = checker(requestedRights),
                    message = "Requested rights '$requestedRights' with granted rights '$grantedRights'"
                )
            }
        }
    }
}
