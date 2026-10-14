package io.heckel.ntfy.msg

import com.google.gson.Gson
import io.heckel.ntfy.db.CustomHeader
import io.heckel.ntfy.util.HttpUtil
import io.heckel.ntfy.util.Log
import io.heckel.ntfy.util.accountTokenRedeemUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Exchanges a single-use enrollment code for an access token (POST /v1/account/token/redeem).
 * The request is unauthenticated; the code itself is the credential.
 */
class TokenRedeemer(private val client: OkHttpClient) {
    private val gson = Gson()

    fun redeem(baseUrl: String, code: String, label: String, customHeaders: List<CustomHeader> = emptyList()): RedeemedToken {
        val url = accountTokenRedeemUrl(baseUrl)
        val body = gson.toJson(RedeemRequest(code, label)).toRequestBody(JSON_MEDIA_TYPE)
        val request = HttpUtil.requestBuilder(url, user = null, customHeaders)
            .post(body)
            .build()
        Log.d(TAG, "Redeeming enrollment code at $url")
        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (response.code == 404) {
                throw ServerUnsupportedException()
            } else if (!response.isSuccessful) {
                throw ApiService.parseApiException(responseBody)
                    ?: Exception("Unexpected response ${response.code} when redeeming enrollment code")
            }
            val token = try {
                gson.fromJson(responseBody, RedeemedToken::class.java)
            } catch (_: Exception) {
                null
            }
            // Gson ignores Kotlin nullability, so missing fields arrive as null
            if (token == null || token.token.isNullOrEmpty() || token.username.isNullOrEmpty()) {
                throw Exception("Invalid response when redeeming enrollment code")
            }
            return token
        }
    }

    data class RedeemedToken(
        val token: String,
        val username: String
    )

    private data class RedeemRequest(
        val code: String,
        val label: String
    )

    /** The server predates enrollment codes. */
    class ServerUnsupportedException : Exception("Server does not support enrollment codes")

    companion object {
        private const val TAG = "NtfyTokenRedeemer"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        // Must match the server's error code for invalid, used or expired enrollment codes
        const val ERROR_CODE_INVALID_CODE = 40060
    }
}
