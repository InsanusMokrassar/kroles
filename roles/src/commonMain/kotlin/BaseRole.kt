package dev.inmo.kroles.roles

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Value class wrapping a plain string identifier of a role.
 *
 * @property plain The raw string representation of the role.
 */
@Serializable
@JvmInline
value class BaseRole(
    val plain: String
) {
    companion object {
        /**
         * A [BaseRole] with an empty plain string.
         */
        val EMPTY = BaseRole("")
    }
}
