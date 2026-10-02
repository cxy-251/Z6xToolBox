package core

import (
	"fmt"
	"os"
	"sync"
)

// RotatingFile 是按大小轮转的日志文件：超过 maxBytes 时把 hub.log 改名为 hub.log.1，
// 依次后移，最多保留 keep 份旧文件。
type RotatingFile struct {
	mu       sync.Mutex
	path     string
	maxBytes int64
	keep     int
	f        *os.File
	size     int64
}

func OpenRotating(path string, maxBytes int64, keep int) (*RotatingFile, error) {
	r := &RotatingFile{path: path, maxBytes: maxBytes, keep: keep}
	return r, r.open()
}

func (r *RotatingFile) open() error {
	f, err := os.OpenFile(r.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return err
	}
	st, err := f.Stat()
	if err != nil {
		f.Close()
		return err
	}
	r.f, r.size = f, st.Size()
	return nil
}

func (r *RotatingFile) Write(p []byte) (int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.size+int64(len(p)) > r.maxBytes {
		if err := r.rotate(); err != nil {
			return 0, err
		}
	}
	n, err := r.f.Write(p)
	r.size += int64(n)
	return n, err
}

func (r *RotatingFile) rotate() error {
	r.f.Close()
	for i := r.keep - 1; i >= 1; i-- {
		os.Rename(fmt.Sprintf("%s.%d", r.path, i), fmt.Sprintf("%s.%d", r.path, i+1))
	}
	os.Rename(r.path, r.path+".1")
	return r.open()
}

func (r *RotatingFile) Close() error {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.f.Close()
}
