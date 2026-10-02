// Package pty 打开伪终端，供网页终端与 SSH 共用。
//
// 原理：打开 /dev/ptmx 得到主端，解锁并取得从端编号后打开 /dev/pts/<编号>；
// shell 进程以从端为标准输入输出与控制终端，程序读写主端即与 shell 交互。
package pty

import (
	"fmt"
	"os"
	"syscall"

	"golang.org/x/sys/unix"
)

// Open 返回伪终端的主端与从端。
func Open() (ptmx *os.File, tty *os.File, err error) {
	ptmx, err = os.OpenFile("/dev/ptmx", os.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		return nil, nil, err
	}
	fd := int(ptmx.Fd())
	if err = unix.IoctlSetPointerInt(fd, unix.TIOCSPTLCK, 0); err != nil { // 解锁从端
		ptmx.Close()
		return nil, nil, err
	}
	n, err := unix.IoctlGetInt(fd, unix.TIOCGPTN) // 取得从端编号
	if err != nil {
		ptmx.Close()
		return nil, nil, err
	}
	tty, err = os.OpenFile(fmt.Sprintf("/dev/pts/%d", n), os.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		ptmx.Close()
		return nil, nil, err
	}
	return ptmx, tty, nil
}

// SetSize 设置终端窗口大小（行、列），shell 中的程序据此排版。
func SetSize(ptmx *os.File, rows, cols uint16) error {
	return unix.IoctlSetWinsize(int(ptmx.Fd()), unix.TIOCSWINSZ, &unix.Winsize{Row: rows, Col: cols})
}
