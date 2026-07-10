package dev.inmo.kroles.repos.ktor.repos.ktor.server

import dev.inmo.kroles.repos.WriteRolesRepo
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.ktor.server.includeWebsocketHandling
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Registers the write-side roles API endpoints on this [Route], serving them from [repo].
 *
 * Sets up websocket handling for the role created/removed/included/excluded change flows, plus POST
 * handlers for including, excluding and modifying a subject's direct roles and for creating and
 * removing roles. Each POST handler receives the corresponding [RolesKtorConstants] wrapper (or a
 * [dev.inmo.kroles.roles.BaseRole]) as body and responds with the repository result.
 *
 * @param repo Write repository the endpoints delegate to.
 */
fun Route.configureWriteRolesRepoRoutes(
    repo: WriteRolesRepo
) {
    includeWebsocketHandling(
        RolesKtorConstants.RoleCreatedFlowPathPart,
        repo.roleCreated
    )
    includeWebsocketHandling(
        RolesKtorConstants.RoleRemovedFlowPathPart,
        repo.roleRemoved
    )
    includeWebsocketHandling(
        RolesKtorConstants.RoleIncludedFlowPathPart,
        repo.roleIncluded
    )
    includeWebsocketHandling(
        RolesKtorConstants.RoleExcludedFlowPathPart,
        repo.roleExcluded
    )

    post(RolesKtorConstants.IncludeDirectPathPart) {
        val wrapper = call.receive<RolesKtorConstants.IncludeExcludeWrapper>()
        call.respond(
            repo.includeDirect(
                wrapper.subject,
                wrapper.role
            )
        )
    }
    post(RolesKtorConstants.IncludeDirectsPathPart) {
        val wrapper = call.receive<RolesKtorConstants.IncludesExcludesWrapper>()
        call.respond(
            repo.includeDirect(
                wrapper.subject,
                wrapper.roles
            )
        )
    }

    post(RolesKtorConstants.ExcludeDirectPathPart) {
        val wrapper = call.receive<RolesKtorConstants.IncludeExcludeWrapper>()
        call.respond(
            repo.excludeDirect(
                wrapper.subject,
                wrapper.role
            )
        )
    }
    post(RolesKtorConstants.ExcludeDirectsPathPart) {
        val wrapper = call.receive<RolesKtorConstants.IncludesExcludesWrapper>()
        call.respond(
            repo.excludeDirect(
                wrapper.subject,
                wrapper.roles
            )
        )
    }
    post(RolesKtorConstants.ModifyDirectPathPart) {
        val wrapper = call.receive<RolesKtorConstants.ModifyWrapper>()
        call.respond(
            repo.modifyDirect(
                subject = wrapper.subject,
                toExclude = wrapper.toExclude,
                toInclude = wrapper.toInclude
            )
        )
    }

    post(RolesKtorConstants.CreateRolePathPart) {
        val role = call.receive<BaseRole>()
        call.respond(
            repo.createRole(role)
        )
    }
    post(RolesKtorConstants.RemoveRolePathPart) {
        val roles = call.receive<BaseRole>()
        call.respond(
            repo.removeRole(roles)
        )
    }
}
