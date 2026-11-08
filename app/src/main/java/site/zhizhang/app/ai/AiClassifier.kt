package site.zhizhang.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import site.zhizhang.app.data.LedgerEntry
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiClassifier {
    suspend fun classify(
        entries: List<LedgerEntry>,
        baseUrl: String,
        apiKey: String,
        model: String,
        hideAmount: Boolean
    ): Map<Long, String> = withContext(Dispatchers.IO) {
        require(entries.isNotEmpty()) { "请先选择流水" }
        require(entries.size <= 20) { "每批最多处理 20 笔" }
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://127.0.0.1") || baseUrl.startsWith("http://localhost")) {
            "接口地址必须使用 HTTPS，本机调试地址除外"
        }
        require(apiKey.isNotBlank() && model.isNotBlank()) { "请完整配置 API 地址、密钥和模型" }

        val payload = JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("merchant", entry.merchant)
                    put("type", if (entry.type.name == "INCOME") "收入" else "支出")
                    if (!hideAmount) put("amount", entry.amountCents / 100.0)
                })
            }
        }
        val prompt = """
            你是中文个人账本的分类器。请把下列交易分成最贴切的一个短分类。
            优先使用：餐饮美食、购物消费、交通出行、居家生活、医疗健康、休闲娱乐、教育学习、通讯充值、其他收入、其他。
            只返回 JSON 对象，不要 Markdown，不要解释。格式：{"categories":[{"id":1,"category":"餐饮美食"}]}
            交易数据：$payload
        """.trimIndent()
        val request = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", "你只输出合法 JSON。") })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }
        // json_object is not supported by every compatible endpoint. Retry without it when necessary.
        val response = runCatching { post(baseUrl, apiKey, request) }.recoverCatching { error ->
            if (error is IOException && error.message?.contains("HTTP 400") == true) {
                request.remove("response_format")
                post(baseUrl, apiKey, request)
            } else throw error
        }.getOrThrow()
        parseResponse(response, entries.map { it.id }.toSet())
    }

    private fun post(baseUrl: String, apiKey: String, body: JSONObject): String {
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code：${text.take(300)}")
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(response: String, validIds: Set<Long>): Map<Long, String> {
        val root = JSONObject(response)
        var content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        content = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = if (content.startsWith("[")) JSONArray(content) else {
            val obj = JSONObject(content)
            obj.optJSONArray("categories") ?: obj.optJSONArray("results") ?: JSONArray().put(obj)
        }
        val result = mutableMapOf<Long, String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.optLong("id", -1)
            val category = item.optString("category").trim().take(20)
            if (id in validIds && category.isNotBlank()) result[id] = category
        }
        require(result.isNotEmpty()) { "API 未返回可识别的分类结果" }
        return result
    }
}
