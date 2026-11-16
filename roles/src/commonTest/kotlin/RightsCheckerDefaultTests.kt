package dev.inmo.kroles.roles

import dev.inmo.kroles.roles.rwm.RWMRole.AccessRights
import dev.inmo.kroles.roles.rwm.RightsChecker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RightsCheckerDefaultTests {
    private val rights = listOf("", "r", "w", "m", "rw", "rm", "wm", "rwm")

    @Test
    fun constructorsAndOverloadsCheckEveryRightsCombination() = runTest {
        rights.forEach { granted ->
            val flags = AccessRights(granted)
            val checkers = listOf(
                RightsChecker.Default(flags),
                RightsChecker(flags),
                RightsChecker(granted),
                RightsChecker(flags.r, flags.w, flags.m)
            )
            rights.forEach { requested ->
                val expected = requested.all { it in granted }
                checkers.forEach { checker ->
                    assertEquals(expected, checker(AccessRights(requested)), "$granted / $requested")
                    assertEquals(expected, checker(requested), "$granted / $requested")
                }
            }
        }
    }

    @Test
    fun operatorsCombineResultsAndShortCircuit() = runTest {
        listOf(false, true).forEach { first ->
            listOf(false, true).forEach { second ->
                val calls = mutableListOf<String>()
                val left = RightsChecker { calls.add("left"); first }
                val right = RightsChecker { calls.add("right"); second }

                assertEquals(first || second, (left + right)("r"))
                assertEquals(if (first) listOf("left") else listOf("left", "right"), calls)
                calls.clear()
                assertEquals(first && second, (left * right)("r"))
                assertEquals(if (first) listOf("left", "right") else listOf("left"), calls)
                calls.clear()
                assertEquals(!first, (!left)("r"))
                assertEquals(listOf("left"), calls)
            }
        }
    }
}
