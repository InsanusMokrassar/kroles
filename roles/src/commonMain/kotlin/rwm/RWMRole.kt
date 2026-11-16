package dev.inmo.kroles.roles.rwm

import dev.inmo.kroles.roles.BaseRole
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

/**
 * Role represented as `prefix.rights[.identifier]`.
 *
 * The [prefix] identifies the role category, and [rights] use `r` for read, `w` for write and `m` for manage
 * access. These flags are independent; manage access does not imply read or write access.
 * The optional [identifier] scopes the role to an entity and includes everything after the second dot.
 * For example, `groups.rw.10` represents read and write access to the group with identifier `10`.
 *
 * Wrapping a [BaseRole] does not validate or normalize its contents.
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
     * Raw text between the first and second dots, or the remaining text when there is no second dot.
     * Returns null when the text after the first dot is absent or blank. If that text starts with another
     * dot, returns an empty string instead.
     */
    internal val rightsStringNullable: String?
        get() = role.plain.dropWhile { it != '.' }.removePrefix(".").takeIf { it.isNotBlank() } ?.takeWhile { it != '.' }
    /**
     * Rights backed by the raw rights segment, or an empty string when it is missing.
     * Only lowercase `r`, `w` and `m` affect the access flags; other characters are preserved.
     */
    val rights
        get() = AccessRights(rightsStringNullable ?: "")
    /**
     * Raw string backing [rights], without filtering, reordering or deduplicating characters.
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
     * Entity identifier containing everything after the second dot, including any further dots.
     * Returns null when that text is absent or blank.
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
     * Read/write/manage flags backed by a string. Each flag is set when its lowercase character occurs
     * anywhere in [string]. Character order, repetitions and unrecognized characters do not affect the flags.
     * For example, `rw`, `wr`, `rrw` and `wrx` all enable read and write access without manage access.
     *
     * Equality and hash codes use the access flags, so equivalent flag sets compare equal with `==`.
     * The original string is preserved and serialized as a primitive string without normalization.
     *
     * @property string Raw rights text, preserved as supplied.
     */
    @Serializable(with = AccessRights.Serializer::class)
    @ExposedCopyVisibility
    data class AccessRights internal constructor(val string: String) : Comparable<Identifier> {
        /**
         * Serializes rights as their original string, preserving character order and unrecognized characters.
         */
        object Serializer : KSerializer<AccessRights> {
            override val descriptor = PrimitiveSerialDescriptor("dev.inmo.kroles.roles.rwm.RWMRole.AccessRights", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: AccessRights) = encoder.encodeString(value.string)

            override fun deserialize(decoder: Decoder): AccessRights = AccessRights(decoder.decodeString())
        }

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
         * Builds rights from independent flags, writing enabled characters once each in `rwm` order.
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
         * Returns true when this rights set grants every access flag requested by [requested].
         * Compares flags only, ignoring character order, repetitions and unrecognized characters.
         *
         * @param requested The access rights that must be present in this set.
         */
        infix operator fun contains(requested: AccessRights) = (requested.r == false || r) && (!requested.w || w) && (!requested.m || m)
        override fun toString(): String {
            return string
        }

        override fun hashCode(): Int {
            return (if (r) 1 else 0) + (if (w) 2 else 0) + (if (m) 4 else 0)
        }

        override fun equals(other: Any?): Boolean {
            return super.equals(other) || (other is AccessRights && other.r == r && other.w == w && other.m == m)
        }
    }

    /**
     * Identifier used to scope access rights to a specific entity.
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
        const val READ_ACCESS = "r"
        /**
         * Character marking manage access in a rights string.
         */
        const val MANAGE_ACCESS = "m"
        /**
         * Character marking write access in a rights string.
         */
        const val WRITE_ACCESS = "w"

        /**
         * Checks [requiredPrefix] and [identifier] using [AccessChecker.RWM], then checks the role's rights
         * with [accessChecker]. A null [identifier] accepts any role identifier; otherwise an exact match
         * is required, including rejection of roles without an identifier. Unparseable roles return false.
         *
         * @return True when all conditions are satisfied.
         */
        @Deprecated("Use AccessChecker instead")
        suspend fun checkRights(
            roleToCheck: BaseRole,
            requiredPrefix: String,
            identifier: Identifier? = null,
            accessChecker: RightsChecker
        ) = roleToCheck.rwmRoleOrNull()?.let {
            val rightsString = it.rightsStringNullable ?: return@let false
            AccessChecker.RWM(prefix = requiredPrefix, identifier = identifier).check(it)
                    && accessChecker(rightsString)
        } ?: false

        /**
         * Delegates to [AccessChecker.RWM] using [requiredPrefix], [requiredRight] and [identifier].
         *
         * A null [identifier] accepts any role identifier. A non-null identifier requires an exact match,
         * so a role without an identifier fails that check. The role's flags must be a subset of
         * [requiredRight], as evaluated by [RightsChecker.Default].
         *
         * @see AccessChecker.RWM
         */
        @Deprecated(
            "Use `AccessChecker` instead",
            ReplaceWith(
                "AccessChecker.RWM(requiredPrefix, requiredRight, identifier).check(roleToCheck)",
                "dev.inmo.kroles.roles.rwm.AccessChecker"
            )
        )
        suspend fun checkRights(
            roleToCheck: BaseRole,
            requiredPrefix: String,
            requiredRight: AccessRights,
            identifier: Identifier? = null
        ) = AccessChecker.RWM(
            prefix = requiredPrefix,
            accessRights = requiredRight,
            identifier = identifier
        ).check(roleToCheck)

        /**
         * Delegates to [AccessChecker.RWM] using [requiredPrefix] and rights built from [read], [write] and [manage].
         *
         * A null [identifier] accepts any role identifier; otherwise the identifier must match exactly.
         * The role's flags must be a subset of the supplied flags, as evaluated by [RightsChecker.Default].
         *
         * @see AccessChecker.RWM
         */
        @Deprecated(
            "Use `AccessChecker` instead",
            ReplaceWith(
                "AccessChecker.RWM(requiredPrefix, read, write, manage, identifier).check(roleToCheck)",
                "dev.inmo.kroles.roles.rwm.AccessChecker"
            )
        )
        suspend fun checkRights(
            roleToCheck: BaseRole,
            requiredPrefix: String,
            read: Boolean = false,
            write: Boolean = false,
            manage: Boolean = false,
            identifier: Identifier? = null
        ) = AccessChecker.RWM(
            prefix = requiredPrefix,
            read = read,
            write = write,
            manage = manage,
            identifier = identifier
        ).check(roleToCheck)

        /**
         * Wraps [role] as an [RWMRole], or returns null when [rightsStringNullable] is null.
         * Does not validate the prefix, identifier or characters in the rights segment.
         */
        operator fun invoke(
            role: String
        ) = BaseRole(role).rwmRoleOrNull()


        /**
         * Builds `prefix.rights[.identifier]` from the supplied values without validation or escaping.
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
            prefix, AccessRights(read = read, write = write, manage = manage), identifier
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
