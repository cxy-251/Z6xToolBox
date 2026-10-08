package library

import (
	_ "embed"
	"encoding/json"
	"io"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"

	"z6x/hub/internal/core"
)

// rpgRuntime 是 omni-deck 的 web/players/rpg-runtime.js 副本：NW.js 兼容层，
// 把 RPG Maker 的存档读写接到 /save、/api/save 接口上。omni-deck 在 Qt 窗口中于文档创建时注入它；
// hub 在返回游戏的 HTML 时插入同一个脚本，因此任何浏览器中的存档都写进游戏目录的 save/。
// 更新方法见 hub/internal/modules/library/README.md。
//
//go:embed assets/rpg-runtime.js
var rpgRuntime []byte

// Game 与 omni-deck 注册表中的条目对应：ID 即游戏文件夹名，存档目录规则也相同。
type Game struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Type    string `json:"type"` // rpg 或 slg
	Lib     string `json:"lib"`
	Entry   string `json:"entry"` // 相对 root 的入口页面
	Size    int64  `json:"-"`
	root    string // 网页文件所在目录
	saveDir string
	icon    string
}

// gameMeta 是游戏文件夹中可选的 omni.json（字段与 omni-deck 相同，这里只用到其中几项）。
type gameMeta struct {
	Name   string `json:"name"`
	Hidden bool   `json:"hidden"`
	Icon   string `json:"icon"`
}

func readMeta(folder string) gameMeta {
	var m gameMeta
	if b, err := os.ReadFile(filepath.Join(folder, "omni.json")); err == nil {
		json.Unmarshal(b, &m)
	}
	return m
}

func firstExisting(base string, cands ...string) string {
	for _, c := range cands {
		if c == "" {
			continue
		}
		p := filepath.Join(base, c)
		if _, err := os.Stat(p); err == nil {
			return p
		}
	}
	return ""
}

// registerRPG 对应 omni-deck 的 register_rpg：index.html 可在根目录、www/ 或 data/www/ 下。
func registerRPG(folder, id string, m gameMeta) *Game {
	for _, sub := range []string{"", "www", filepath.Join("data", "www")} {
		www := filepath.Join(folder, sub)
		if _, err := os.Stat(filepath.Join(www, "index.html")); err == nil {
			icon := firstExisting(folder, m.Icon)
			if icon == "" {
				icon = firstExisting(www, filepath.Join("icon", "icon.png"))
			}
			return &Game{ID: id, Type: "rpg", Entry: "index.html", root: www, saveDir: filepath.Join(www, "save"), icon: icon}
		}
	}
	return nil
}

// registerSLG 对应 omni-deck 的 register_slg（网页版部分）。
func registerSLG(folder, id string, m gameMeta) *Game {
	entry := ""
	for _, c := range []string{"index.html", "game.html", filepath.Join("www", "index.html")} {
		if _, err := os.Stat(filepath.Join(folder, c)); err == nil {
			entry = filepath.ToSlash(c)
			break
		}
	}
	if entry == "" {
		filepath.WalkDir(folder, func(p string, d fs.DirEntry, err error) error {
			if err == nil && !d.IsDir() && d.Name() == "index.html" {
				rel, _ := filepath.Rel(folder, p)
				entry = filepath.ToSlash(rel)
				return fs.SkipAll
			}
			return nil
		})
	}
	if entry == "" {
		return nil
	}
	icon := firstExisting(folder, m.Icon, "icon.png", "cover.png", "web-presplash.jpg", filepath.Join("icons", "icon-512x512.png"),
		"icon.jpg", "cover.jpg", filepath.Join("icon", "icon.png"), filepath.Join("www", "icon", "icon.png"))
	return &Game{ID: id, Type: "slg", Entry: entry, root: folder, saveDir: filepath.Join(folder, "save"), icon: icon}
}

// scanGames 扫描所有资源库的 rpg_games 和 slg_games。同名游戏以优先级高的库为准。
func scanGames(libs []Lib) map[string]*Game {
	out := map[string]*Game{}
	for _, lib := range libs {
		for _, cat := range []string{"rpg", "slg"} {
			dir := filepath.Join(lib.Path, gamesRoot, cat+"_games")
			ents, err := os.ReadDir(dir)
			if err != nil {
				continue
			}
			for _, e := range ents {
				if !e.IsDir() || strings.HasPrefix(e.Name(), ".") {
					continue
				}
				id := e.Name()
				if _, dup := out[id]; dup {
					continue
				}
				folder := filepath.Join(dir, id)
				m := readMeta(folder)
				if m.Hidden {
					continue
				}
				var g *Game
				if cat == "rpg" {
					g = registerRPG(folder, id, m)
				} else {
					g = registerSLG(folder, id, m)
				}
				if g == nil {
					continue
				}
				g.Name = m.Name
				if g.Name == "" {
					g.Name = id
				}
				g.Lib = lib.ID
				out[id] = g
			}
		}
	}
	return out
}

// lookupGame 不区分大小写地查找游戏；找不到时重新扫描一次（游戏可能刚复制进来）。
func (m *Module) lookupGame(id string) *Game {
	for attempt := 0; attempt < 2; attempt++ {
		games := m.games(attempt == 1)
		if g, ok := games[id]; ok {
			return g
		}
		low := strings.ToLower(id)
		for gid, g := range games {
			if strings.ToLower(gid) == low {
				return g
			}
		}
	}
	return nil
}

var headTag = regexp.MustCompile(`(?i)<head[^>]*>`)

// injectRuntime 在 <head> 之后插入 rpg-runtime.js，使它先于游戏自己的脚本运行。
func injectRuntime(page []byte) []byte {
	tag := []byte(`<script src="/z6x/rpg-runtime.js"></script>`)
	if loc := headTag.FindIndex(page); loc != nil {
		return append(append(append([]byte{}, page[:loc[1]]...), tag...), page[loc[1]:]...)
	}
	return append(tag, page...)
}

func (m *Module) gameRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/games", func(w http.ResponseWriter, req *http.Request) {
		games := m.games(req.URL.Query().Has("refresh"))
		list := make([]*Game, 0, len(games))
		for _, g := range games {
			list = append(list, g)
		}
		sort.Slice(list, func(i, j int) bool { return list[i].ID < list[j].ID })
		core.WriteJSON(w, list)
	})
	r.HandleFunc("GET /game/{id}/{rest...}", m.serveGameFile)
	r.HandleFunc("GET /icon/{id}", func(w http.ResponseWriter, req *http.Request) {
		if g := m.lookupGame(req.PathValue("id")); g != nil && g.icon != "" {
			w.Header().Set("Cache-Control", "public, max-age="+strconv.Itoa(cacheFileSec))
			http.ServeFile(w, req, g.icon)
			return
		}
		w.Header().Set("Content-Type", "image/svg+xml")
		io.WriteString(w, defaultIcon)
	})
	r.HandleFunc("GET /z6x/rpg-runtime.js", func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
		w.Header().Set("Cache-Control", "no-cache")
		w.Write(rpgRuntime)
	})
	// 以下几个接口与 omni-deck 完全相同，供 rpg-runtime.js 调用。
	r.HandleFunc("GET /save/{id}/{file}", m.readSave)
	r.HandleFunc("POST /api/save/{id}", m.writeSave)
	r.HandleFunc("DELETE /api/save/{id}", m.deleteSave)
	r.HandleFunc("GET /api/readdir", m.readdir)
	r.HandleFunc("GET /api/patch/{name}", m.patch)
}

func (m *Module) serveGameFile(w http.ResponseWriter, req *http.Request) {
	g := m.lookupGame(req.PathValue("id"))
	if g == nil {
		http.NotFound(w, req)
		return
	}
	target, err := resolveCI(g.root, req.PathValue("rest"))
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}
	if fi, err := os.Stat(target); err == nil && fi.IsDir() {
		target = filepath.Join(target, "index.html")
	}
	fi, err := os.Stat(target)
	if err != nil || fi.IsDir() {
		http.NotFound(w, req)
		return
	}
	if strings.EqualFold(filepath.Ext(target), ".html") {
		page, err := os.ReadFile(target)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-cache")
		w.Write(injectRuntime(page))
		return
	}
	f, err := os.Open(target)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer f.Close()
	// 游戏素材不会改变，缓存一天；入口页面（上面的 .html）不缓存，以便运行时更新后立即生效
	w.Header().Set("Cache-Control", "public, max-age="+strconv.Itoa(cacheAssetSec))
	http.ServeContent(w, req, fi.Name(), fi.ModTime(), f)
}

// saveTarget 只取文件名部分，存档只能落在游戏的 save/ 目录中。
func (m *Module) saveTarget(req *http.Request, name string) (string, *Game) {
	g := m.lookupGame(req.PathValue("id"))
	base := filepath.Base(name)
	if g == nil || base == "" || base == "." || base == "/" || base == ".." {
		return "", g
	}
	return filepath.Join(g.saveDir, base), g
}

func (m *Module) readSave(w http.ResponseWriter, req *http.Request) {
	target, _ := m.saveTarget(req, req.PathValue("file"))
	if target == "" {
		http.NotFound(w, req)
		return
	}
	if _, err := os.Stat(target); err != nil {
		http.NotFound(w, req)
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	http.ServeFile(w, req, target)
}

func (m *Module) writeSave(w http.ResponseWriter, req *http.Request) {
	target, g := m.saveTarget(req, req.URL.Query().Get("file"))
	if target == "" {
		core.WriteError(w, http.StatusNotFound, "游戏不存在或文件名为空")
		return
	}
	data, err := io.ReadAll(http.MaxBytesReader(w, req.Body, 32<<20))
	if err != nil {
		core.WriteError(w, http.StatusBadRequest, "存档过大或读取失败")
		return
	}
	// 先写临时文件再改名：写到一半断电也不会损坏原有存档。
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err == nil {
		tmp := target + ".z6x-tmp"
		if err = os.WriteFile(tmp, data, 0o644); err == nil {
			err = os.Rename(tmp, target)
		}
		if err != nil {
			m.env.Log.Error("存档写入失败", "game", g.ID, "err", err)
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
	}
	core.WriteJSON(w, map[string]string{"status": "ok"})
}

func (m *Module) deleteSave(w http.ResponseWriter, req *http.Request) {
	target, _ := m.saveTarget(req, req.URL.Query().Get("file"))
	if target != "" {
		os.Remove(target)
	}
	core.WriteJSON(w, map[string]string{"status": "ok"})
}

// readdir 是 rpg-runtime.js 中 fs.readdirSync 的后端：列出游戏目录下的子目录。只允许带游戏 id 的请求。
func (m *Module) readdir(w http.ResponseWriter, req *http.Request) {
	q := req.URL.Query()
	g := m.lookupGame(q.Get("game_id"))
	if g == nil || q.Get("path") == "" {
		core.WriteJSON(w, []string{})
		return
	}
	target, err := resolveCI(g.root, strings.TrimLeft(q.Get("path"), "/"))
	if err != nil {
		core.WriteJSON(w, []string{})
		return
	}
	ents, err := os.ReadDir(target)
	names := []string{}
	if err == nil {
		for _, e := range ents {
			names = append(names, e.Name())
		}
	}
	core.WriteJSON(w, names)
}

// patch 返回游戏目录下的 adapter.js（单个游戏的兼容补丁），没有时返回空补丁。
func (m *Module) patch(w http.ResponseWriter, req *http.Request) {
	w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache")
	id := strings.TrimSuffix(req.PathValue("name"), ".js")
	if g := m.lookupGame(id); g != nil {
		if b, err := os.ReadFile(filepath.Join(g.root, "adapter.js")); err == nil {
			w.Write(b)
			return
		}
	}
	io.WriteString(w, "// default adapter\n")
}

const defaultIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="12" fill="#2f6fed"/><text x="32" y="42" font-size="30" text-anchor="middle" fill="#fff">🎮</text></svg>`
