package dev.inmo.kroles.repos.ktor.repos.ktor.server

import dev.inmo.kroles.repos.BaseRoleSubject
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import dev.inmo.kroles.roles.BaseRole
import dev.inmo.micro_utils.ktor.server.getQueryParameter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal suspend inline fun RoutingContext.subjectOrRespondError(): BaseRoleSubject? {
    val role = call.getQueryParameter(RolesKtorConstants.SubjectRoleQueryParameterName) ?.let(::BaseRole) ?.let(
        BaseRoleSubject::OtherRole
    )
    val identifier = call.getQueryParameter(RolesKtorConstants.SubjectIdentifierQueryParameterName) ?.let(
        BaseRoleSubject::Direct
    )

    return role ?: identifier ?: let {
        call.respond(HttpStatusCode.BadRequest, "You must specify one of parameters: ${RolesKtorConstants.RoleQueryParameterName} or ${RolesKtorConstants.SubjectIdentifierQueryParameterName}")
        null
    }
}