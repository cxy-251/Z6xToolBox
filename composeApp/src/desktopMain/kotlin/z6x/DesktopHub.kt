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

    override fun openInBrowser(host: String) {
        val uri = URI("http://$host:$HubPort/")
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
        } else {
            ProcessBuilder("xdg-open", uri.toString()).start()
        }
    }
}
