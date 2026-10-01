package z6x.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import z6x.framework.Category
import z6x.framework.Module
import z6x.framework.Scope

/** 模块在目录里的位置。 */
data class Located(val scope: Scope, val category: Category, val module: Module) {
    val breadcrumb get() = "${scope.icon} ${scope.label}  ›  ${category.icon} ${category.name}"
}

/**
 * 整个应用的界面状态。属性用 `by mutableStateOf(...)` 委托：读它的 @Composable 会被追踪，
 * 写它时 Compose 自动重组这些界面。这取代了 Avalonia 里的 INotifyPropertyChanged。
 */
class AppState(val scopes: List<Scope>) {
    val index: Map<String, Located> = buildMap {
        for (s in scopes) for (c in s.categories) for (m in c.modules) {
            require(m.id !in this) { "模块 id 重复：${m.id}" }
            put(m.id, Located(s, c, m))
        }
    }

    var scope by mutableStateOf(scopes.first())
        private set
    var current by mutableStateOf(scopes.first().categories.firstOrNull()?.modules?.firstOrNull())
        private set
    var query by mutableStateOf("")
    var showDevice by mutableStateOf(false)
    var toast by mutableStateOf("")

    val searchFocus = FocusRequester()

    fun selectScope(s: Scope) {
        scope = s
        current = s.categories.firstOrNull()?.modules?.firstOrNull()
        showDevice = false
    }

    fun open(id: String) {
        val hit = index[id] ?: return
        scope = hit.scope
        current = hit.module
        showDevice = false
    }

    /** 搜索标题、关键词、概述和命令，大小写不敏感。 */
    fun search(): List<Located> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return index.values.filter { (_, _, m) ->
            m.title.contains(q, true) || m.keywords.contains(q, true) || m.overview.contains(q, true) ||
                m.steps.any { it.command.contains(q, true) || it.title.contains(q, true) }
        }
    }

    val moduleCount get() = index.size
}
