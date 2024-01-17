package dev.inmo.kroles.roles.rwm

import dev.inmo.kroles.roles.BaseRole
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Role with form "prefix.rmw.identifier", where "prefix" is an identifier of role, r - read access, w - write access,
 * m - manage access, and "identifier" is optional id for granulating of access rights.
 *
 * @sample "groups.rw.10" should give access for reading and writing in the group with id 10
 */
@Serializable
@JvmInline
value class RWMRole internal constructor(
    val role: BaseRole
) {
    internal val rightsStringNullable: String?
        get() = role.plain.dropWhile { it != '.' }.removePrefix(".").takeIf { it.isNotBlank() } ?.takeWhile { it != '.' }
    val rights
        get() = AccessRights(rightsStringNullable ?: "")
    val rightsString: String
        get() = rights.string
    val prefix: String
        get() = role.plain.takeWhile { it != '.' }
    val readAccess: Boolean
        get() = rights.r
    val writeAccess: Boolean
        get() = rights.w
    val manageAccess: Boolean
        get() = rights.m
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

    constructor(
        prefix: String,
        rights: AccessRights,
        identifier: String?
    ) : this(
        BaseRole(
            "$prefix.${rights}${identifier?.let { ".$it" } ?: ""}"
        )
    )

    constructor(
        prefix: String,
        read: Boolean,
        manage: Boolean,
        write: Boolean,
        identifier: String?
    ) : this(
        prefix, AccessRights(read, manage, write), identifier
    )

    @Serializable
    @JvmInline
    value class AccessRights internal constructor(val string: String) : Comparable<Identifier> {
        val r: Boolean
            get() = string.contains(READ_ACCESS)
        val w: Boolean
            get() = string.contains(WRITE_ACCESS)
        val m: Boolean
            get() = string.contains(MANAGE_ACCESS)
        constructor(
            read: Boolean = false,
            write: Boolean = false,
            manage: Boolean = false
        ) : this("${READ_ACCESS.takeIf { read } ?: ""}${WRITE_ACCESS.takeIf { write } ?: ""}${MANAGE_ACCESS.takeIf { manage } ?: ""}")

        override fun compareTo(other: Identifier): Int = string.compareTo(other.string)

        operator fun contains(rights: AccessRights) = !r || rights.r && !w || rights.w && !m || rights.m
        override fun toString(): String {
            return string
        }
    }

    @Serializable
    @JvmInline
    value class Identifier internal constructor(val string: String) : Comparable<Identifier> {
        override fun compareTo(other: Identifier): Int = string.compareTo(other.string)
    }

    companion object {
        val READ_ACCESS = "r"
        val MANAGE_ACCESS = "m"
        val WRITE_ACCESS = "w"

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

        operator fun invoke(
            role: String
        ) = BaseRole(role).rwmRoleOrNull()
    }
}