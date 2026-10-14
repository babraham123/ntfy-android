package io.heckel.ntfy.util

import io.heckel.ntfy.db.User
import java.net.URI
import java.net.URLDecoder

/**
 * A parsed deep link: ntfy://<host>[:port]/<topic>?user=<u>&code=<c>[&display=..][&secure=false]
 *
 * The optional `code` is a single-use enrollment code that the app exchanges for an access token,
 * so a device can sign in to a protected server without anyone typing a password. Parsing is built on
 * java.net.URI (not android.net.Uri) so it can be unit tested on the JVM.
 */
data class EnrollLink(
    val baseUrl: String,
    val topic: String,
    val user: String?,
    val code: String?,
    val displayName: String?,
    val secure: Boolean
) {
    sealed class Decision {
        data object Redeem : Decision()
        data object Subscribe : Decision()
        data class Refuse(val reason: RefuseReason) : Decision()
    }

    enum class RefuseReason {
        InsecureCode, // The code is a credential and must only travel over HTTPS
        OtherUser, // Never overwrite a saved login with a different one
        AuthorizationHeader // A custom Authorization header would override the token, like in the user settings
    }

    companion object {
        const val SCHEME = "ntfy"

        fun parse(link: String): EnrollLink? {
            val uri = try {
                URI(link)
            } catch (_: Exception) {
                return null
            }
            if (uri.scheme != SCHEME) return null
            val host = uri.host ?: return null
            val segments = (uri.rawPath ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.size != 1 || !validTopic(segments.first())) return null
            val params = try {
                parseQuery(uri.rawQuery)
            } catch (_: IllegalArgumentException) {
                return null // Malformed percent-encoding
            }
            val secure = params["secure"]?.let { !it.equals("false", ignoreCase = true) && it != "0" } ?: true
            return EnrollLink(
                baseUrl = baseUrl(host, uri.port, secure),
                topic = segments.first(),
                user = params["user"]?.ifEmpty { null },
                code = params["code"]?.ifEmpty { null },
                displayName = params["display"],
                secure = secure
            )
        }

        fun decide(link: EnrollLink, savedUser: User?, hasCustomAuthHeader: Boolean): Decision {
            if (link.code != null && !link.secure) {
                return Decision.Refuse(RefuseReason.InsecureCode)
            }
            if (link.code != null && hasCustomAuthHeader) {
                return Decision.Refuse(RefuseReason.AuthorizationHeader)
            }
            if (savedUser != null) {
                // An unused code is left to expire on the server
                return if (link.user == null || link.user == savedUser.username) {
                    Decision.Subscribe
                } else {
                    Decision.Refuse(RefuseReason.OtherUser)
                }
            }
            return if (link.code != null) Decision.Redeem else Decision.Subscribe
        }

        private fun baseUrl(host: String, port: Int, secure: Boolean): String {
            return if (secure) {
                if (port != 443 && port != -1) "https://$host:$port" else "https://$host"
            } else {
                if (port != 80 && port != -1) "http://$host:$port" else "http://$host"
            }
        }

        // First value wins, and '+' is kept literally, like android.net.Uri.getQueryParameter
        private fun parseQuery(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrEmpty()) return emptyMap()
            val params = mutableMapOf<String, String>()
            rawQuery.split("&").forEach { pair ->
                val parts = pair.split("=", limit = 2)
                val key = decode(parts[0])
                if (key.isNotEmpty() && !params.containsKey(key)) {
                    params[key] = decode(parts.getOrElse(1) { "" })
                }
            }
            return params
        }

        private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}
