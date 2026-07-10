package dev.inmo.kroles.repos.repos.ktor.client

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.ReadRolesRepo
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.ktor.common.buildStandardUrl
import dev.inmo.micro_utils.pagination.Pagination
import dev.inmo.micro_utils.pagination.PaginationResult
import dev.inmo.micro_utils.pagination.asUrlQueryArrayParts
import dev.inmo.micro_utils.pagination.asUrlQueryParts
import dev.inmo.micro_utils.repos.ktor.common.getAllRoute
import dev.inmo.micro_utils.repos.ktor.common.reversedParameterName
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.*

/**
 * [ReadRolesRepo] implementation that reads roles data from a remote roles server over HTTP.
 *
 * Each method issues a GET request built from [rootPath] and the matching path part and query
 * parameters from [RolesKtorConstants], then deserializes the response body.
 *
 * @param client Ktor HTTP client used for every request.
 * @param rootPath Root path part the roles API is served under; defaults to
 * [RolesKtorConstants.DefaultRolesRootPathPart].
 */
class ReadKtorRolesRepo(
    private val client: HttpClient,
    private val rootPath: String = RolesKtorConstants.DefaultRolesRootPathPart
) : ReadRolesRepo {
    private val BaseRoleSubject.queryParameter
        get() = when (this) {
            is BaseRoleSubject.OtherRole -> RolesKtorConstants.SubjectRoleQueryParameterName to role.plain.encodeURLParameter()
            is BaseRoleSubject.Direct -> RolesKtorConstants.SubjectIdentifierQueryParameterName to identifier.encodeURLParameter()
        }
    override suspend fun getDirectSubjects(
        role: BaseRole
    ): List<BaseRoleSubject> = client.get(
        buildStandardUrl(
            rootPath,
            RolesKtorConstants.GetDirectSubjectsPathPart,
            RolesKtorConstants.RoleQueryParameterName to role.plain.encodeURLParameter()
        )
    ).body()

    override suspend fun getDirectRoles(
        subject: BaseRoleSubject
    ): List<BaseRole> = client.get(
        buildStandardUrl(
            rootPath,
            RolesKtorConstants.GetDirectRolesPathPart,
            subject.queryParameter
        )
    ).body()

    override suspend fun getAll(): Map<BaseRoleSubject, List<BaseRole>> = client.get(
        buildStandardUrl(
            rootPath,
            RolesKtorConstants.GetAllPathPart
        )
    ).body<List<Pair<BaseRoleSubject, List<BaseRole>>>>().toMap()

    override suspend fun getAllRoles(
        subject: BaseRoleSubject
    ): List<BaseRole> = client.get(
        buildStandardUrl(
            rootPath,
            RolesKtorConstants.GetAllRolesPathPart,
            subject.queryParameter
        )
    ).body()

    override suspend fun getAllRolesByPagination(
        pagination: Pagination,
        reversed: Boolean
    ): PaginationResult<BaseRole> {
        return client.get(
            buildStandardUrl(
                rootPath,
                RolesKtorConstants.GetAllRolesByPaginationPathPart,
                *pagination.asUrlQueryArrayParts,
                reversedParameterName to reversed.toString()
            )
        ).body()
    }

    override suspend fun getAllSubjectsByPagination(pagination: Pagination, reversed: Boolean): PaginationResult<BaseRoleSubject> {
        return client.get(
            buildStandardUrl(
                rootPath,
                RolesKtorConstants.GetAllSubjectsRolesByPaginationPathPart,
                pagination.asUrlQueryParts + (
                    reversedParameterName to reversed.toString()
                )
            )
        ).body()
    }

    override suspend fun contains(
        subject: BaseRoleSubject,
        role: BaseRole
    ): Boolean = client.get(
        buildStandardUrl(
            rootPath,
            RolesKtorConstants.ContainsPathPart,
            subject.queryParameter,
            RolesKtorConstants.RoleQueryParameterName to role.plain.encodeURLParameter()
        )
    ).body()

    override suspend fun containsAny(
        subject: BaseRoleSubject,
        roles: List<BaseRole>
    ): Boolean = client.get(
        buildStandardUrl(
            rootPath,
            RolesKtorConstants.ContainsAnyPathPart,
            subject.queryParameter,
            *roles.map {
                RolesKtorConstants.RoleQueryParameterName to it.plain
            }.toTypedArray()
        )
    ).body()
}
