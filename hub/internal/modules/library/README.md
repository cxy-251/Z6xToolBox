# 资源库模块（library）

与 omni-deck 兼容的资源库服务。目录结构、游戏 ID、存档位置和网页游戏接口均与 omni-deck 一致，
因此同一个资源库（机身存储或 U 盘）可以在 omni-deck 和 hub 之间直接搬动，存档随游戏目录一起转移。

## 与 omni-deck 对应的代码

| hub | omni-deck |
|---|---|
| roots.go：标记文件、目录结构 | omni/core/library.py |
| games.go：registerRPG / registerSLG | omni/features/games/registry.py |
| games.go：/game、/save、/api/save、/api/readdir、/api/patch、/icon | omni/features/games/api.py |
| assets/rpg-runtime.js | web/players/rpg-runtime.js（副本） |

omni-deck 修改了上述规则或 rpg-runtime.js 时，hub 需同步更新。更新 runtime：

    cp ~/Games/omni-deck/web/players/rpg-runtime.js hub/internal/modules/library/assets/
