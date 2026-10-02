package core

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"runtime/debug"
	"sync"
	"time"
)

// Module 是每个功能模块要实现的接口。
type Module interface {
	Name() string
	// Start 在 hub 启动时调用一次；需要常驻的工作应自行开协程，并在 ctx 取消时退出。
	Start(ctx context.Context, env *Env) error
	// Stop 在 hub 退出时调用。
	Stop(ctx context.Context) error
	// Routes 把模块的接口挂到 r 上，路径约定为 /api/<模块名>/ 和 /ui/<模块名>/。
	Routes(r Router)
}

// Router 是模块注册路由用的接口。core 会给每个处理函数套上 panic 隔离和鉴权。
type Router interface {
	Handle(pattern string, h http.Handler)
	HandleFunc(pattern string, h func(http.ResponseWriter, *http.Request))
}

// Env 是传给模块的公共依赖。
type Env struct {
	Config *Config
	Log    *slog.Logger
	// Fail 由模块在运行中遇到无法恢复的错误时调用，状态会体现在 /api/health 中。
	Fail func(err error)
}

// State 是模块的运行状态。
type State string

const (
	StateRunning State = "running"
	StateFailed  State = "failed"
	StateStopped State = "stopped"
)

type entry struct {
	mod   Module
	mu    sync.Mutex
	state State
	err   string
	since time.Time
}

func (e *entry) set(s State, err error) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.state, e.since = s, time.Now()
	e.err = ""
	if err != nil {
		e.err = err.Error()
	}
}

// ModuleStatus 是 /api/health 和 /api/modules 中每个模块的状态。
type ModuleStatus struct {
	Name  string `json:"name"`
	State State  `json:"state"`
	Error string `json:"error,omitempty"`
	Since string `json:"since"`
}

func (e *entry) status() ModuleStatus {
	e.mu.Lock()
	defer e.mu.Unlock()
	return ModuleStatus{Name: e.mod.Name(), State: e.state, Error: e.err, Since: e.since.Format(time.RFC3339)}
}

// guard 包装模块的处理函数：处理函数 panic 时只让该模块进入 failed 状态，其他模块不受影响。
func (e *entry) guard(log *slog.Logger, h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				err := fmt.Errorf("处理 %s 时 panic：%v", r.URL.Path, v)
				log.Error("模块 panic", "module", e.mod.Name(), "err", err, "stack", string(debug.Stack()))
				e.set(StateFailed, err)
				http.Error(w, "模块内部错误", http.StatusInternalServerError)
			}
		}()
		h.ServeHTTP(w, r)
	})
}

// moduleMux 记录模块注册的路由，并统一套上 guard。
type moduleMux struct {
	inner *http.ServeMux
	e     *entry
	log   *slog.Logger
	token string
}

// Handle 注册路由：先鉴权，再进入带 panic 隔离的模块处理函数。
func (m *moduleMux) Handle(pattern string, h http.Handler) {
	m.inner.Handle(pattern, RequireToken(m.token, m.e.guard(m.log, h)))
}

func (m *moduleMux) HandleFunc(pattern string, h func(http.ResponseWriter, *http.Request)) {
	m.Handle(pattern, http.HandlerFunc(h))
}
