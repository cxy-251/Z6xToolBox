package z6x

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import z6x.content.Content
import z6x.device.DeviceShell
import z6x.ui.AppShell
import z6x.ui.AppState
import z6x.ui.Z6xTheme

/** 应用入口界面，桌面端和以后的安卓端共用。平台相关的能力（设备连接、剪贴板）由调用方传进来。 */
@Composable
fun App(state: AppState = remember { AppState(Content.scopes) }, shell: DeviceShell, onCopy: (String) -> Unit) {
    Z6xTheme {
        AppShell(state, shell, onCopy)
    }
}
