package com.reqlab.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class McpOAuthGrantType {
    @SerialName("authorization_code") AUTHORIZATION_CODE,
    @SerialName("client_credentials") CLIENT_CREDENTIALS,
    @SerialName("refresh_token") REFRESH_TOKEN,
    @SerialName("paste_token") PASTE_TOKEN,
}

@Serializable
data class McpOAuthConfig(
    val authServerUrl: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val scopes: List<String> = emptyList(),
    val redirectPort: Int = 8099,
    val redirectUri: String? = null,
    val useDcr: Boolean = true,
    val useDiscovery: Boolean = true,
    val grantType: McpOAuthGrantType = McpOAuthGrantType.AUTHORIZATION_CODE,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = "Bearer",
    val expiresAtEpochMillis: Long? = null,
    val resource: String? = null,
)

@Serializable
data class OAuthProtectedResourceMetadata(
    val resource: String? = null,
    @SerialName("authorization_servers") val authorizationServers: List<String> = emptyList(),
    @SerialName("bearer_methods_supported") val bearerMethodsSupported: List<String>? = null,
    @SerialName("scopes_supported") val scopesSupported: List<String>? = null,
)

@Serializable
data class OAuthAuthorizationServerMetadata(
    val issuer: String? = null,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String? = null,
    @SerialName("token_endpoint") val tokenEndpoint: String? = null,
    @SerialName("registration_endpoint") val registrationEndpoint: String? = null,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("scopes_supported") val scopesSupported: List<String>? = null,
    @SerialName("response_types_supported") val responseTypesSupported: List<String>? = null,
    @SerialName("grant_types_supported") val grantTypesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String>? = null,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>? = null,
)

@Serializable
data class OAuthDynamicClientRegistrationRequest(
    @SerialName("client_name") val clientName: String = "ReqLab",
    @SerialName("redirect_uris") val redirectUris: List<String> = emptyList(),
    @SerialName("grant_types") val grantTypes: List<String> = listOf("authorization_code", "refresh_token"),
    @SerialName("response_types") val responseTypes: List<String> = listOf("code"),
    @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String = "none",
    @SerialName("scope") val scope: String? = null,
)

@Serializable
data class OAuthDynamicClientRegistrationResponse(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
    @SerialName("client_id_issued_at") val clientIdIssuedAt: Long? = null,
    @SerialName("client_secret_expires_at") val clientSecretExpiresAt: Long? = null,
    @SerialName("redirect_uris") val redirectUris: List<String>? = null,
    @SerialName("grant_types") val grantTypes: List<String>? = null,
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)

@Serializable
data class OAuthError(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
)

@Serializable
enum class McpOAuthPhase {
    DISCOVERY,
    DCR,
    AUTHORIZE,
    TOKEN,
    REFRESH,
    RETRY,
}

@Serializable
data class McpOAuthDebugEntry(
    val phase: McpOAuthPhase,
    val timestampEpochMillis: Long,
    val requestSummary: String,
    val responseSummary: String? = null,
    val statusCode: Int? = null,
    val error: String? = null,
    val payload: JsonElement? = null,
)
