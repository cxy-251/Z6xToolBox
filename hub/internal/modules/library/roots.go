package library

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// 与 omni-deck 的 omni/core/library.py 保持一致：标记文件名、两棵顶层目录、各分类的相对路径。
// 同一个资源库在 omni-deck 和 hub 中都能识别，游戏和媒体可以在两边之间直接搬动。
const (
	markerFile    = "omnilibrary.json"
	layoutVersion = 1
	gamesRoot     = "standalone_games"
	mediaRoot     = "media_library"
	defaultDir    = "omni_library"
)

var gameCategories = []string{"rpg", "retro", "slg", "flash", "steam", "renpy", "unity", "godot", "unreal", "wine", "3ds", "app"}

var mediaLayout = map[string]string{
	"manga": "manga", "novels": "novels", "novels.standard": "novels/standard", "novels.nsfw": "novels/nsfw",
	"audio": "audio", "audio.standard": "audio/standard", "audio.nsfw": "audio/nsfw",
	"shortvideo": "shortvideo", "shortvideo.kuaishou": "shortvideo/快手", "shortvideo.douyin": "shortvideo/抖音",
	"shortvideo.tiktok": "shortvideo/TikTok", "docs": "docs", "shared": "shared",
}

// layout 返回逻辑键到相对路径的完整对照表（games.rpg → standalone_games/rpg_games 等）。
func layout() map[string]string {
	m := map[string]string{}
	for _, c := range gameCategories {
		m["games."+c] = filepath.Join(gamesRoot, c+"_games")
	}
	for k, v := range mediaLayout {
		m["media."+k] = filepath.Join(mediaRoot, filepath.FromSlash(v))
	}
	return m
}

// Lib 是一个资源库根目录。
type Lib struct {
	ID       string `json:"id"`
	Label    string `json:"label"`
	Path     string `json:"path"`
	Location string `json:"location"` // internal（机身存储）、usb（U 盘）或 config（配置中指定）
}

type marker struct {
	ID            string `json:"id"`
	Label         string `json:"label"`
	LayoutVersion int    `json:"layout_version"`
	Created       string `json:"created"`
}

func readMarker(root string) (marker, bool) {
	var m marker
	b, err := os.ReadFile(filepath.Join(root, markerFile))
	if err != nil || json.Unmarshal(b, &m) != nil || m.ID == "" {
		return m, false
	}
	return m, true
}

// discover 按优先级找出所有资源库：配置中指定的、机身存储的 omni_library、各 U 盘根目录或其下的 omni_library。
// 同一 id 只保留第一个（与 omni-deck 一致，按优先级合并）。
func discover(configured []string, internal, storage string) []Lib {
	type cand struct{ path, loc string }
	var cands []cand
	for _, p := range configured {
		cands = append(cands, cand{p, "config"})
	}
	cands = append(cands, cand{filepath.Join(internal, defaultDir), "internal"})
	ents, _ := os.ReadDir(storage)
	for _, e := range ents {
		n := e.Name()
		if n == "emulated" || n == "self" || n == "primary" || strings.HasPrefix(n, ".") {
			continue
		}
		vol := filepath.Join(storage, n)
		cands = append(cands, cand{vol, "usb"}, cand{filepath.Join(vol, defaultDir), "usb"})
	}
	var out []Lib
	seen := map[string]bool{}
	for _, c := range cands {
		m, ok := readMarker(c.path)
		if !ok || seen[m.ID] {
			continue
		}
		seen[m.ID] = true
		label := m.Label
		if label == "" {
			label = filepath.Base(c.path)
		}
		out = append(out, Lib{ID: m.ID, Label: label, Path: c.path, Location: c.loc})
	}
	return out
}

// initLibrary 在 root 建立资源库：写标记文件、建好全部分类目录。已是资源库时只补齐缺少的目录。
func initLibrary(root, label string) (Lib, error) {
	if err := os.MkdirAll(root, 0o755); err != nil {
		return Lib{}, err
	}
	m, ok := readMarker(root)
	if !ok {
		b := make([]byte, 4)
		rand.Read(b)
		m = marker{ID: hex.EncodeToString(b), Label: label, LayoutVersion: layoutVersion, Created: time.Now().Format("2006-01-02 15:04:05")}
		data, _ := json.MarshalIndent(m, "", "  ")
		if err := os.WriteFile(filepath.Join(root, markerFile), data, 0o644); err != nil {
			return Lib{}, err
		}
	}
	rels := make([]string, 0, len(layout()))
	for _, rel := range layout() {
		rels = append(rels, rel)
	}
	sort.Strings(rels)
	for _, rel := range rels {
		if err := os.MkdirAll(filepath.Join(root, rel), 0o755); err != nil {
			return Lib{}, err
		}
	}
	return Lib{ID: m.ID, Label: m.Label, Path: root}, nil
}

var errOutside = errors.New("路径越出资源库范围")

// within 把相对路径接到 base 下，并确认结果没有越出 base。
func within(base, rel string) (string, error) {
	base = filepath.Clean(base)
	p := filepath.Join(base, filepath.FromSlash(rel))
	if p != base && !strings.HasPrefix(p, base+string(filepath.Separator)) {
		return "", errOutside
	}
	return p, nil
}

// resolveCI 与 omni-deck 的 resolve_case_insensitive_path 相同：逐级按不区分大小写的方式匹配文件名。
// RPG Maker 游戏多在 Windows 上制作，代码里的文件名大小写常与实际文件不一致。
func resolveCI(base, rel string) (string, error) {
	p, err := within(base, rel)
	if err != nil {
		return "", err
	}
	if _, err := os.Stat(p); err == nil {
		return p, nil
	}
	cur := filepath.Clean(base)
	for _, part := range strings.Split(filepath.ToSlash(strings.TrimPrefix(p, cur)), "/") {
		if part == "" {
			continue
		}
		next := filepath.Join(cur, part)
		if _, err := os.Lstat(next); err != nil {
			ents, rerr := os.ReadDir(cur)
			if rerr != nil {
				return p, nil
			}
			lower := strings.ToLower(part)
			for _, e := range ents {
				if strings.ToLower(e.Name()) == lower {
					next = filepath.Join(cur, e.Name())
					break
				}
			}
		}
		cur = next
	}
	return cur, nil
}

func fmtSize(n int64) string {
	switch {
	case n >= 1<<30:
		return fmt.Sprintf("%.1f GB", float64(n)/(1<<30))
	case n >= 1<<20:
		return fmt.Sprintf("%.1f MB", float64(n)/(1<<20))
	default:
		return fmt.Sprintf("%d KB", n>>10)
	}
}
