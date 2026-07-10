package dev.inmo.kroles.repos

import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kroles.roles.rwm.*


/**
 * Checks whether the given [subject], through all its effective roles, is allowed to access the resource named by
 * [prefix]/[identifier] according to the given [accessCheck].
 */
suspend fun ReadRolesRepo.isIdentifierAllowed(
    subject: BaseRoleSubject,
    prefix: String,
    identifier: RWMRole.Identifier,
    accessCheck: RightsChecker
): Boolean = getAllRoles(subject).isIdentifierAllowed(
    prefix = prefix,
    identifier = identifier,
    accessCheck = accessCheck,
)

/**
 * Checks whether the given [subject], through all its effective roles, is allowed to access the resource named by
 * [prefix]/[identifier] with the given [requiredAccess] rights string.
 */
suspend fun ReadRolesRepo.isIdentifierAllowed(
    subject: BaseRoleSubject,
    prefix: String,
    identifier: RWMRole.Identifier,
    requiredAccess: String
): Boolean = getAllRoles(subject).isIdentifierAllowed(
    prefix = prefix,
    identifier = identifier,
    requiredAccess = requiredAccess,
)

/**
 * Checks whether the given [subject], through all its effective roles, is allowed to access the resource named by
 * [prefix]/[identifier] with the requested combination of [read]/[write]/[manage] rights.
 */
suspend fun ReadRolesRepo.isIdentifierAllowed(
    subject: BaseRoleSubject,
    prefix: String,
    identifier: RWMRole.Identifier,
    read: Boolean = false,
    write: Boolean = false,
    manage: Boolean = false
): Boolean = getAllRoles(subject).isIdentifierAllowed(
    prefix = prefix,
    identifier = identifier,
    read = read,
    write = write,
    manage = manage
)

/**
 * Checks whether the given [subject], through all its effective roles, is allowed access to the resource group
 * named by [prefix] according to the given [accessCheck].
 */
suspend fun ReadRolesRepo.isAccessAllowed(
    subject: BaseRoleSubject,
    prefix: String,
    accessCheck: RightsChecker
): Boolean = getAllRoles(subject).isAccessAllowed(prefix = prefix, accessCheck = accessCheck)

/**
 * Checks whether the given [subject], through all its effective roles, is allowed access to the resource group
 * named by [prefix] with the given [requiredAccess] rights string.
 */
suspend fun ReadRolesRepo.isAccessAllowed(
    subject: BaseRoleSubject,
    prefix: String,
    requiredAccess: String
): Boolean = getAllRoles(subject).isAccessAllowed(prefix = prefix, requiredAccess = requiredAccess)

/**
 * Checks whether the given [subject], through all its effective roles, is allowed access to the resource group
 * named by [prefix] with the requested combination of [read]/[write]/[manage] rights.
 */
suspend fun ReadRolesRepo.isAccessAllowed(
    subject: BaseRoleSubject,
    prefix: String,
    read: Boolean = false,
    write: Boolean = false,
    manage: Boolean = false
): Boolean = getAllRoles(subject).isAccessAllowed(prefix = prefix, read = read, write = write, manage = manage)

/**
 * Returns the identifiers under [prefix] the given [subject] is allowed to access according to the given
 * [accessCheck], or null when access is unrestricted for that prefix.
 */
suspend fun ReadRolesRepo.getAllowedIdentifiers(
    subject: BaseRoleSubject,
    prefix: String,
    accessCheck: RightsChecker
): List<RWMRole.Identifier>? = getAllRoles(subject).getAllowedIdentifiers(prefix = prefix, accessCheck = accessCheck)

/**
 * Returns the identifiers under [prefix] the given [subject] is allowed to access with the given [requiredAccess]
 * rights string, or null when access is unrestricted for that prefix.
 */
suspend fun ReadRolesRepo.getAllowedIdentifiers(
    subject: BaseRoleSubject,
    prefix: String,
    requiredAccess: String
): List<RWMRole.Identifier>? = getAllRoles(subject).getAllowedIdentifiers(
    prefix = prefix,
    requiredAccess = requiredAccess
)

/**
 * Returns the identifiers under [prefix] the given [subject] is allowed to access with the requested combination of
 * [read]/[write]/[manage] rights, or null when access is unrestricted for that prefix.
 */
suspend fun ReadRolesRepo.getAllowedIdentifiers(
    subject: BaseRoleSubject,
    prefix: String,
    read: Boolean = false,
    write: Boolean = false,
    manage: Boolean = false,
): List<RWMRole.Identifier>? = getAllRoles(subject).getAllowedIdentifiers(
    prefix = prefix,
    read = read,
    write = write,
    manage = manage
)


/**
 * Checks whether the given [subject], through all its effective roles, includes the given [role].
 */
suspend fun ReadRolesRepo.includesBaseRole(subject: BaseRoleSubject, role: BaseRole): Boolean = getAllRoles(subject).includesBaseRole(
    role = role
)