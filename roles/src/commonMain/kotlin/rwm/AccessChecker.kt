package dev.inmo.kroles.roles.rwm

import dev.inmo.kroles.roles.BaseRole
import kotlinx.serialization.Serializable

/**
 * Suspended predicate that decides whether a role passes an access check.
 *
 * Checkers can be combined using logical OR ([plus]), AND ([times]) and negation ([not]).
 */
fun interface AccessChecker {
    /**
     * Returns true when [role] passes this checker.
     */
    suspend fun check(role: BaseRole): Boolean
    /**
     * Checks the underlying [BaseRole] of [role] by default.
     */
    suspend fun check(role: RWMRole): Boolean = check(role.role)
    /**
     * Wraps the raw [role] string in a [BaseRole] and checks it by default.
     */
    suspend fun check(role: String): Boolean = check(BaseRole(role))

    /**
     * Checker backed by callbacks for each supported role representation.
     *
     * @param byRole Callback used for [BaseRole] checks.
     * @param byRWMRole Callback used for [RWMRole] checks; defaults to passing its underlying role to [byRole].
     * @param byStringRole Callback used for string checks; defaults to wrapping the string in a [BaseRole]
     * and passing it to [byRole].
     */
    class Callback(
        private val byRole: suspend (role: BaseRole) -> Boolean,
        private val byRWMRole: suspend (RWMRole) -> Boolean = { byRole(it.role) },
        private val byStringRole: suspend (String) -> Boolean = { byRole(BaseRole(it)) }
    ) : AccessChecker {
        override suspend fun check(role: BaseRole): Boolean {
            return byRole(role)
        }

        override suspend fun check(role: RWMRole): Boolean {
            return byRWMRole(role)
        }

        override suspend fun check(role: String): Boolean {
            return byStringRole(role)
        }
    }

    /**
     * Checks an RWM role's prefix, access rights and optional identifier.
     *
     * Base roles and strings that cannot be parsed as RWM roles fail the check.
     *
     * @property prefix Exact prefix to match. A null prefix does not match any RWM role.
     * @property accessRights Rights used to build a [RightsChecker]; null skips the rights check.
     * @property identifier Exact identifier to match; null accepts any identifier, including an absent one.
     */
    @Serializable
    data class RWM(
        val prefix: String? = null,
        val accessRights: RWMRole.AccessRights? = null,
        val identifier: RWMRole.Identifier? = null,
    ) : AccessChecker {
        private val rightsChecker = accessRights ?.let { RightsChecker(it) }
        override suspend fun check(role: BaseRole): Boolean {
            return check(role.rwmRoleOrNull() ?: return false)
        }

        override suspend fun check(role: RWMRole): Boolean {
            return (prefix == role.prefix) && (rightsChecker ?.invoke(role.rights) ?: true) && (identifier == role.identifier || identifier == null)
        }

        override suspend fun check(role: String): Boolean {
            return check(BaseRole(role).rwmRoleOrNull() ?: return false)
        }

        companion object {
            /**
             * Creates a checker for [prefix] with a [RightsChecker] built from [read], [write] and [manage].
             * A null [identifier] accepts any identifier, including an absent one.
             */
            operator fun invoke(
                prefix: String,
                read: Boolean = false,
                write: Boolean = false,
                manage: Boolean = false,
                identifier: RWMRole.Identifier? = null
            ) = RWM(
                prefix = prefix,
                accessRights = RWMRole.AccessRights(
                    read = read,
                    write = write,
                    manage = manage
                ),
                identifier = identifier
            )
        }
    }

    /**
     * Adapts [rightsChecker] to check an RWM role's access rights, ignoring its prefix and identifier.
     *
     * Passes [RWMRole.rights] to the [RWMRole.AccessRights] overload of [RightsChecker.invoke].
     * Base roles and strings that cannot be parsed as RWM roles return false without invoking [rightsChecker].
     * An [RWMRole] input is checked directly, without reparsing its underlying role.
     *
     * @param rightsChecker Predicate that determines whether the role's rights are accepted.
     */
    class RWMRightsOnly(
        private val rightsChecker: RightsChecker
    ) : AccessChecker {
        /**
         * Checks [role]'s rights directly, including empty rights.
         */
        override suspend fun check(role: RWMRole): Boolean = rightsChecker(role.rights)
        /**
         * Parses [role] as an RWM role and checks its rights, or returns false when parsing fails.
         */
        override suspend fun check(role: BaseRole): Boolean = role.rwmRoleOrNull() ?.let { check(it) } == true
        /**
         * Wraps [role] in a [BaseRole] and applies the same parsing and rights check.
         */
        override suspend fun check(role: String): Boolean = check(BaseRole(role))
    }

    /**
     * Combines checkers with logical OR, checking [other] only when this checker fails.
     */
    operator fun plus(other: AccessChecker): AccessChecker {
        val first = this
        return Callback(
            byRole = { first.check(it) || other.check(it) },
            byRWMRole = { first.check(it) || other.check(it) },
            byStringRole = { first.check(it) || other.check(it) },
        )
    }

    /**
     * Combines checkers with logical AND, checking [other] only when this checker passes.
     */
    operator fun times(other: AccessChecker): AccessChecker {
        val first = this
        return Callback(
            byRole = { first.check(it) && other.check(it) },
            byRWMRole = { first.check(it) && other.check(it) },
            byStringRole = { first.check(it) && other.check(it) },
        )
    }

    /**
     * Returns a checker that passes when this checker fails.
     */
    operator fun not(): AccessChecker {
        val first = this
        return Callback(
            byRole = { !first.check(it) },
            byRWMRole = { !first.check(it) },
            byStringRole = { !first.check(it) },
        )
    }

    /**
     * Negates this checker, just like [not].
     */
    operator fun unaryMinus(): AccessChecker = not()

    /**
     * Combines this checker with the negation of [other] using logical OR: `this || !other`.
     * Checks [other] only when this checker fails.
     */
    operator fun minus(other: AccessChecker): AccessChecker {
        return this + (-other)
    }
}
