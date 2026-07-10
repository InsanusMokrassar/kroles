package dev.inmo.kroles.repos

import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kroles.roles.rwm.RWMRole
import dev.inmo.kroles.roles.rwm.rwmRoleOrNull
import dev.inmo.micro_utils.common.diff


/**
 * Returns the direct-subject identifiers that transitively hold the given [role].
 */
suspend fun ReadRolesRepo.getDirects(
    role: BaseRole
): List<BaseRolSubjectDirectIdentifier> = getAllSubjectsByPagination(BaseRoleSubject(role)).mapNotNull { (it as? BaseRoleSubject.Direct) ?.identifier }
/**
 * Returns all roles effectively held by the given [role] acting as a subject.
 */
suspend fun ReadRolesRepo.getAllRoles(role: BaseRole): List<BaseRole> = getAllRoles(BaseRoleSubject.OtherRole(role))
/**
 * Returns all roles effectively held by the direct subject with the given [identifier].
 */
suspend fun ReadRolesRepo.getAllRoles(identifier: BaseRolSubjectDirectIdentifier): List<BaseRole> = getAllRoles(BaseRoleSubject.Direct(identifier))
/**
 * Checks whether the direct subject with the given [identifier] effectively has the given [role].
 */
suspend fun ReadRolesRepo.contains(
    identifier: BaseRolSubjectDirectIdentifier,
    role: BaseRole
): Boolean = getAllRoles(BaseRoleSubject(identifier)).contains(role)
/**
 * Checks whether the direct subject with the given [identifier] effectively has any of the given [roles].
 */
suspend fun ReadRolesRepo.containsAny(
    identifier: BaseRolSubjectDirectIdentifier,
    roles: List<BaseRole>
): Boolean = containsAny(BaseRoleSubject.Direct(identifier), roles)


/**
 * Applies rights-level changes to the given [subject]'s direct RWM roles: for each existing role the rights in
 * [toExclude] are removed and the rights in [toInclude] are added (grouped by prefix and identifier), and the
 * resulting difference is committed through [modifyDirect]. Returns whether the update succeeded.
 */
suspend fun RolesRepo.modifyDirect(subject: BaseRoleSubject, toExclude: List<RWMRole>, toInclude: List<RWMRole>): Boolean {
    val toExcludeByPrefix = toExclude.groupBy { it.prefix }
    val toIncludeByPrefix = toInclude.groupBy { it.prefix }
    val changesByPrefixes: Map<String, (RWMRole) -> RWMRole?> = (toExcludeByPrefix.keys + toIncludeByPrefix.keys).toSet().associateWith {
        val toExcludeByIdentifier = toExcludeByPrefix[it] ?.groupBy { it.identifier } ?: emptyMap()
        val toIncludeByIdentifier = toIncludeByPrefix[it] ?.groupBy { it.identifier } ?: emptyMap()


        return@associateWith {
            val identifier = it.identifier
            val afterExclude = toExcludeByIdentifier[identifier] ?.fold(it.rights) { rights, role ->
                rights - role.rights
            } ?: it.rights
            val resultRights = toIncludeByIdentifier[identifier] ?.fold(afterExclude) { rights, role ->
                rights + role.rights
            } ?: afterExclude

            RWMRole(it.prefix, resultRights, it.identifier)
        }
    }

    val directRoles = getDirectRoles(subject)

    val newDirectRoles = directRoles.mapNotNull {
        val rwm = it.rwmRoleOrNull() ?: return@mapNotNull it
        (changesByPrefixes[rwm.prefix] ?: return@mapNotNull it).invoke(rwm) ?.role
    }

    val diff = directRoles.sortedBy { it.plain }.diff(newDirectRoles.sortedBy { it.plain })

    return modifyDirect(
        subject,
        toExclude = diff.removed.map { it.value },
        toInclude = diff.added.map { it.value }
    )
}


/**
 * Removes the rights of the given [rwmRoles] from the given [subject]'s direct roles. Returns whether it succeeded.
 */
suspend fun RolesRepo.excludeDirect(subject: BaseRoleSubject, rwmRoles: List<RWMRole>): Boolean {
    return modifyDirect(subject, toExclude = rwmRoles, toInclude = emptyList())
}


/**
 * Removes the rights of the given [rwmRole] from the given [subject]'s direct roles. Returns whether it succeeded.
 */
suspend fun RolesRepo.excludeDirect(subject: BaseRoleSubject, rwmRole: RWMRole): Boolean {
    return excludeDirect(subject, listOf(rwmRole))
}


/**
 * Adds the rights of the given [rwmRoles] to the given [subject]'s direct roles. Returns whether it succeeded.
 */
suspend fun RolesRepo.includeDirect(subject: BaseRoleSubject, rwmRoles: List<RWMRole>): Boolean {
    return modifyDirect(subject, toExclude = emptyList(), toInclude = rwmRoles)
}


/**
 * Adds the rights of the given [rwmRole] to the given [subject]'s direct roles. Returns whether it succeeded.
 */
suspend fun RolesRepo.includeDirect(subject: BaseRoleSubject, rwmRole: RWMRole): Boolean {
    return includeDirect(subject, listOf(rwmRole))
}
