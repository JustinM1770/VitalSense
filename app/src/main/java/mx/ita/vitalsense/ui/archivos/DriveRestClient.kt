package mx.ita.vitalsense.ui.archivos

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

object DriveRestClient {
    private const val TAG = "DriveRestClient"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val multipartRelatedMediaType = "multipart/related".toMediaType()

    private val http by lazy {
        OkHttpClient.Builder().build()
    }

    private fun authRequest(url: String, accessToken: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
    }

    class DriveApiException(
        val httpCode: Int,
        override val message: String,
        val rawBody: String,
    ) : IllegalStateException(message)

    private fun parseErrorMessage(body: String): String {
        return try {
            // Drive error format: {"error": {"message": "...", ...}}
            val root = JSONObject(body)
            val err = root.optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() } ?: body
        } catch (_: Exception) {
            body
        }
    }

    private fun throwDriveError(code: Int, body: String, operation: String): Nothing {
        val parsed = parseErrorMessage(body).take(350)
        Log.w(TAG, "$operation failed: $code $parsed")
        throw DriveApiException(code, parsed, body)
    }

    fun listFolders(accessToken: String, pageSize: Int = 200): List<DriveFolder> {
        val q = "mimeType='application/vnd.google-apps.folder' and trashed=false"
        val fields = "files(id,name)"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?q=${encode(q)}" +
            "&fields=${encode(fields)}" +
            "&pageSize=$pageSize"

        val req = authRequest(url, accessToken).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throwDriveError(resp.code, body, "listFolders")
            }

            val json = JSONObject(body)
            val files: JSONArray = json.optJSONArray("files") ?: JSONArray()
            val result = ArrayList<DriveFolder>(files.length())
            for (i in 0 until files.length()) {
                val obj = files.getJSONObject(i)
                val id = obj.optString("id")
                val name = obj.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    result.add(DriveFolder(id = id, name = name))
                }
            }
            return result.sortedBy { it.name.lowercase() }
        }
    }

    fun listFilesInFolder(accessToken: String, folderId: String, pageSize: Int = 500): List<DriveFileItem> {
        val q = "'$folderId' in parents and trashed=false"
        val fields = "files(id,name,mimeType)"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?q=${encode(q)}" +
            "&fields=${encode(fields)}" +
            "&pageSize=$pageSize"

        val req = authRequest(url, accessToken).get().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throwDriveError(resp.code, body, "listFilesInFolder")
            }

            val json = JSONObject(body)
            val files: JSONArray = json.optJSONArray("files") ?: JSONArray()
            val result = ArrayList<DriveFileItem>(files.length())
            for (i in 0 until files.length()) {
                val obj = files.getJSONObject(i)
                val id = obj.optString("id")
                val name = obj.optString("name")
                val mimeType = obj.optString("mimeType")
                if (id.isNotBlank() && name.isNotBlank()) {
                    result.add(DriveFileItem(id = id, name = name, mimeType = mimeType))
                }
            }
            return result.sortedBy { it.name.lowercase() }
        }
    }

    /**
     * Uploads bytes from an InputStream into a Drive folder using multipart upload.
     */
    fun uploadFileToFolder(
        accessToken: String,
        folderId: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream,
    ): DriveFileItem {
        val metadata = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put(folderId))

        val metaPart = metadata.toString().toRequestBody(jsonMediaType)

        val fileBytes = inputStream.readBytes()
        val filePart = fileBytes.toRequestBody(mimeType.toMediaType())

        val multipart = MultipartBody.Builder()
            .setType(multipartRelatedMediaType)
            .addPart(metaPart)
            .addPart(filePart)
            .build()

        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType"
        val req = authRequest(url, accessToken).post(multipart).build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throwDriveError(resp.code, body, "uploadFileToFolder")
            }

            val json = JSONObject(body)
            val id = json.optString("id")
            val name = json.optString("name")
            val mt = json.optString("mimeType")
            return DriveFileItem(id = id, name = name, mimeType = mt)
        }
    }

    fun deleteFile(accessToken: String, fileId: String) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val req = authRequest(url, accessToken).delete().build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throwDriveError(resp.code, body, "deleteFile")
            }
        }
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
