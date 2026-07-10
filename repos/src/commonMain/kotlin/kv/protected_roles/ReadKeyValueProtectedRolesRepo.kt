package dev.inmo.kroles.repos.kv.protected_roles

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.ReadProtectedRolesRepo
import dev.inmo.micro_utils.repos.ReadKeyValueRepo

/**
 * Read-only [ReadProtectedRolesRepo] implementation backed by a key-value store.
 *
 * Access rights are stored as a flag per [BaseRoleSubject]: the presence of a subject key marks it
 * as protected, and the stored boolean value tells whether inclusion is additionally permitted.
 *
 * @param protectedKeyValueRepo Underlying store mapping each protected subject to its inclusion flag.
 */
class ReadKeyValueProtectedRolesRepo(
    private val protectedKeyValueRepo: ReadKeyValueRepo<BaseRoleSubject, Boolean>
) : ReadProtectedRolesRepo {
    /**
     * Allows inclusion only when the subject has an explicit `true` flag stored.
     */
    override suspend fun allowInclude(subject: BaseRoleSubject): Boolean {
        return protectedKeyValueRepo.get(subject) == true
    }
    /**
     * Allows exclusion whenever the subject has any flag stored (i.e. is known/protected).
     */
    override suspend fun allowExclude(subject: BaseRoleSubject): Boolean {
        return protectedKeyValueRepo.contains(subject)
    }
}
