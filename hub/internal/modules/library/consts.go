package library

import "time"

// 资源库模块的内部常量：技术细节，不在配置文件中开放（改错了容易出问题，也没有调整的必要）。
// 用户可以调整的参数在 Config 中（hub.yaml 的 library 节），默认值见 DefaultConfig。

// 浏览器缓存时长（秒，用于 Cache-Control: max-age）
const (
	cacheAssetSec = 24 * 3600     // 游戏素材、漫画图片：内容不会改变
	cacheFileSec  = 3600          // 资源库中的任意文件（/lib/…）、游戏图标
	cacheCoverSec = 7 * 24 * 3600 // 缩小后的音乐封面（缓存文件名含尺寸，改尺寸后地址内容随之更新）
)

// 接口一次最多返回的条数（防止一次请求拖垮手机）
const (
	maxItemsPerRequest  = 200 // 短视频作品、视频列表
	maxMatrixPerRequest = 500 // 多联放映的视频列表
)

const (
	// txtChunkLines：没有章节标题的 TXT 小说，每多少行分为一个「部分」
	txtChunkLines = 300
	// firstScanWait：首次媒体扫描进行中时，请求最多等待多久（手机上扫描约 40 秒到 2 分钟）
	firstScanWait = 3 * time.Minute
	// libsCacheTTL / gamesCacheTTL：资源库位置与游戏列表的缓存时间（游戏列表要读每个游戏的目录，较慢）
	libsCacheTTL  = 10 * time.Second
	gamesCacheTTL = 30 * time.Second
)
