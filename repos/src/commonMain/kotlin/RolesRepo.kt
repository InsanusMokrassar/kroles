package dev.inmo.kroles.repos

import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import dev.inmo.micro_utils.pagination.utils.getAllByWithNextPaging
import kotlinx.coroutines.flow.Flow


/**
 * Read-only side of a roles repository: queries which roles a subject has and which subjects hold a role,
 * both directly and transitively through the role hierarchy.
 */
interface ReadRolesRepo {
    /**
     * Returns the subjects that are granted the given [role] directly (without traversing the hierarchy).
     */
    suspend fun getDirectSubjects(role: BaseRole): List<BaseRoleSubject>
    /**
     * Returns the roles granted to the given [subject] directly (without traversing the hierarchy).
     */
    suspend fun getDirectRoles(subject: BaseRoleSubject): List<BaseRole>
    /**
     * Returns the whole mapping of every subject to the list of roles directly granted to it.
     */
    suspend fun getAll(): Map<BaseRoleSubject, List<BaseRole>>
    /**
     * Returns all roles the given [subject] effectively has, traversing the hierarchy transitively so that
     * inherited roles are included.
     */
    suspend fun getAllRoles(subject: BaseRoleSubject): List<BaseRole> = when (subject) {
        is BaseRoleSubject.OtherRole -> {
            val toCheck = linkedSetOf<BaseRole>()
            val roles = mutableSetOf<BaseRole>()
            toCheck.addAll(getDirectRoles(subject))
            roles.addAll(toCheck)

            while (toCheck.isNotEmpty()) {
                val current = toCheck.first()
                roles.add(current)

                toCheck.addAll(
                    getDirectRoles(BaseRoleSubject.OtherRole(current)).filter {
                        it !in roles
                    }
                )

                toCheck.remove(current)
            }

            roles.toList()
        }
        is BaseRoleSubject.Direct -> {
            val roles = getDirectRoles(subject)
            roles.flatMap {
                getAllRoles(BaseRoleSubject.OtherRole(it)) + it
            }.distinct()
        }
    }
    /**
     * Returns every subject reachable from the given [subject] by traversing the hierarchy transitively,
     * collapsing role subjects down to the concrete subjects that ultimately hold them.
     */
    suspend fun getAllSubjectsByPagination(subject: BaseRoleSubject): Set<BaseRoleSubject> {
        val visitedSubjects = mutableSetOf<BaseRoleSubject>()
        val subjectsToVisit = mutableListOf<BaseRoleSubject>(subject)
        while (subjectsToVisit.isNotEmpty()) {
            when (val current = subjectsToVisit.removeFirst()) {
                is BaseRoleSubject.OtherRole -> {
                    val directSubjects = getDirectSubjects(current.role)
                    directSubjects.forEach {
                        if (visitedSubjects.add(it)) {
                            subjectsToVisit.add(it)
                        }
                    }
                }
                is BaseRoleSubject.Direct -> visitedSubjects.add(current)
            }
        }
        return visitedSubjects.toSet()
    }
    /**
     * Returns a page of all known roles according to the given [pagination], optionally [reversed].
     */
    suspend fun getAllRolesByPagination(pagination: Pagination, reversed: Boolean = false): PaginationResult<BaseRole>
    /**
     * Returns all known roles by iterating over every page of [getAllRolesByPagination].
     */
    suspend fun getAllRoles() = getAllByWithNextPaging {
        getAllRolesByPagination(it)
    }
    /**
     * Returns a page of all known subjects according to the given [pagination], optionally [reversed].
     */
    suspend fun getAllSubjectsByPagination(pagination: Pagination, reversed: Boolean = false): PaginationResult<BaseRoleSubject>
    /**
     * Returns whether the given [subject] effectively has the given [role] (taking the hierarchy into account).
     */
    suspend fun contains(subject: BaseRoleSubject, role: BaseRole): Boolean
    /**
     * Returns whether the given [subject] effectively has any of the given [roles] (taking the hierarchy into account).
     */
    suspend fun containsAny(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean
}


/**
 * Write side of a roles repository: mutates the grants and exposes change events as flows.
 */
interface WriteRolesRepo {
    /**
     * Emits a subject/role pair whenever a role is directly granted to a subject.
     */
    val roleIncluded: Flow<Pair<BaseRoleSubject, BaseRole>>
    /**
     * Emits a subject/role pair whenever a role is directly revoked from a subject.
     */
    val roleExcluded: Flow<Pair<BaseRoleSubject, BaseRole>>
    /**
     * Emits a role whenever a new role is created.
     */
    val roleCreated: Flow<BaseRole>
    /**
     * Emits a role whenever a role is removed.
     */
    val roleRemoved: Flow<BaseRole>

    /**
     * Directly grants the given [role] to the given [subject]. Returns whether the state changed.
     */
    suspend fun includeDirect(subject: BaseRoleSubject, role: BaseRole): Boolean
    /**
     * Directly revokes the given [role] from the given [subject]. Returns whether the state changed.
     */
    suspend fun excludeDirect(subject: BaseRoleSubject, role: BaseRole): Boolean
    /**
     * Creates the given [newRole]. Returns whether it was created.
     */
    suspend fun createRole(newRole: BaseRole): Boolean
    /**
     * Removes the given [role]. Returns whether it was removed.
     */
    suspend fun removeRole(role: BaseRole): Boolean

    /**
     * Directly grants all the given [roles] to the given [subject]. Returns whether every grant changed the state.
     */
    suspend fun includeDirect(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean {
        return roles.map {
            includeDirect(subject, it)
        }.all { it }
    }
    /**
     * Directly revokes all the given [roles] from the given [subject]. Returns whether every revocation changed the state.
     */
    suspend fun excludeDirect(subject: BaseRoleSubject, roles: List<BaseRole>): Boolean {
        return roles.map {
            excludeDirect(subject, it)
        }.all { it }
    }
    /**
     * Applies both revocations ([toExclude]) and grants ([toInclude]) for the given [subject] in a single call,
     * excluding first and then including. Returns whether both operations succeeded.
     */
    suspend fun modifyDirect(subject: BaseRoleSubject, toExclude: List<BaseRole>, toInclude: List<BaseRole>): Boolean {
        return excludeDirect(subject, toExclude) && includeDirect(subject, toInclude)
    }
}

/**
 * Full roles repository combining the read ([ReadRolesRepo]) and write ([WriteRolesRepo]) sides.
 */
interface RolesRepo : ReadRolesRepo, WriteRolesRepo
