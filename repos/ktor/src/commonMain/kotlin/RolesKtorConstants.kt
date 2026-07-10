package dev.inmo.kroles.repos.repos.ktor

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.roles.BaseRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Constants shared between the Ktor client and server implementations of the roles HTTP API.
 *
 * Holds the URL path parts and query-parameter names used to build and route requests, as well as
 * the small serializable wrapper DTOs used as request/response bodies for write operations.
 */
object RolesKtorConstants {
    /**
     * Serializer for a list of subjects, used when reading/writing collections of
     * [dev.inmo.kroles.repos.BaseRoleSubject] values over the wire.
     */
    val RoleSubjectsSerializer = ListSerializer(BaseRoleSubject.serializer())

    /**
     * Request body pairing a single [subject] with a single [role]. Used by include/exclude-direct
     * endpoints operating on one role at a time.
     */
    @Serializable
    data class IncludeExcludeWrapper(
        val subject: BaseRoleSubject,
        val role: BaseRole
    )

    /**
     * Request body pairing a single [subject] with a list of [roles]. Used by include/exclude-direct
     * endpoints operating on several roles at once.
     */
    @Serializable
    data class IncludesExcludesWrapper(
        val subject: BaseRoleSubject,
        val roles: List<BaseRole>
    )

    /**
     * Request body for a combined modify-direct operation on a [subject]: the roles [toExclude] are
     * removed and the roles [toInclude] are added in a single call.
     */
    @Serializable
    data class ModifyWrapper(
        val subject: BaseRoleSubject,
        val toExclude: List<BaseRole>,
        val toInclude: List<BaseRole>
    )

    /** Default root path part under which the roles API routes are registered. */
    const val DefaultRolesRootPathPart = "roles"

    /** Query-parameter name carrying a role identifier. */
    const val RoleQueryParameterName = "role"
    /** Query-parameter name carrying a direct subject identifier. */
    const val SubjectIdentifierQueryParameterName = "subject_identifier"
    /** Query-parameter name carrying a subject expressed as a role. */
    const val SubjectRoleQueryParameterName = "subject_role"

    /** Path part for retrieving the direct subjects of a role. */
    const val GetDirectSubjectsPathPart = "getSubjectsByRole"
    /** Path part for retrieving the direct roles of a subject. */
    const val GetDirectRolesPathPart = "getDirectSubjectRoles"
    /** Path part for retrieving all subject-to-roles mappings. */
    const val GetAllPathPart = "getAll"
    /** Path part for retrieving all roles of a subject (direct and inherited). */
    const val GetAllRolesPathPart = "getAllRoles"
    /** Path part for retrieving all roles as a pagination page. */
    const val GetAllRolesByPaginationPathPart = "getRolesPage"
    /** Path part for retrieving all subjects as a pagination page. */
    const val GetAllSubjectsRolesByPaginationPathPart = "getSubjects"
    /** Path part for checking whether a subject contains a role. */
    const val ContainsPathPart = "contains"
    /** Path part for checking whether a subject contains any of several roles. */
    const val ContainsAnyPathPart = "containsAny"

    /** Path part for directly including a single role into a subject. */
    const val IncludeDirectPathPart = "includeDirect"
    /** Path part for directly including several roles into a subject. */
    const val IncludeDirectsPathPart = "includeDirects"
    /** Path part for directly excluding a single role from a subject. */
    const val ExcludeDirectPathPart = "excludeDirect"
    /** Path part for directly excluding several roles from a subject. */
    const val ExcludeDirectsPathPart = "excludeDirects"
    /** Path part for a combined include/exclude modification of a subject's direct roles. */
    const val ModifyDirectPathPart = "modifyDirect"
    /** Path part for creating a new role. */
    const val CreateRolePathPart = "createRole"
    /** Path part for removing an existing role. */
    const val RemoveRolePathPart = "removeRole"
    /** Path part for the websocket flow of role-included events. */
    const val RoleIncludedFlowPathPart = "roleIncludedFlow"
    /** Path part for the websocket flow of role-excluded events. */
    const val RoleExcludedFlowPathPart = "roleExcludedFlow"
    /** Path part for the websocket flow of role-created events. */
    const val RoleCreatedFlowPathPart = "roleCreatedFlow"
    /** Path part for the websocket flow of role-removed events. */
    const val RoleRemovedFlowPathPart = "roleRemovedFlow"

}