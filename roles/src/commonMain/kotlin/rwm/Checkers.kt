package dev.inmo.kroles.roles.rwm

import dev.inmo.kroles.roles.BaseRole


/**
 * Returns true when any role in the list grants the [accessCheck] access for the given [prefix] and [identifier].
 */
suspend fun List<BaseRole>.isIdentifierAllowed(
    prefix: String,
    identifier: RWMRole.Identifier,
    accessCheck: RightsChecker
): Boolean {
    return any {
        RWMRole.checkRights(it, prefix, identifier, accessCheck)
    }
}

/**
 * Overload of isIdentifierAllowed building the checker from a raw [requiredAccess] rights string.
 */
suspend fun List<BaseRole>.isIdentifierAllowed(
    prefix: String,
    identifier: RWMRole.Identifier,
    requiredAccess: String
): Boolean {
    return isIdentifierAllowed(prefix, identifier, RightsChecker(requiredAccess))
}

/**
 * Overload of isIdentifierAllowed building the checker from the individual read/write/manage flags.
 */
suspend fun List<BaseRole>.isIdentifierAllowed(
    prefix: String,
    identifier: RWMRole.Identifier,
    read: Boolean = false,
    write: Boolean = false,
    manage: Boolean = false
): Boolean {
    return isIdentifierAllowed(prefix, identifier, RightsChecker(read, write, manage))
}

/**
 * Returns true when any role in the list grants the [accessCheck] access for the given [prefix], ignoring identifiers.
 */
suspend fun List<BaseRole>.isAccessAllowed(
    prefix: String,
    accessCheck: RightsChecker
): Boolean {
    return any {
        RWMRole.checkRights(it, prefix, null, accessCheck)
    }
}

/**
 * Overload of isAccessAllowed building the checker from a raw [requiredAccess] rights string.
 */
suspend fun List<BaseRole>.isAccessAllowed(
    prefix: String,
    requiredAccess: String
): Boolean {
    return isAccessAllowed(prefix, RightsChecker(requiredAccess))
}

/**
 * Overload of isAccessAllowed building the checker from the individual read/write/manage flags.
 */
suspend fun List<BaseRole>.isAccessAllowed(
    prefix: String,
    read: Boolean = false,
    write: Boolean = false,
    manage: Boolean = false
): Boolean {
    return isAccessAllowed(prefix, RightsChecker(read, write, manage))
}

/**
 * Collects the identifiers of the roles matching [prefix] whose rights pass [accessCheck].
 *
 * @return The list of allowed identifiers, or null when a matching role has no identifier.
 */
suspend fun List<BaseRole>.getAllowedIdentifiers(
    prefix: String,
    accessCheck: RightsChecker
): List<RWMRole.Identifier>? {
    return mapNotNull {
        val rmw = it.rwmRoleOrNull()?.takeIf { it.prefix == prefix } ?: return@mapNotNull null
        rmw.takeIf { accessCheck(rmw.rightsString) } ?.let { it.identifier ?: return null }
    }
}

/**
 * Overload of getAllowedIdentifiers building the checker from a raw [requiredAccess] rights string.
 */
suspend fun List<BaseRole>.getAllowedIdentifiers(
    prefix: String,
    requiredAccess: String
): List<RWMRole.Identifier>? = getAllowedIdentifiers(prefix, RightsChecker(requiredAccess))

/**
 * Overload of getAllowedIdentifiers building the checker from the individual read/write/manage flags.
 */
suspend fun List<BaseRole>.getAllowedIdentifiers(
    prefix: String,
    read: Boolean = false,
    write: Boolean = false,
    manage: Boolean = false,
): List<RWMRole.Identifier>? = getAllowedIdentifiers(prefix, RightsChecker(read, write, manage))


/**
 * Checks whether this list of roles grants the access described by [role]. For an RWM role its prefix, rights and
 * optional identifier are checked; otherwise the list is checked for an exact containment of [role].
 */
suspend fun List<BaseRole>.includesBaseRole(role: BaseRole): Boolean {
    return role.rwmRoleOrNull()?.let {
        val identifier = it.identifier
        if (identifier == null) {
            isAccessAllowed(
                prefix = it.prefix,
                read = it.readAccess,
                write = it.writeAccess,
                manage = it.manageAccess
            )
        } else {
            isIdentifierAllowed(
                prefix = it.prefix,
                identifier = identifier,
                read = it.readAccess,
                write = it.writeAccess,
                manage = it.manageAccess
            )
        }
    } ?: contains(role)
}