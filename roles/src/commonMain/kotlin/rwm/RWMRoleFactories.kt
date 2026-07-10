package dev.inmo.kroles.roles.rwm

import dev.inmo.kroles.roles.BaseRole


/**
 * Wraps this [BaseRole] as an [RWMRole] or returns null when it has no valid rights segment.
 */
fun BaseRole.rwmRoleOrNull(): RWMRole? = RWMRole(this).takeIf { it.rightsStringNullable != null }
/**
 * Wraps this [BaseRole] as an [RWMRole] or throws when it has no valid rights segment.
 */
fun BaseRole.rwmRoleOrThrow(): RWMRole = RWMRole(this).also {
    require(it.rightsStringNullable != null)
}

/**
 * Parses [role] into an [RWMRole] or returns null when it has no valid rights segment.
 */
fun rwmRoleOrNull(role: String): RWMRole? = BaseRole(role).rwmRoleOrNull()
/**
 * Parses [role] into an [RWMRole] or throws when it has no valid rights segment.
 */
fun rwmRoleOrThrow(role: String): RWMRole = BaseRole(role).rwmRoleOrThrow()
