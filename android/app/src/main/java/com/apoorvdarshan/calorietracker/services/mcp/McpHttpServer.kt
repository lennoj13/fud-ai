package com.apoorvdarshan.calorietracker.services.mcp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight, zero-dependency embedded HTTP and SSE server implementing
 * the Model Context Protocol (MCP) and companion REST API for Fud AI.
 */
class McpHttpServer(
    private val port: Int,
    private val authToken: String,
    private val toolExecutor: McpToolExecutor
) {
    private var serverSocket: ServerSocket? = null
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    // SSE client channels keyed by sessionId
    private val sseSessions = ConcurrentHashMap<String, Channel<String>>()

    fun start() {
        if (serverSocket != null && !serverSocket!!.isClosed) return

        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
            serverSocket = socket
            Log.i(TAG, "McpHttpServer started on port $port")

            acceptJob = serverScope.launch {
                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        launch { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!socket.isClosed) {
                            Log.w(TAG, "Error accepting client connection", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start McpHttpServer on port $port", e)
            throw e
        }
    }

    fun stop() {
        try {
            acceptJob?.cancel()
            serverSocket?.close()
            serverSocket = null
            sseSessions.values.forEach { it.close() }
            sseSessions.clear()
            Log.i(TAG, "McpHttpServer stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping McpHttpServer", e)
        }
    }

    private suspend fun handleClient(client: Socket) {
        withContext(Dispatchers.IO) {
            try {
                client.soTimeout = 30000 // 30s timeout for request headers
                val inputStream = client.getInputStream()
                val outputStream = client.getOutputStream()
                val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

                val requestLine = reader.readLine() ?: return@withContext
                val requestParts = requestLine.split(" ")
                if (requestParts.size < 2) return@withContext

                val method = requestParts[0].uppercase()
                val rawUri = requestParts[1]
                val path = rawUri.substringBefore("?")
                val queryString = if (rawUri.contains("?")) rawUri.substringAfter("?") else ""
                val queryParams = parseQueryParams(queryString)

                // Read headers
                val headers = mutableMapOf<String, String>()
                var contentLength = 0
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val headerName = line.substring(0, colonIdx).trim().lowercase()
                        val headerValue = line.substring(colonIdx + 1).trim()
                        headers[headerName] = headerValue
                        if (headerName == "content-length") {
                            contentLength = headerValue.toIntOrNull() ?: 0
                        }
                    }
                    line = reader.readLine()
                }

                // Handle CORS preflight
                if (method == "OPTIONS") {
                    sendCorsPreflightResponse(outputStream)
                    return@withContext
                }

                // Check authentication if configured
                if (authToken.isNotBlank()) {
                    val authHeader = headers["authorization"]
                    val tokenFromHeader = authHeader?.removePrefix("Bearer ")?.trim()
                    val tokenFromQuery = queryParams["token"]
                    val providedToken = tokenFromHeader ?: tokenFromQuery

                    if (providedToken != authToken) {
                        sendJsonResponse(outputStream, 401, JSONObject().apply {
                            put("error", "Unauthorized")
                            put("message", "Token de autorización inválido o ausente.")
                        })
                        return@withContext
                    }
                }

                // Read request body if present
                val body = if (contentLength > 0) {
                    val buffer = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val read = reader.read(buffer, readTotal, contentLength - readTotal)
                        if (read < 0) break
                        readTotal += read
                    }
                    String(buffer, 0, readTotal)
                } else ""

                // Route request
                when {
                    // 1. SSE Connection for MCP
                    method == "GET" && path == "/mcp/sse" -> {
                        client.soTimeout = 0 // Keep connection open indefinitely for SSE
                        handleSseConnection(outputStream, queryParams)
                    }

                    // 2. MCP JSON-RPC message endpoint (POST /mcp/message or POST /mcp)
                    method == "POST" && (path == "/mcp/message" || path == "/mcp") -> {
                        val sessionId = queryParams["sessionId"]
                        handleMcpRpcMessage(outputStream, body, sessionId)
                    }

                    // 3. OpenAPI 3.0 Specification for ChatGPT Actions
                    method == "GET" && (path == "/openapi.json" || path == "/openapi.yaml") -> {
                        val hostHeader = headers["host"] ?: "localhost:$port"
                        val scheme = if (headers["x-forwarded-proto"] == "https" || hostHeader.contains("serveo.net") || hostHeader.contains("serveousercontent.com")) "https" else "http"
                        val baseUrl = "$scheme://$hostHeader"
                        val openApiJson = McpOpenApiSpec.generateJson(baseUrl)
                        sendJsonResponse(outputStream, 200, openApiJson)
                    }

                    // 4. Status and greeting
                    method == "GET" && (path == "/" || path == "/status" || path == "/api/status") -> {
                        val hostHeader = headers["host"] ?: "localhost:$port"
                        val scheme = if (headers["x-forwarded-proto"] == "https" || hostHeader.contains("serveo.net") || hostHeader.contains("serveousercontent.com")) "https" else "http"
                        val baseUrl = "$scheme://$hostHeader"
                        sendJsonResponse(outputStream, 200, JSONObject().apply {
                            put("app", "Fud AI")
                            put("mcp_server", "active")
                            put("version", "1.0.0")
                            put("tools_count", McpToolRegistry.getToolsListJson().length())
                            put("openapi_spec", "$baseUrl/openapi.json")
                            put("sse_endpoint", "$baseUrl/mcp/sse")
                            put("message_endpoint", "$baseUrl/mcp/message")
                            put("direct_mcp_endpoint", "$baseUrl/mcp")
                        })
                    }

                    // 5. REST: Today's summary
                    method == "GET" && path == "/api/summary" -> {
                        val result = toolExecutor.executeTool("get_today_summary", JSONObject().apply {
                            queryParams["date"]?.let { put("date", it) }
                        })
                        sendJsonResponse(outputStream, 200, result.optJSONObject("structuredData") ?: result)
                    }

                    // 6. REST: Food entries (query & filter)
                    method == "GET" && path == "/api/meals" -> {
                        val args = JSONObject().apply {
                            queryParams["from"]?.let { put("from", it) }
                            queryParams["to"]?.let { put("to", it) }
                            queryParams["meal_type"]?.let { put("meal_type", it) }
                            queryParams["query"]?.let { put("query", it) }
                            queryParams["limit"]?.toIntOrNull()?.let { put("limit", it) }
                        }
                        val result = toolExecutor.executeTool("get_food_entries", args)
                        sendJsonResponse(outputStream, 200, result.optJSONObject("structuredData") ?: result)
                    }

                    // 7. REST: Log meal (with photos, ingredients & macros)
                    method == "POST" && path == "/api/meals" -> {
                        val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                        val result = toolExecutor.executeTool("log_food_entry", json)
                        val status = if (result.optBoolean("isError", false)) 400 else 201
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    // 8. REST: Update meal
                    (method == "PUT" || method == "PATCH") && path == "/api/meals" -> {
                        val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                        val result = toolExecutor.executeTool("update_food_entry", json)
                        val status = if (result.optBoolean("isError", false)) 400 else 200
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    // 9. REST: Delete meal
                    method == "DELETE" && path == "/api/meals" -> {
                        val id = queryParams["id"] ?: runCatching { JSONObject(body).optString("id") }.getOrNull() ?: ""
                        val result = toolExecutor.executeTool("delete_food_entry", JSONObject().put("id", id))
                        val status = if (result.optBoolean("isError", false)) 400 else 200
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    // 10. REST: Get user profile & goals
                    method == "GET" && path == "/api/profile" -> {
                        val result = toolExecutor.executeTool("get_user_profile", JSONObject())
                        sendJsonResponse(outputStream, 200, result.optJSONObject("structuredData") ?: result)
                    }

                    // 11. REST: Update user goals
                    method == "POST" && (path == "/api/goals" || path == "/api/profile/goals") -> {
                        val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                        val result = toolExecutor.executeTool("update_user_goals", json)
                        val status = if (result.optBoolean("isError", false)) 400 else 200
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    // 12. REST: Weight history & log weight
                    method == "GET" && path == "/api/weight" -> {
                        val limit = queryParams["limit"]?.toIntOrNull() ?: 30
                        val result = toolExecutor.executeTool("get_weight_history", JSONObject().put("limit", limit))
                        sendJsonResponse(outputStream, 200, result.optJSONObject("structuredData") ?: result)
                    }
                    method == "POST" && path == "/api/weight" -> {
                        val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                        val result = toolExecutor.executeTool("log_weight", json)
                        val status = if (result.optBoolean("isError", false)) 400 else 201
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    // 13. REST: Water history & log water
                    method == "GET" && path == "/api/water" -> {
                        val days = queryParams["days"]?.toIntOrNull() ?: 7
                        val result = toolExecutor.executeTool("get_water_history", JSONObject().put("days", days))
                        sendJsonResponse(outputStream, 200, result.optJSONObject("structuredData") ?: result)
                    }
                    method == "POST" && path == "/api/water" -> {
                        val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                        val result = toolExecutor.executeTool("log_water", json)
                        val status = if (result.optBoolean("isError", false)) 400 else 201
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    // 14. REST: Workouts
                    method == "GET" && path == "/api/workouts" -> {
                        val args = JSONObject().apply {
                            queryParams["limit"]?.toIntOrNull()?.let { put("limit", it) }
                            queryParams["date"]?.let { put("date", it) }
                        }
                        val result = toolExecutor.executeTool("get_workout_history", args)
                        sendJsonResponse(outputStream, 200, result.optJSONObject("structuredData") ?: result)
                    }
                    method == "POST" && path == "/api/workouts" -> {
                        val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                        val result = toolExecutor.executeTool("log_workout_session", json)
                        val status = if (result.optBoolean("isError", false)) 400 else 201
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    // 15. REST: Fasting
                    method == "POST" && path == "/api/fasting" -> {
                        val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                        val result = toolExecutor.executeTool("control_fasting", json)
                        val status = if (result.optBoolean("isError", false)) 400 else 200
                        sendJsonResponse(outputStream, status, result.optJSONObject("structuredData") ?: result)
                    }

                    else -> {
                        sendJsonResponse(outputStream, 404, JSONObject().apply {
                            put("error", "Not Found")
                            put("path", path)
                        })
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception handling client", e)
            } finally {
                // If it's not an active SSE connection, close it
                runCatching { client.close() }
            }
        }
    }

    private suspend fun handleSseConnection(out: OutputStream, queryParams: Map<String, String>) {
        val sessionId = queryParams["sessionId"] ?: UUID.randomUUID().toString()
        val channel = Channel<String>(Channel.BUFFERED)
        sseSessions[sessionId] = channel

        val writer = PrintWriter(out, false, StandardCharsets.UTF_8)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/event-stream; charset=utf-8\r\n")
        writer.print("Cache-Control: no-cache, no-transform\r\n")
        writer.print("Connection: keep-alive\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("\r\n")
        writer.flush()

        // Send endpoint registration event per MCP spec
        writer.print("event: endpoint\r\n")
        writer.print("data: /mcp/message?sessionId=$sessionId\r\n\r\n")
        writer.flush()

        try {
            while (true) {
                // Wait for message or send keep-alive comment
                val message = kotlinx.coroutines.selects.select<String?> {
                    channel.onReceive { it }
                }

                if (message != null) {
                    writer.print("event: message\r\n")
                    writer.print("data: $message\r\n\r\n")
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SSE connection ended for session $sessionId")
        } finally {
            sseSessions.remove(sessionId)
            channel.close()
        }
    }

    private suspend fun handleMcpRpcMessage(out: OutputStream, rawBody: String, sessionId: String?) {
        val request = runCatching { JSONObject(rawBody) }.getOrNull()
        if (request == null) {
            sendJsonResponse(out, 400, JSONObject().apply {
                put("jsonrpc", "2.0")
                put("error", JSONObject().apply {
                    put("code", -32700)
                    put("message", "Parse error: Invalid JSON")
                })
            })
            return
        }

        val id = request.opt("id")
        val method = request.optString("method")
        val params = request.optJSONObject("params") ?: JSONObject()

        val response = JSONObject().apply {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
        }

        when (method) {
            "initialize" -> {
                response.put("result", JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", JSONObject().apply {
                        put("tools", JSONObject().apply {
                            put("listChanged", false)
                        })
                    })
                    put("serverInfo", JSONObject().apply {
                        put("name", "fudai-mcp-server")
                        put("version", "1.0.0")
                    })
                })
            }

            "notifications/initialized" -> {
                // Client acknowledging initialization; no response payload needed
                sendRawResponse(out, 204, "No Content", "text/plain", "")
                return
            }

            "ping" -> {
                response.put("result", JSONObject())
            }

            "tools/list" -> {
                response.put("result", JSONObject().apply {
                    put("tools", McpToolRegistry.getToolsListJson())
                })
            }

            "tools/call" -> {
                val toolName = params.optString("name")
                val toolArgs = params.optJSONObject("arguments") ?: JSONObject()
                val executionResult = toolExecutor.executeTool(toolName, toolArgs)
                response.put("result", executionResult)
            }

            else -> {
                response.put("error", JSONObject().apply {
                    put("code", -32601)
                    put("message", "Method not found: '$method'")
                })
            }
        }

        val responseString = response.toString()

        // If the client has an active SSE session, also broadcast to the SSE channel
        if (!sessionId.isNullOrBlank()) {
            sseSessions[sessionId]?.trySend(responseString)
        }

        // Send HTTP 200 response directly to the POST caller
        sendJsonResponse(out, 200, response)
    }

    private fun sendJsonResponse(out: OutputStream, statusCode: Int, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        val statusText = when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "OK"
        }
        sendRawResponse(out, statusCode, statusText, "application/json; charset=utf-8", bytes)
    }

    private fun sendCorsPreflightResponse(out: OutputStream) {
        val writer = PrintWriter(out, false, StandardCharsets.UTF_8)
        writer.print("HTTP/1.1 204 No Content\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
        writer.print("Access-Control-Max-Age: 86400\r\n")
        writer.print("Content-Length: 0\r\n")
        writer.print("\r\n")
        writer.flush()
    }

    private fun sendRawResponse(
        out: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val writer = PrintWriter(out, false, StandardCharsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${body.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
        writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        out.write(body)
        out.flush()
    }

    private fun sendRawResponse(
        out: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) = sendRawResponse(out, statusCode, statusText, contentType, body.toByteArray(StandardCharsets.UTF_8))

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { part ->
            val equalsIdx = part.indexOf('=')
            if (equalsIdx > 0) {
                val key = URLDecoder.decode(part.substring(0, equalsIdx), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(part.substring(equalsIdx + 1), StandardCharsets.UTF_8.name())
                key to value
            } else null
        }.toMap()
    }

    companion object {
        private const val TAG = "McpHttpServer"
    }
}
