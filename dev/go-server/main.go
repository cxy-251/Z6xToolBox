// 投影仪上的 Go 测试服务（z6x_go_server）。
// 原始源码没有保留，这是按设备上二进制的实际输出重写的等价版本：监听 :8088，返回运行环境信息。
package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"runtime"
)

func main() {
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		host, _ := os.Hostname()
		fmt.Fprintf(w, "Z6X Pro Native Go Server\nOS: %s\nArch: %s\nHostname: %s\nGoVersion: %s\n",
			runtime.GOOS, runtime.GOARCH, host, runtime.Version())
	})
	log.Println("Native Go HTTP server listening on :8088")
	log.Fatal(http.ListenAndServe(":8088", nil))
}
