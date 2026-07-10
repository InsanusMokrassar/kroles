package dev.inmo.kroles.repos.kv.protected_roles

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.ProtectedRolesRepo
import dev.inmo.kroles.repos.ReadProtectedRolesRepo
import dev.inmo.kroles.repos.WriteProtectedRolesRepo
import dev.inmo.micro_utils.repos.KeyValueRepo
import dev.inmo.micro_utils.repos.ReadKeyValueRepo

/**
 * Combined read/write [ProtectedRolesRepo] implementation backed by a key-value store.
 *
 * Delegates reads to [ReadKeyValueProtectedRolesRepo] and writes to
 * [WriteKeyValueProtectedRolesRepo], both sharing the same underlying [protectedKeyValueRepo].
 *
 * @param protectedKeyValueRepo Underlying store mapping each protected subject to its inclusion flag.
 */
class KeyValueProtectedRolesRepo(
    private val protectedKeyValueRepo: KeyValueRepo<BaseRoleSubject, Boolean>
) : ReadProtectedRolesRepo by ReadKeyValueProtectedRolesRepo(protectedKeyValueRepo),
    WriteProtectedRolesRepo by WriteKeyValueProtectedRolesRepo(protectedKeyValueRepo),
    ProtectedRolesRepo
