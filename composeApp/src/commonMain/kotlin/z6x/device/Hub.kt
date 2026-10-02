package z6x.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** z6x-hub 的主端口和 WebDAV 端口，与 hub/hub.example.yaml 一致。 */
const val HubPort = 8090

/**
 * /api/health 的返回内容。@Serializable 让编译器插件自动生成 JSON 解析代码，
 * 字段名不一致时用 @SerialName 指定，相当于 C# 的 [JsonPropertyName]。
 */
@Serializable
data class HubHealth(
    val version: String,
    @SerialName("uptime_sec") val uptimeSec: Long,
    val modules: List<HubModule> = emptyList(),
)

@Serializable
data class HubModule(val name: String, val state: String, val error: String? = null)

/** 未知字段直接忽略：hub 以后增加字段，旧版工具箱也能正常解析。 */
val hubJson = Json { ignoreUnknownKeys = true }

fun parseHubHealth(text: String): HubHealth = hubJson.decodeFromString(HubHealth.serializer(), text)

/** 从 ADB 地址（如 192.168.0.109:5555）中取出主机部分。 */
fun hostOf(address: String): String = address.substringBefore(':')

/**
 * 与 hub 交互的能力。commonMain 只定义接口：
 * 桌面版通过 HTTP 查询状态、运行 hub/deploy.sh 部署；以后的安卓版可只实现查询。
 */
interface HubControl {
    /** 查询 hub 状态；hub 未运行或连不上时抛出异常。 */
    suspend fun health(host: String): HubHealth

    /** 当前平台能否部署（需要本机有 Go 工具链和项目源码）。 */
    val canDeploy: Boolean

    /** 编译并部署 hub，每输出一行就回调一次，返回退出码。 */
    suspend fun deploy(address: String, onLine: (String) -> Unit): Int

    /** 用系统浏览器打开 hub 首页。 */
    fun openInBrowser(host: String)
}
