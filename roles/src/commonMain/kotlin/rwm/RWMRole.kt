package dev.inmo.kroles.roles.rwm

import dev.inmo.kroles.roles.BaseRole
import kotlinx.serialization.Serializable
import kotlin.js.JsName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

/**
 * Role with form "prefix.rmw.identifier", where "prefix" is an identifier of role, r - read access, w - write access,
 * m - manage access, and "identifier" is optional id for granulating of access rights.
 *
 * @sample "groups.rw.10" should give access for reading and writing in the group with id 10
 */
@Serializable
@JvmInline
value class RWMRole internal constructor(
    /**
     * Underlying [BaseRole] whose plain string is parsed into prefix, rights and identifier segments.
     */
    val role: BaseRole
) {
    /**
     * The raw rights segment (the part between the first and second dot) or null when it is absent or blank.
     */
    internal val rightsStringNullable: String?
        get() = role.plain.dropWhile { it != '.' }.removePrefix(".").takeIf { it.isNotBlank() } ?.takeWhile { it != '.' }
    /**
     * Parsed [AccessRights] built from the rights segment (empty rights when the segment is missing).
     */
    val rights
        get() = AccessRights(rightsStringNullable ?: "")
    /**
     * String form of the current [rights] (the r/w/m flags).
     */
    val rightsString: String
        get() = rights.string
    /**
     * Role prefix: the identifier part before the first dot.
     */
    val prefix: String
        get() = role.plain.takeWhile { it != '.' }
    /**
     * True when the read (r) flag is present in the rights.
     */
    val readAccess: Boolean
        get() = rights.r
    /**
     * True when the write (w) flag is present in the rights.
     */
    val writeAccess: Boolean
        get() = rights.w
    /**
     * True when the manage (m) flag is present in the rights.
     */
    val manageAccess: Boolean
        get() = rights.m
    /**
     * Optional granularity [Identifier] (the segment after the second dot) or null when absent or blank.
     */
    val identifier: Identifier?
        get() {
            var foundFirst = false
            return role.plain.dropWhile {
                when {
                    it != '.' -> true
                    foundFirst -> false
                    else -> {
                        foundFirst = true
                        true
                    }
                }
            }.removePrefix(".").takeIf { it.isNotBlank() } ?.let(::Identifier)
        }

    /**
     * Encodes the read/write/manage access flags as a string containing the r, w and/or m characters.
     *
     * @property string Raw flag string composed of the r/w/m access characters.
     */
    @Serializable
    @JvmInline
    value class AccessRights internal constructor(val string: String) : Comparable<Identifier> {
        /**
         * True when the read (r) flag is present.
         */
        val r: Boolean
            get() = string.contains(READ_ACCESS)
        /**
         * True when the write (w) flag is present.
         */
        val w: Boolean
            get() = string.contains(WRITE_ACCESS)
        /**
         * True when the manage (m) flag is present.
         */
        val m: Boolean
            get() = string.contains(MANAGE_ACCESS)
        /**
         * Builds an [AccessRights] from the individual read/write/manage flags.
         */
        constructor(
            read: Boolean = false,
            write: Boolean = false,
            manage: Boolean = false
        ) : this("${READ_ACCESS.takeIf { read } ?: ""}${WRITE_ACCESS.takeIf { write } ?: ""}${MANAGE_ACCESS.takeIf { manage } ?: ""}")

        override fun compareTo(other: Identifier): Int = string.compareTo(other.string)

        /**
         * Union of two rights: a flag is set when it is present in either operand.
         */
        operator fun plus(other: AccessRights) = AccessRights(
            read = r || other.r,
            write = w || other.w,
            manage = m || other.m,
        )

        /**
         * Intersection of two rights: a flag is set only when present in both operands.
         */
        operator fun times(other: AccessRights) = AccessRights(
            read = r && other.r,
            write = w && other.w,
            manage = m && other.m,
        )

        /**
         * Difference of two rights: keeps only the flags present here and absent in [other].
         */
        operator fun minus(other: AccessRights) = AccessRights(
            read = r && !other.r,
            write = w && !other.w,
            manage = m && !other.m,
        )

        /**
         * True when none of the read/write/manage flags is set.
         */
        fun isEmpty() = !r && !w && !m

        /**
         * Checks whether the required [rights] are satisfied by this set of flags.
         */
        operator fun contains(rights: AccessRights) = !r || rights.r && !w || rights.w && !m || rights.m
        override fun toString(): String {
            return string
        }
    }

    /**
     * Optional identifier segment used to granulate access rights to a specific entity.
     *
     * @property string Raw identifier value.
     */
    @Serializable
    @JvmInline
    value class Identifier internal constructor(val string: String) : Comparable<Identifier> {
        override fun compareTo(other: Identifier): Int = string.compareTo(other.string)

        override fun toString(): String {
            return string
        }
    }

    companion object {
        /**
         * Character marking read access in a rights string.
         */
        val READ_ACCESS = "r"
        /**
         * Character marking manage access in a rights string.
         */
        val MANAGE_ACCESS = "m"
        /**
         * Character marking write access in a rights string.
         */
        val WRITE_ACCESS = "w"

        /**
         * Checks that [role] is an RWM role with the given [requiredPrefix], an identifier that is either absent or
         * equal to [identifier], and rights accepted by [accessChecker]. Returns false when [role] is not an RWM role.
         *
         * @return True when all conditions are satisfied.
         */
        suspend fun checkRights(
            role: BaseRole,
            requiredPrefix: String,
            identifier: Identifier? = null,
            accessChecker: RightsChecker
        ) = role.rwmRoleOrNull()?.let {
            val rightsString = it.rightsStringNullable ?: return@let false
            it.prefix == requiredPrefix
                    && (it.identifier == null || it.identifier == identifier)
                    && accessChecker(rightsString)
        } ?: false

        /**
         * Convenience overload of checkRights that builds the checker from a required [AccessRights] value.
         */
        suspend fun checkRights(
            role: BaseRole,
            requiredPrefix: String,
            requiredRight: AccessRights,
            identifier: Identifier? = null
        ) = checkRights(
            role,
            requiredPrefix,
            identifier,
            RightsChecker(requiredRight)
        )

        /**
         * Convenience overload of checkRights that builds the required rights from the individual read/write/manage flags.
         */
        suspend fun checkRights(
            role: BaseRole,
            requiredPrefix: String,
            read: Boolean = false,
            manage: Boolean = false,
            write: Boolean = false,
            identifier: Identifier? = null
        ) = checkRights(
            role,
            requiredPrefix,
            AccessRights(read, manage, write),
            identifier
        )

        /**
         * Parses [role] into an [RWMRole] or returns null when it does not have a valid rights segment.
         */
        operator fun invoke(
            role: String
        ) = BaseRole(role).rwmRoleOrNull()


        /**
         * Builds an [RWMRole] from a [prefix], [rights] and an optional [identifier].
         */
        operator fun invoke(
            prefix: String,
            rights: AccessRights,
            identifier: Identifier? = null
        ): RWMRole = RWMRole(
            BaseRole(
                "$prefix.${rights}${identifier ?.let { ".$it" } ?: ""}"
            )
        )

        /**
         * Builds an [RWMRole] from a [prefix], [rights] and a string [identifier].
         */
        @JvmName("invokeWithStringIdentifier")
        operator fun invoke(
            prefix: String,
            rights: AccessRights,
            identifier: String
        ): RWMRole = invoke(
            prefix,
            rights,
            Identifier(identifier)
        )

        /**
         * Builds an [RWMRole] from a [prefix], individual read/write/manage flags and an optional [identifier].
         */
        operator fun invoke(
            prefix: String,
            read: Boolean,
            manage: Boolean,
            write: Boolean,
            identifier: Identifier? = null
        ): RWMRole = invoke(
            prefix, AccessRights(read, manage, write), identifier
        )

        /**
         * Builds an [RWMRole] from a [prefix], individual read/write/manage flags and a string [identifier].
         */
        @JvmName("invokeWithStringIdentifier")
        operator fun invoke(
            prefix: String,
            read: Boolean,
            manage: Boolean,
            write: Boolean,
            identifier: String
        ): RWMRole = invoke(
            prefix, read, manage, write, Identifier(identifier)
        )
    }
}
