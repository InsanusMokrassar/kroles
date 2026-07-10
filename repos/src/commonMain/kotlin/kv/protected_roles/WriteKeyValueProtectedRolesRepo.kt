package dev.inmo.kroles.repos.kv.protected_roles

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.WriteProtectedRolesRepo
import dev.inmo.micro_utils.repos.WriteKeyValueRepo
import dev.inmo.micro_utils.repos.set
import dev.inmo.micro_utils.repos.unset

/**
 * Mutating [WriteProtectedRolesRepo] implementation backed by a key-value store.
 *
 * Marks subjects as protected by storing an inclusion flag against them, and removes protection by
 * unsetting that flag.
 *
 * @param protectedKeyValueRepo Underlying store mapping each protected subject to its inclusion flag.
 */
class WriteKeyValueProtectedRolesRepo(
    private val protectedKeyValueRepo: WriteKeyValueRepo<BaseRoleSubject, Boolean>
) : WriteProtectedRolesRepo {
    /**
     * Marks [subject] as protected, storing whether inclusion of new roles stays permitted.
     *
     * @param allowInclude Whether the subject may still have roles included while protected.
     */
    override suspend fun protect(subject: BaseRoleSubject, allowInclude: Boolean) {
        protectedKeyValueRepo.set(subject, allowInclude)
    }

    /**
     * Removes any protection previously set for [subject].
     */
    override suspend fun unprotect(subject: BaseRoleSubject) {
        protectedKeyValueRepo.unset(subject)
    }
}
