package com.example.android.uamp.common.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

data class AccountState(val signedIn: Boolean, val email: String?)

class AuthException(message: String) : Exception(message)

/**
 * Stretto account session. Mirrors the official native sign-in: the system browser performs
 * the Google sign-in and hands back a one-time code through the `stretto-native://auth`
 * callback (PKCE + state), which is exchanged for the same session cookie the website uses.
 * Email/password sign-in uses the website's `/login` endpoint directly.
 *
 * It lives in the common module so the playback service can read the session while the UI
 * drives sign-in and sign-out; both run in the same process.
 */
class StrettoAccount private constructor(context: Context) {

    companion object {
        const val BASE_URL = "https://next.kaiserapps.com"
        const val CALLBACK_SCHEME = "stretto-native"
        private const val CALLBACK_HOST = "auth"
        private const val PREFS = "stretto_account"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_EMAIL = "email"
        private const val KEY_PENDING = "pending_login"
        private const val PENDING_TTL_MS = 10 * 60 * 1000L

        @Volatile
        private var instance: StrettoAccount? = null

        fun getInstance(context: Context): StrettoAccount =
            instance ?: synchronized(this) {
                instance ?: StrettoAccount(context.applicationContext).also { instance = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /** True while the library is being downloaded. */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)

    /** Last library sync failure, or null when the last sync worked. */
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the UI wants the library re-downloaded. */
    val refreshRequests: SharedFlow<Unit> = _refreshRequests.asSharedFlow()

    val isSignedIn: Boolean get() = _state.value.signedIn

    fun requestRefresh() {
        _refreshRequests.tryEmit(Unit)
    }

    fun setSyncing(value: Boolean) {
        _syncing.value = value
    }

    fun setSyncError(message: String?) {
        _syncError.value = message
    }

    fun cookieHeader(): String? {
        val json = prefs.getString(KEY_COOKIES, null) ?: return null
        val cookies = JSONObject(json)
        if (cookies.length() == 0) return null
        return cookies.keys().asSequence().joinToString("; ") { "$it=${cookies.getString(it)}" }
    }

    private fun readState(): AccountState {
        val signedIn = !prefs.getString(KEY_COOKIES, null).isNullOrEmpty()
        return AccountState(signedIn, prefs.getString(KEY_EMAIL, null))
    }

    // ---- Browser sign-in (Google) ----

    /** Starts a browser sign-in and returns the URL to open in the browser. */
    fun beginBrowserLogin(): Uri {
        val verifier = randomToken()
        val loginState = randomToken()
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        prefs.edit().putString(
            KEY_PENDING,
            JSONObject()
                .put("verifier", verifier)
                .put("state", loginState)
                .put("at", System.currentTimeMillis())
                .toString()
        ).apply()
        return Uri.parse("$BASE_URL/native/login").buildUpon()
            .appendQueryParameter("state", loginState)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    fun isLoginCallback(uri: Uri?): Boolean =
        uri != null && uri.scheme == CALLBACK_SCHEME && uri.host == CALLBACK_HOST

    /** Finishes a browser sign-in from the `stretto-native://auth` callback. */
    suspend fun completeBrowserLogin(callback: Uri) = withContext(Dispatchers.IO) {
        val pending = prefs.getString(KEY_PENDING, null)?.let { JSONObject(it) }
        prefs.edit().remove(KEY_PENDING).apply()
        if (pending == null || System.currentTimeMillis() - pending.getLong("at") > PENDING_TTL_MS) {
            throw AuthException("That sign-in expired. Please try again.")
        }
        if (callback.getQueryParameter("state") != pending.getString("state")) {
            throw AuthException("The sign-in response did not match this request.")
        }
        callback.getQueryParameter("error")?.let { throw AuthException("Sign-in failed: $it") }
        val code = callback.getQueryParameter("code")?.takeIf { it.isNotEmpty() }
            ?: throw AuthException("No sign-in code was returned.")

        val response = post(
            "/native/exchange",
            JSONObject()
                .put("code", code)
                .put("code_verifier", pending.getString("verifier"))
                .put("state", pending.getString("state")),
            authenticated = false
        )
        finishSignIn(response, fallbackEmail = null)
    }

    // ---- Email sign-in ----

    suspend fun signInWithPassword(email: String, password: String) = withContext(Dispatchers.IO) {
        val response = post(
            "/login",
            JSONObject().put("email", email.trim()).put("password", password),
            authenticated = false
        )
        finishSignIn(response, fallbackEmail = email.trim())
    }

    private fun finishSignIn(response: Response, fallbackEmail: String?) {
        if (response.cookies.isEmpty()) throw AuthException("Stretto did not return a session.")
        saveCookies(response.cookies)
        val email = try {
            val check = post("/checklogin", null, authenticated = true)
            if (check.body.optBoolean("loggedIn")) check.body.optString("email") else null
        } catch (e: IOException) {
            null
        } catch (e: AuthException) {
            null
        }
        prefs.edit().putString(KEY_EMAIL, email ?: fallbackEmail).apply()
        _syncError.value = null
        _state.value = readState()
    }

    // ---- Sign out ----

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                post("/logout", null, authenticated = true)
            } catch (_: Exception) {
                // Signing out locally is what matters; the server session is best effort.
            }
        }
        clearSession()
    }

    /** Drops the stored session, e.g. after the server reports it as expired. */
    fun clearSession() {
        prefs.edit().remove(KEY_COOKIES).remove(KEY_EMAIL).remove(KEY_PENDING).apply()
        _syncError.value = null
        _state.value = readState()
    }

    // ---- Library ----

    class Unauthorized : IOException("Your session has expired.")

    /** Downloads the signed-in user's raw library JSON. Throws [Unauthorized] on a 403. */
    fun fetchLibrary(): String {
        val connection = open("/latestdata", "GET", authenticated = true)
        try {
            val code = connection.responseCode
            captureCookies(connection)
            if (code == 401 || code == 403) throw Unauthorized()
            if (code !in 200..299) throw IOException("Stretto returned HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // ---- HTTP ----

    private class Response(val body: JSONObject, val cookies: Map<String, String>)

    private fun post(path: String, body: JSONObject?, authenticated: Boolean): Response {
        val connection = open(path, "POST", authenticated)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                JSONObject()
            }
            if (code !in 200..299 || !json.optBoolean("success", true)) {
                val message = json.optString("errorMessage").ifEmpty {
                    json.optString("error").ifEmpty { json.optString("message") }
                }
                throw AuthException(message.ifEmpty { "Stretto request failed (HTTP $code)." })
            }
            return Response(json, parseCookies(connection))
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String, authenticated: Boolean): HttpURLConnection {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Stretto-Android/1.0")
        if (authenticated) cookieHeader()?.let { connection.setRequestProperty("Cookie", it) }
        return connection
    }

    private fun parseCookies(connection: HttpURLConnection): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        connection.headerFields["Set-Cookie"]?.forEach { header ->
            HttpCookie.parse(header).forEach { cookie ->
                if (cookie.maxAge == 0L) result.remove(cookie.name) else result[cookie.name] = cookie.value
            }
        }
        return result
    }

    /** Servers may rotate the session cookie; keep the newest one. */
    private fun captureCookies(connection: HttpURLConnection) {
        val updated = parseCookies(connection)
        if (updated.isNotEmpty() && isSignedIn) saveCookies(updated)
    }

    private fun saveCookies(cookies: Map<String, String>) {
        val merged = prefs.getString(KEY_COOKIES, null)?.let { JSONObject(it) } ?: JSONObject()
        cookies.forEach { (name, value) -> merged.put(name, value) }
        prefs.edit().putString(KEY_COOKIES, merged.toString()).apply()
    }

    private fun randomToken(): String =
        base64Url(ByteArray(32).also { random.nextBytes(it) })

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
