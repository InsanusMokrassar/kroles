package dev.inmo.kroles.roles.rwm

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Functional abstraction that decides whether a given access-rights value satisfies some requirement.
 */
fun interface RightsChecker {
    /**
     * Checks whether the given [rights] satisfy this checker.
     */
    suspend operator fun invoke(rights: RWMRole.AccessRights): Boolean
    /**
     * Checks the rights represented by a raw [rights] string by parsing it into access rights first.
     */
    suspend operator fun invoke(rights: String): Boolean = invoke(RWMRole.AccessRights(rights))

    /**
     * Combines two checkers with a logical OR: the result passes when either checker passes.
     */
    operator fun plus(other: RightsChecker): RightsChecker {
        val first = this
        return RightsChecker { first(it) || other(it) }
    }

    /**
     * Combines two checkers with a logical AND: the result passes only when both checkers pass.
     */
    operator fun times(other: RightsChecker): RightsChecker {
        val first = this
        return RightsChecker { first(it) && other(it) }
    }

    /**
     * Negates this checker: the result passes when this checker does not.
     */
    operator fun not(): RightsChecker {
        val first = this
        return RightsChecker { !first(it) }
    }

    /**
     * Default checker that passes when the required [rights] are contained in the checked rights.
     *
     * @property rights The required access rights this checker is built around.
     */
    @Serializable
    @JvmInline
    value class RMW(private val rights: RWMRole.AccessRights) : RightsChecker {
        override suspend fun invoke(rights: RWMRole.AccessRights): Boolean = rights in this.rights
        override suspend fun invoke(rights: String): Boolean = invoke(RWMRole.AccessRights(rights))
    }

    companion object {
        /**
         * Creates a [RMW] checker for the given required [rights].
         */
        operator fun invoke(rights: RWMRole.AccessRights) = RMW(rights)
        /**
         * Creates a [RMW] checker from a raw rights [rights] string.
         */
        operator fun invoke(rights: String) = invoke(RWMRole.AccessRights(rights))
        /**
         * Creates a [RMW] checker from the individual read/write/manage flags.
         */
        operator fun invoke(read: Boolean, write: Boolean, manage: Boolean) = RMW(
            RWMRole.AccessRights(
                read,
                write,
                manage
            )
        )
    }
}
