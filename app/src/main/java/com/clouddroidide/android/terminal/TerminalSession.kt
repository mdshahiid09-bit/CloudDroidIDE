package com.clouddroidide.android.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * Interactive PTY session backed by libclouddroid_pty.so with an ANSI/VT stream buffer.
 */
class TerminalSession(
    private val command: String,
    private val args: Array<String>,
    private val cwd: String,
    private val env: Array<String>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pid: Int = -1
    private var masterFd: Int = -1
    private var readerJob: Job? = null

    private val lineBuffer = StringBuilder()
    private val lines = ArrayDeque<String>()
    private val maxLines = 1500
    private val ansiEscape = StringBuilder()
    private var inEscape = false
    private var inOsc = false

    private val _screenText = MutableStateFlow("")
    val screenText: StateFlow<String> = _screenText.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    fun start(rows: Int = 30, cols: Int = 90) {
        if (_isRunning.value) return
        val proc = NativePty.createSubprocess(command, cwd, args, env, rows, cols)
        if (proc == null || proc.size < 2) {
            appendSystemLine("[pty] Failed to spawn subprocess: $command")
            return
        }
        pid = proc[0]
        masterFd = proc[1]
        _isRunning.value = true
        _exitCode.value = null

        readerJob = scope.launch {
            val buf = ByteArray(4096)
            while (_isRunning.value) {
                val n = NativePty.readFd(masterFd, buf, buf.size)
                if (n <= 0) break
                val chunk = String(buf, 0, n, StandardCharsets.UTF_8)
                processPtyOutput(chunk)
            }
            val code = NativePty.waitFor(pid)
            _exitCode.value = code
            _isRunning.value = false
            NativePty.closeFd(masterFd)
            masterFd = -1
            appendSystemLine("\n[process exited with code $code]")
        }
    }

    fun sendCommand(cmd: String) {
        sendRaw("$cmd\n")
    }

    fun sendRaw(text: String) {
        val fd = masterFd
        if (fd < 0 || !_isRunning.value) return
        scope.launch {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            NativePty.writeFd(fd, bytes, bytes.size)
        }
    }

    fun sendControlChar(letter: Char) {
        val code = (letter.uppercaseChar().code - 64).coerceIn(1, 26)
        val fd = masterFd
        if (fd < 0 || !_isRunning.value) return
        scope.launch {
            NativePty.writeFd(fd, byteArrayOf(code.toByte()), 1)
        }
    }

    fun resize(rows: Int, cols: Int) {
        val fd = masterFd
        if (fd >= 0 && _isRunning.value) {
            NativePty.setPtyWindowSize(fd, rows, cols)
        }
    }

    fun clearScreen() {
        synchronized(this) {
            lines.clear()
            lineBuffer.clear()
            _screenText.value = ""
        }
    }

    fun stop() {
        _isRunning.value = false
        if (pid > 0) {
            NativePty.killProcess(pid)
        }
        if (masterFd >= 0) {
            NativePty.closeFd(masterFd)
            masterFd = -1
        }
        readerJob?.cancel()
        scope.cancel()
    }

    private fun appendSystemLine(msg: String) {
        processPtyOutput("$msg\n")
    }

    @Synchronized
    private fun processPtyOutput(chunk: String) {
        for (ch in chunk) {
            if (inOsc) {
                if (ch == '\u0007') {
                    inOsc = false
                }
                continue
            }
            if (inEscape) {
                ansiEscape.append(ch)
                if (ansiEscape.length == 1 && ch == ']') {
                    inOsc = true
                    inEscape = false
                    ansiEscape.clear()
                } else if (ansiEscape.length == 1 && ch != '[') {
                    inEscape = false
                    ansiEscape.clear()
                } else if (ch in '@'..'~' && ansiEscape.length > 1) {
                    val seq = ansiEscape.toString()
                    if (seq == "[2J" || seq == "[3J") {
                        lines.clear()
                        lineBuffer.clear()
                    } else if (seq == "[K" || seq == "[0K") {
                        // Clear to end of current line handled by lineBuffer reset when carriage returned
                    }
                    inEscape = false
                    ansiEscape.clear()
                }
                continue
            }

            when (ch) {
                '\u001B' -> {
                    inEscape = true
                    ansiEscape.clear()
                }
                '\r' -> {
                    lineBuffer.clear()
                }
                '\n' -> {
                    lines.addLast(lineBuffer.toString())
                    while (lines.size > maxLines) {
                        lines.removeFirst()
                    }
                    lineBuffer.clear()
                }
                '\b', '\u007F' -> {
                    if (lineBuffer.isNotEmpty()) {
                        lineBuffer.deleteCharAt(lineBuffer.length - 1)
                    }
                }
                '\t' -> {
                    lineBuffer.append("    ")
                }
                else -> {
                    if (ch.code >= 32) {
                        lineBuffer.append(ch)
                    }
                }
            }
        }
        val rendered = buildString {
            for (line in lines) {
                append(line).append('\n')
            }
            append(lineBuffer)
        }
        _screenText.value = rendered
    }
}
