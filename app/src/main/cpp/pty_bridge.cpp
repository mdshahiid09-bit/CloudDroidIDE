#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <signal.h>
#include <string.h>
#include <errno.h>

static char** extractStringArray(JNIEnv* env, jobjectArray array, const char* firstArg, int* outCount) {
    jsize len = (array != nullptr) ? env->GetArrayLength(array) : 0;
    int extra = (firstArg != nullptr) ? 1 : 0;
    int total = len + extra;
    char** result = static_cast<char**>(calloc(static_cast<size_t>(total + 1), sizeof(char*)));
    if (!result) return nullptr;

    int idx = 0;
    if (firstArg != nullptr) {
        result[idx++] = strdup(firstArg);
    }
    for (jsize i = 0; i < len; ++i) {
        auto jStr = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        if (jStr != nullptr) {
            const char* cStr = env->GetStringUTFChars(jStr, nullptr);
            result[idx++] = strdup(cStr ? cStr : "");
            env->ReleaseStringUTFChars(jStr, cStr);
            env->DeleteLocalRef(jStr);
        }
    }
    result[idx] = nullptr;
    if (outCount) *outCount = idx;
    return result;
}

static void freeStringArray(char** arr) {
    if (!arr) return;
    for (int i = 0; arr[i] != nullptr; ++i) {
        free(arr[i]);
    }
    free(arr);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_clouddroidide_android_terminal_NativePty_createSubprocess(
        JNIEnv* env,
        jclass /* clazz */,
        jstring cmd,
        jstring cwd,
        jobjectArray argsArray,
        jobjectArray envArray,
        jint rows,
        jint cols) {

    const char* cmdStr = env->GetStringUTFChars(cmd, nullptr);
    const char* cwdStr = env->GetStringUTFChars(cwd, nullptr);

    char** argPtrs = extractStringArray(env, argsArray, cmdStr, nullptr);
    char** envPtrs = extractStringArray(env, envArray, nullptr, nullptr);

    struct winsize ws {};
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);

    int masterFd = -1;
    int slaveFd = -1;
    if (openpty(&masterFd, &slaveFd, nullptr, nullptr, &ws) != 0) {
        freeStringArray(argPtrs);
        freeStringArray(envPtrs);
        env->ReleaseStringUTFChars(cmd, cmdStr);
        env->ReleaseStringUTFChars(cwd, cwdStr);
        return nullptr;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(masterFd);
        close(slaveFd);
        freeStringArray(argPtrs);
        freeStringArray(envPtrs);
        env->ReleaseStringUTFChars(cmd, cmdStr);
        env->ReleaseStringUTFChars(cwd, cwdStr);
        return nullptr;
    }

    if (pid == 0) {
        close(masterFd);
        setsid();
        ioctl(slaveFd, TIOCSCTTY, 0);

        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > STDERR_FILENO) {
            close(slaveFd);
        }

        if (cwdStr != nullptr && strlen(cwdStr) > 0) {
            chdir(cwdStr);
        }

        execve(cmdStr, argPtrs, envPtrs);
        _exit(127);
    }

    close(slaveFd);
    freeStringArray(argPtrs);
    freeStringArray(envPtrs);
    env->ReleaseStringUTFChars(cmd, cmdStr);
    env->ReleaseStringUTFChars(cwd, cwdStr);

    jintArray result = env->NewIntArray(2);
    jint values[2] = { static_cast<jint>(pid), static_cast<jint>(masterFd) };
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_clouddroidide_android_terminal_NativePty_readFd(
        JNIEnv* env,
        jclass /* clazz */,
        jint fd,
        jbyteArray buffer,
        jint len) {
    if (fd < 0 || len <= 0) return -1;
    jbyte* buf = env->GetByteArrayElements(buffer, nullptr);
    ssize_t n = read(fd, buf, static_cast<size_t>(len));
    env->ReleaseByteArrayElements(buffer, buf, 0);
    return static_cast<jint>(n);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_clouddroidide_android_terminal_NativePty_writeFd(
        JNIEnv* env,
        jclass /* clazz */,
        jint fd,
        jbyteArray buffer,
        jint len) {
    if (fd < 0 || len <= 0) return -1;
    jbyte* buf = env->GetByteArrayElements(buffer, nullptr);
    ssize_t n = write(fd, buf, static_cast<size_t>(len));
    env->ReleaseByteArrayElements(buffer, buf, JNI_ABORT);
    return static_cast<jint>(n);
}

extern "C" JNIEXPORT void JNICALL
Java_com_clouddroidide_android_terminal_NativePty_closeFd(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint fd) {
    if (fd >= 0) {
        close(fd);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_clouddroidide_android_terminal_NativePty_setPtyWindowSize(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint fd,
        jint rows,
        jint cols) {
    if (fd < 0) return;
    struct winsize ws {};
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    ioctl(fd, TIOCSWINSZ, &ws);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_clouddroidide_android_terminal_NativePty_waitFor(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint pid) {
    int status = 0;
    if (waitpid(pid, &status, 0) < 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_clouddroidide_android_terminal_NativePty_killProcess(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint pid) {
    if (pid > 0) {
        kill(-pid, SIGKILL);
        kill(pid, SIGKILL);
    }
}
