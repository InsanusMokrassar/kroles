package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.RWMRole
import kotlin.test.Test
import kotlin.test.assertTrue

class RWMRoleTests {
    @Test
    fun commonRolesCheckWorksCorrectly() {
        availableToRequiredRights.forEach {
            val (rawRequestedRights, rawGrantedRights) = it
            val requestedRights = RWMRole.AccessRights(rawRequestedRights)
            rawGrantedRights.forEach { grantedRight ->
                val grantedRights = RWMRole.AccessRights(grantedRight)
                assertTrue(
                    actual = grantedRights.contains(requestedRights),
                    message = "Granted right '$grantedRight' must contain '$rawRequestedRights'"
                )
            }
        }
    }

    companion object {
        val availableToRequiredRights = listOf(
            "" to listOf(""),
            "r" to listOf("r", "rw", "rm", "rwm"),
            "w" to listOf("w", "rw", "wm", "rwm"),
            "m" to listOf("m", "rm", "wm", "rwm"),
            "rw" to listOf("rw", "rwm"),
            "rm" to listOf("rm", "rwm"),
            "wm" to listOf("wm", "rwm"),
            "rwm" to listOf("rwm"),
        )
    }
}
