//go:build !(arm || 386 || mips || mipsle)

package airplay

import "github.com/alicebob/alac"

// decoder 把一个 ALAC 包解码为 16 位小端 PCM。
type decoder interface{ Decode([]byte) []byte }

func newALAC() (decoder, error) { return alac.New() }
