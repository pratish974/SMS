package com.dns.sms.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Google Drive, app-data folder only (scope drive.appdata: only this app can see the file,
 * Requirement.md section 9). The only network code in the app.
 */
@Singleton
class DriveClient @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val settings: com.dns.sms.settings.AppSettings,
) {

    sealed interface Auth {
        data class Token(val value: String) : Auth
        /** First use, or access was removed: the user must approve in Google's sheet. */
        data class NeedsConsent(val intent: PendingIntent) : Auth
    }

    data class RemoteFile(val id: String, val size: Long, val modifiedTime: String?)

    class DriveException(val code: Int, message: String) : IOException(message)

    private val client get() = Identity.getAuthorizationClient(ctx)
    /** Authorizes the Google account the user picked for backups (Settings > Backup), if any. */
    private fun request(): AuthorizationRequest {
        val b = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE)))
        settings.driveAccount.value.takeIf { it.contains('@') }?.let { b.setAccount(android.accounts.Account(it, GOOGLE_ACCOUNT_TYPE)) }
        return b.build()
    }

    suspend fun authorize(): Auth = client.authorize(request()).await().toAuth()

    /** After the consent sheet returns. */
    /** After the consent sheet returns. Throws (ApiException) so callers can report why it failed. */
    fun fromConsentResult(data: Intent?): Auth = client.getAuthorizationResultFromIntent(data).toAuth()

    private fun AuthorizationResult.toAuth(): Auth {
        val pi = pendingIntent
        return if (hasResolution() && pi != null) Auth.NeedsConsent(pi)
        else Auth.Token(accessToken ?: throw IOException("no access token"))
    }

    /** Runs [block] with a token; on 401 the cached token is cleared and it retries once. */
    private suspend fun <T> withToken(block: (String) -> T): T {
        val first = authorize() as? Auth.Token ?: throw DriveException(401, "Google Drive sign-in needed")
        return try {
            withContext(Dispatchers.IO) { block(first.value) }
        } catch (e: DriveException) {
            if (e.code != 401) throw e
            runCatching { client.clearToken(ClearTokenRequest.builder().setToken(first.value).build()).await() }
            val again = authorize() as? Auth.Token ?: throw e
            withContext(Dispatchers.IO) { block(again.value) }
        }
    }

    suspend fun accountEmail(): String? = runCatching {
        withToken { t -> JSONObject(get(t, "$API/about?fields=user(emailAddress)")).getJSONObject("user").optString("emailAddress") }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun find(): RemoteFile? = withToken { t ->
        val q = URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val files = JSONObject(get(t, "$API/files?spaces=appDataFolder&q=$q&fields=files(id,size,modifiedTime)&orderBy=modifiedTime%20desc"))
            .getJSONArray("files")
        if (files.length() == 0) null else files.getJSONObject(0).let {
            RemoteFile(it.getString("id"), it.optString("size", "0").toLongOrNull() ?: 0, it.optString("modifiedTime"))
        }
    }

    /** Replaces the single backup file (or creates it). */
    suspend fun upload(file: File): RemoteFile {
        val existing = find()
        return withToken { t ->
            val id = existing?.id ?: JSONObject(
                createFile(t, """{"name":"$FILE_NAME","parents":["appDataFolder"]}""".toByteArray()),
            ).getString("id")
            val conn = open(t, "PATCH", "$UPLOAD/files/$id?uploadType=media&fields=id,size,modifiedTime")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(file.length())
            conn.outputStream.use { o -> file.inputStream().use { it.copyTo(o) } }
            val json = JSONObject(readBody(conn))
            RemoteFile(json.getString("id"), json.optString("size", "0").toLongOrNull() ?: file.length(), json.optString("modifiedTime"))
        }
    }

    /** S9: removes the backup file from the app's Drive folder. */
    suspend fun delete(id: String) = withToken { t -> check(open(t, "DELETE", "$API/files/$id")) }

    suspend fun download(id: String, dest: File, maxBytes: Long) = withToken { t ->
        val conn = open(t, "GET", "$API/files/$id?alt=media")
        check(conn)
        var total = 0L
        conn.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("backup is larger than allowed")
                    out.write(buf, 0, n)
                }
            }
        }
    }

    private fun open(token: String, method: String, url: String): HttpURLConnection {
        require(url.startsWith("https://www.googleapis.com/"))
        val conn = URL(url).openConnection() as HttpURLConnection
        // HttpURLConnection has no PATCH; Drive accepts the override header.
        if (method == "PATCH") {
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            conn.requestMethod = method
        }
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun get(token: String, url: String): String = readBody(open(token, "GET", url))

    /** Creates the (empty) backup file's metadata in the app folder; returns {"id": ...}. */
    private fun createFile(token: String, metadata: ByteArray): String {
        val body = metadata
        val conn = open(token, "POST", "$API/files?fields=id")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setFixedLengthStreamingMode(body.size)
        conn.outputStream.use { it.write(body) }
        return readBody(conn)
    }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code !in 200..299) {
            // Error bodies never contain message data; still, only the status code is surfaced.
            runCatching { conn.errorStream?.close() }
            throw DriveException(code, "Google Drive error $code")
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        check(conn)
        return conn.inputStream.use { it.readBytes().decodeToString() }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FILE_NAME = "sms-backup.bin"
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
