package dev.inmo.kroles.repos.repos.ktor.client

import dev.inmo.kroles.repos.ReadRolesRepo
import dev.inmo.kroles.repos.RolesRepo
import dev.inmo.kroles.repos.WriteRolesRepo
import dev.inmo.kroles.repos.repos.ktor.RolesKtorConstants
import io.ktor.client.HttpClient

/**
 * Full [RolesRepo] client backed by a remote roles server reached through a Ktor [HttpClient].
 *
 * Combines a [ReadKtorRolesRepo] and a [WriteKtorRolesRepo] via delegation, so reads use plain HTTP
 * requests and write-side change flows use websockets. All requests are addressed under [rootPath].
 *
 * @param client Ktor HTTP client used for every request.
 * @param rootPath Root path part the roles API is served under; defaults to
 * [RolesKtorConstants.DefaultRolesRootPathPart].
 */
class KtorRolesRepo(
    private val client: HttpClient,
    private val rootPath: String = RolesKtorConstants.DefaultRolesRootPathPart
) : RolesRepo,
    ReadRolesRepo by ReadKtorRolesRepo(
        client,
        rootPath
    ),
    WriteRolesRepo by WriteKtorRolesRepo(
        client,
        rootPath
    )
