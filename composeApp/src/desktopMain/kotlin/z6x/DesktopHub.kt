package z6x

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import z6x.device.HubControl
import z6x.device.HubHealth
import z6x.device.HubPort
import z6x.device.parseHubHealth
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** 桌面版的 HubControl：用 JDK 自带的 HttpClient 查询状态，用 ProcessBuilder 运行部署脚本。 */
class DesktopHub(private val projectDir: File = File(".").absoluteFile) : HubControl {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private val script = File(projectDir, "hub/deploy.sh")

    override suspend fun health(host: String): HubHealth = withContext(Dispatchers.IO) {
        val req = HttpRequest.newBuilder(URI("http://$host:$HubPort/api/health")).timeout(Duration.ofSeconds(5)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        check(res.statusCode() == 200) { "HTTP ${res.statusCode()}" }
        parseHubHealth(res.body())
    }

    override val canDeploy: Boolean get() = script.canExecute()

    override suspend fun deploy(address: String, onLine: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        val proc = ProcessBuilder(script.path, address)
            .directory(projectDir)
            .redirectErrorStream(true) // 标准错误并入标准输出，按顺序显示
            .start()
        proc.inputStream.bufferedReader().useLines { lines ->
            // deploy.sh 按 IP 在 hub/devices/devices.txt 中找到设备名；token 不会出现在输出中
            lines.forEach(onLine)
        }
        proc.waitFor()
    }

    override suspend fun openInBrowser(host: String) {
        val code = withContext(Dispatchers.IO) { runCatching { loginCode(host) }.getOrNull() }
        browse(URI(if (code != null) "http://$host:$HubPort/login/code?c=$code" else "http://$host:$HubPort/"))
    }

    /** 按 hub/devices/devices.txt 找到该地址对应的设备，读取其配置中的 token，申请一次性登录码。 */
    private fun loginCode(host: String): String? {
        val name = File(projectDir, "hub/devices/devices.txt").readLines()
            .map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 2 && !it[0].startsWith("#") && it[1].substringBefore(':') == host }?.get(0) ?: return null
        val token = File(projectDir, "hub/devices/$name.yaml").readLines()
            .firstOrNull { it.startsWith("token:") }?.substringAfter(':')?.trim()?.trim('"') ?: return null
        val req = HttpRequest.newBuilder(URI("http://$host:$HubPort/api/login-code")).timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer $token").POST(HttpRequest.BodyPublishers.noBody()).build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) return null
        return Regex("\"code\"\\s*:\\s*\"([0-9a-f]+)\"").find(res.body())?.groupValues?.get(1)
    }

    private fun browse(uri: URI) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
        } else {
            ProcessBuilder("xdg-open", uri.toString()).start()
        }
    }
}
