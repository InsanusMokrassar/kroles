package dev.inmo.kroles.repos

/**
 * Read side of a protection policy: tells whether including or excluding roles is currently permitted for a subject.
 */
interface ReadProtectedRolesRepo {
    /**
     * Returns whether roles may currently be granted to the given [subject].
     */
    suspend fun allowInclude(subject: BaseRoleSubject): Boolean
    /**
     * Returns whether roles may currently be revoked from the given [subject].
     */
    suspend fun allowExclude(subject: BaseRoleSubject): Boolean

    /**
     * Protection policy that permits every include and exclude operation.
     */
    object AlwaysUnprotected : ReadProtectedRolesRepo {
        override suspend fun allowInclude(subject: BaseRoleSubject): Boolean {
            return true
        }
        override suspend fun allowExclude(subject: BaseRoleSubject): Boolean {
            return true
        }
    }
}

/**
 * Write side of a protection policy: marks subjects as protected or unprotected.
 */
interface WriteProtectedRolesRepo {
    /**
     * Marks the given [subject] as protected. [allowInclude] controls whether including roles remains permitted
     * while the subject is protected.
     */
    suspend fun protect(subject: BaseRoleSubject, allowInclude: Boolean = true)
    /**
     * Removes protection from the given [subject].
     */
    suspend fun unprotect(subject: BaseRoleSubject)
}

/**
 * Full protection policy combining the read ([ReadProtectedRolesRepo]) and write ([WriteProtectedRolesRepo]) sides.
 */
interface ProtectedRolesRepo : ReadProtectedRolesRepo, WriteProtectedRolesRepo
