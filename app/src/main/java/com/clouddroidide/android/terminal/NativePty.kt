package com.clouddroidide.android.terminal

object NativePty {
    init {
        System.loadLibrary("clouddroid_pty")
    }

    @JvmStatic
    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>?,
        env: Array<String>?,
        rows: Int,
        cols: Int
    ): IntArray?

    @JvmStatic
    external fun readFd(fd: Int, buffer: ByteArray, len: Int): Int

    @JvmStatic
    external fun writeFd(fd: Int, buffer: ByteArray, len: Int): Int

    @JvmStatic
    external fun closeFd(fd: Int)

    @JvmStatic
    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int)

    @JvmStatic
    external fun waitFor(pid: Int): Int

    @JvmStatic
    external fun killProcess(pid: Int)
}
