package org.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable

// main fun to run mcp server
fun runMcpServer() {
    val baseURL = "http://127.0.0.1:8000"

    // create a http client
    val httpClient = HttpClient {
        defaultRequest {
            url(baseURL)
            headers {
                append("Accept", "application/geo+json")
                append("User-Agent", "TushareApiClient/1.0")
            }
            contentType(ContentType.Application.Json)
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    // create a mcp server
    val server = Server(
        Implementation(
            name = "stock-analyzer",
            version = "1.0.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
    )

    server.addTool(
        name = "get_current_date",
        description = """
            获取当前的日期，格式为'yyyy-MM-dd'
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                // 没有输入参数，所以 properties 为空
            },
            required = emptyList()
        )
    ) {
        val currentDate = java.time.LocalDate.now().toString()
        CallToolResult(
            content = listOf(TextContent("Today's date is $currentDate"))
        )
    }

    // register a tool to the server
    server.addTool(
        name = "query_stock_data",
        description = """
            根据股票代码和时间范围查询股票K线数据
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("api_name") {
                    put("type", "string")
                    put("description", "API 名称，例如 'daily'")
                }
                putJsonObject("ts_code") {
                    put("type", "string")
                    put("description", "股票代码，例如 '000001.SZ'")
                }
                putJsonObject("start_date") {
                    put("type", "string")
                    put("description", "开始日期，格式例如 '20250401'")
                }
                putJsonObject("end_date") {
                    put("type", "string")
                    put("description", "结束日期，格式例如 '20250421'")
                }
                putJsonObject("fields") {
                    put("type", "string")
                    put("description", "需要返回的字段列表，逗号分隔。可选")
                }
            },
            required = listOf("ts_code", "start_date", "end_date")
        )
    ) { request ->
        val apiName = request.arguments["api_name"]?.jsonPrimitive?.content ?: "daily"
        val tsCode = request.arguments["ts_code"]?.jsonPrimitive?.content
        val startDate = request.arguments["start_date"]?.jsonPrimitive?.content
        val endDate = request.arguments["end_date"]?.jsonPrimitive?.content
        val fields = request.arguments["fields"]?.jsonPrimitive?.content ?: ""

        if (tsCode == null || startDate == null || endDate == null) {
            /**
             * return@addTool —— 返回到特定的 Lambda 块
             * 因为 server.addTool { ... } 里面是一个 Lambda 块（匿名函数）
             * return@addTool 表示：从当前的 addTool 块内直接返回这个值，而不是退出外部函数。
             */
            return@addTool CallToolResult(
                content = listOf(TextContent("Missing parameters: api_name ts_code, start_date, end_date are required."))
            )
        }
        // 🔥 发送 POST 请求到你的股票接口
        val response = httpClient.post("/api") {
            setBody(
                RequestModel(
                    api_name = apiName,
                    params = mapOf(
                        "ts_code" to tsCode,
                        "start_date" to startDate,
                        "end_date" to endDate
                    ),
                    fields = fields
                )
            )
        }.body<ResponseModel>()

        // 根据返回结果构建响应
        if (response.code == 0) {
            // 成功
            CallToolResult(content = listOf(TextContent(response.data)))
        } else {
            // 失败
            CallToolResult(content = listOf(TextContent("Error from stock API: ${response.msg}")))
        }
    }

    // Create a transport using standard IO for server communication
    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )

    runBlocking {
        server.connect(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}

// 定义请求数据模型
@Serializable
data class RequestModel(
    val api_name: String,
    val params: Map<String, String>,
    val fields: String
)

// 定义响应数据模型
@Serializable
data class ResponseModel(
    val code: Int,
    val msg: String,
    val data: String
)