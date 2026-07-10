package dev.inmo.kroles.repos.kv.roles

import dev.inmo.kroles.repos.*
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.kslog.common.KSLog
import dev.inmo.kslog.common.TagLogger
import dev.inmo.micro_utils.repos.KeyValuesRepo
import dev.inmo.micro_utils.repos.MapKeyValuesRepo

/**
 * Combined read/write [RolesRepo] implementation backed by a key-value store.
 *
 * Delegates reads to [ReadKeyValueRolesRepo] and writes to [WriteKeyValueRolesRepo], both sharing
 * the same underlying [keyValuesRepo]. Defaults to an in-memory store with no access-rights
 * protection, which makes it convenient for tests and simple setups.
 *
 * @param keyValuesRepo Underlying key-values store mapping each subject to the roles granted to it;
 * an in-memory map by default.
 * @param protectedRolesRepo Source of include/exclude access-rights checks for writes; unprotected
 * by default.
 * @param logger Logger passed to the write delegate for reporting denials and failures.
 */
class KeyValueRolesRepo(
    keyValuesRepo: KeyValuesRepo<BaseRoleSubject, BaseRole> = MapKeyValuesRepo(),
    protectedRolesRepo: ReadProtectedRolesRepo = ReadProtectedRolesRepo.AlwaysUnprotected,
    logger: KSLog = TagLogger("WriteKeyValueRolesRepo")
) : RolesRepo,
    ReadRolesRepo by ReadKeyValueRolesRepo(keyValuesRepo),
    WriteRolesRepo by WriteKeyValueRolesRepo(keyValuesRepo, protectedRolesRepo, logger)