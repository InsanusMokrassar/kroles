package dev.inmo.kroles.repos

import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.common.Either
import dev.inmo.micro_utils.common.mapOnFirst
import dev.inmo.micro_utils.common.mapOnSecond
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * String identifier of a [BaseRoleSubject.Direct] subject (for example, a user id).
 */
typealias BaseRolSubjectDirectIdentifier = String

/**
 * Anything that can be granted roles.
 *
 * A subject is either a [Direct] raw identifier (such as a user id) or an [OtherRole] wrapping a
 * [BaseRole]. The latter allows roles to be subjects themselves, which is what enables role
 * hierarchies/inheritance.
 */
@Serializable
sealed interface BaseRoleSubject {
    /**
     * String form of this subject: the raw identifier for [Direct] and the plain role value for [OtherRole].
     */
    val rawValue: String

    companion object {
        /**
         * Creates a [Direct] subject from the given raw [identifier].
         */
        operator fun invoke(identifier: BaseRolSubjectDirectIdentifier) = Direct(identifier)
        /**
         * Creates an [OtherRole] subject wrapping the given [role].
         */
        operator fun invoke(role: BaseRole) = OtherRole(role)
        /**
         * Creates a subject from an [Either]: a left identifier becomes a [Direct], a right role becomes an [OtherRole].
         */
        operator fun invoke(either: Either<BaseRolSubjectDirectIdentifier, BaseRole>) = either.mapOnFirst {
            invoke(it)
        } ?: either.mapOnSecond {
            invoke(it)
        } ?: error("Unable to detect what to use in role subject creation with $either")
    }

    /**
     * Subject that is itself a [BaseRole], letting one role inherit the roles granted to another.
     */
    @Serializable
    @SerialName("RoleSubject")
    @JvmInline
    value class OtherRole(val role: BaseRole) : BaseRoleSubject { override val rawValue: String get() = role.plain }

    /**
     * Subject represented by a raw identifier such as a user id.
     */
    @Serializable
    @SerialName("CommonSubject")
    @JvmInline
    value class Direct(val identifier: BaseRolSubjectDirectIdentifier) : BaseRoleSubject { override val rawValue: String get() = identifier }
}