package dev.inmo.kroles.repos.repos.ktor.client

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.WriteRolesRepo
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.ktor.client.createStandardWebsocketFlow
import dev.inmo.micro_utils.ktor.common.buildStandardUrl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.Flow

/**
 * [WriteRolesRepo] implementation that performs role mutations against a remote roles server.
 *
 * Mutating methods send POST requests (with a [RolesKtorConstants] wrapper DTO as body) to URLs
 * precomputed from [rootPath], while the change-event flows are backed by standard websocket flows
 * connected to the corresponding flow path parts.
 *
 * @param client Ktor HTTP client used for every request.
 * @param rootPath Root path part the roles API is served under; defaults to
 * [RolesKtorConstants.DefaultRolesRootPathPart].
 */
class WriteKtorRolesRepo(
    private val client: HttpClient,
    rootPath: String = RolesKtorConstants.DefaultRolesRootPathPart
) : WriteRolesRepo {
    private val includeDirectFullUrl = buildStandardUrl(
        rootPath,
        RolesKtorConstants.IncludeDirectPathPart
    )
    private val includeDirectsFullUrl = buildStandardUrl(
        rootPath,
        RolesKtorConstants.IncludeDirectsPathPart
    )
    private val excludeDirectFullUrl = buildStandardUrl(
        rootPath,
        RolesKtorConstants.ExcludeDirectPathPart
    )
    private val excludeDirectsFullUrl = buildStandardUrl(
        rootPath,
        RolesKtorConstants.ExcludeDirectsPathPart
    )
    private val modifyDirectFullUrl = buildStandardUrl(
        rootPath,
        RolesKtorConstants.ModifyDirectPathPart
    )
    private val createFullUrl = buildStandardUrl(
        rootPath,
        RolesKtorConstants.CreateRolePathPart
    )
    private val removeFullUrl = buildStandardUrl(
        rootPath,
        RolesKtorConstants.RemoveRolePathPart
    )

    override val roleIncluded: Flow<Pair<BaseRoleSubject, BaseRole>> = client.createStandardWebsocketFlow(
        buildStandardUrl(rootPath, RolesKtorConstants.RoleIncludedFlowPathPart)
    )

    override val roleExcluded: Flow<Pair<BaseRoleSubject, BaseRole>> = client.createStandardWebsocketFlow(
        buildStandardUrl(rootPath, RolesKtorConstants.RoleExcludedFlowPathPart)
    )

    override val roleCreated: Flow<BaseRole> = client.createStandardWebsocketFlow(
        buildStandardUrl(rootPath, RolesKtorConstants.RoleCreatedFlowPathPart)
    )

    override val roleRemoved: Flow<BaseRole> = client.createStandardWebsocketFlow(
        buildStandardUrl(rootPath, RolesKtorConstants.RoleRemovedFlowPathPart)
    )

    override suspend fun includeDirect(
        subject: BaseRoleSubject,
        role: BaseRole
    ): Boolean = client.post(includeDirectFullUrl) {
        setBody(
            RolesKtorConstants.IncludeExcludeWrapper(
                subject, role
            )
        )
    }.body()

    override suspend fun includeDirect(
        subject: BaseRoleSubject,
        roles: List<BaseRole>
    ): Boolean = client.post(includeDirectsFullUrl) {
        setBody(
            RolesKtorConstants.IncludesExcludesWrapper(
                subject, roles
            )
        )
    }.body()

    override suspend fun excludeDirect(
        subject: BaseRoleSubject,
        role: BaseRole
    ): Boolean = client.post(excludeDirectFullUrl) {
        setBody(
            RolesKtorConstants.IncludeExcludeWrapper(
                subject, role
            )
        )
    }.body()

    override suspend fun excludeDirect(
        subject: BaseRoleSubject,
        roles: List<BaseRole>
    ): Boolean = client.post(excludeDirectsFullUrl) {
        setBody(
            RolesKtorConstants.IncludesExcludesWrapper(
                subject = subject,
                roles = roles
            )
        )
    }.body()

    override suspend fun modifyDirect(
        subject: BaseRoleSubject,
        toExclude: List<BaseRole>,
        toInclude: List<BaseRole>
    ): Boolean {
        return client.post(modifyDirectFullUrl) {
            setBody(
                RolesKtorConstants.ModifyWrapper(
                    subject = subject,
                    toExclude = toExclude,
                    toInclude = toInclude
                )
            )
        }.body()
    }

    override suspend fun createRole(newRole: BaseRole): Boolean = client.post(createFullUrl) {
        setBody(newRole)
    }.body()

    override suspend fun removeRole(role: BaseRole): Boolean = client.post(removeFullUrl) {
        setBody(role)
    }.body()
}
