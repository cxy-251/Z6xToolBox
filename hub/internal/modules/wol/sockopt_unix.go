package wol

import (
	"net"
	"syscall"
)

// setBroadcast 为 UDP 套接字开启 SO_BROADCAST，否则向广播地址发送会被拒绝。
func setBroadcast(c *net.UDPConn) error {
	raw, err := c.SyscallConn()
	if err != nil {
		return err
	}
	var serr error
	err = raw.Control(func(fd uintptr) {
		serr = syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_BROADCAST, 1)
	})
	if err != nil {
		return err
	}
	return serr
}
