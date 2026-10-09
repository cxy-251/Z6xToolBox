//go:build arm || 386 || mips || mipsle

package airplay

import "errors"

// 所用的 ALAC 解码库在 32 位平台上无法编译（常量 0xFFFFFFFF 超出 int 范围），32 位版本只能播放未压缩的 PCM。
// 苹果设备通常发送 ALAC，因此 32 位版本的 AirPlay 音箱实际上不可用；需要 AirPlay 请用 arm64 版本。

type decoder interface{ Decode([]byte) []byte }

func newALAC() (decoder, error) {
	return nil, errors.New("32 位版本不支持 ALAC 音频，请使用 arm64 版本")
}
