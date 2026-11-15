//! JNI library for `com.termux.terminal.JNI`: creating pseudoterminals, starting subprocesses in
//! them and waiting for those processes. Loaded with `System.loadLibrary("termux")`.
//!
//! The library is `no_std` (with `alloc`) to stay small, unit tests are built with `std`.

#![cfg_attr(not(test), no_std)]
#![allow(non_snake_case)]
// The exported functions are only called by the JVM, with valid JNI arguments.
#![allow(clippy::missing_safety_doc)]

extern crate alloc;

mod pty;
#[cfg(not(test))]
mod rt;

use alloc::borrow::ToOwned;
use alloc::ffi::CString;
use alloc::vec::Vec;
use core::ffi::CStr;
use core::ptr;

use jni_sys::{JNIEnv, jclass, jint, jintArray, jobjectArray, jstring};

/// Call a function of the JNI function table: `jni!(env, FindClass, name)`.
macro_rules! jni {
    ($env:expr, $function:ident $(, $arg:expr)* $(,)?) => {
        ((**$env).v1_6.$function)($env $(, $arg)*)
    };
}

/// Throw a `java.lang.RuntimeException` with `message` unless an exception is already pending
/// (e.g. an `OutOfMemoryError` from a failed JNI call). Returns -1 for convenience.
unsafe fn throw_runtime_exception(env: *mut JNIEnv, message: &CStr) -> jint {
    unsafe {
        if !jni!(env, ExceptionCheck) {
            let class = jni!(env, FindClass, c"java/lang/RuntimeException".as_ptr());
            if !class.is_null() {
                jni!(env, ThrowNew, class, message.as_ptr());
                jni!(env, DeleteLocalRef, class);
            }
        }
    }
    -1
}

/// Copy a Java string with `GetStringUTFChars()`, i.e. as modified UTF-8, as the original C
/// implementation did.
unsafe fn get_string(env: *mut JNIEnv, string: jstring) -> Option<CString> {
    if string.is_null() {
        return None;
    }
    unsafe {
        let chars = jni!(env, GetStringUTFChars, string, ptr::null_mut());
        if chars.is_null() {
            return None;
        }
        let copy = CStr::from_ptr(chars).to_owned();
        jni!(env, ReleaseStringUTFChars, string, chars);
        Some(copy)
    }
}

/// Copy a Java `String[]`, a null array is treated as an empty one.
unsafe fn get_string_array(env: *mut JNIEnv, array: jobjectArray) -> Option<Vec<CString>> {
    unsafe {
        let size = if array.is_null() {
            0
        } else {
            jni!(env, GetArrayLength, array)
        };
        let mut strings = Vec::with_capacity(size.max(0) as usize);
        for i in 0..size {
            let element = jni!(env, GetObjectArrayElement, array, i);
            let string = get_string(env, element);
            if !element.is_null() {
                jni!(env, DeleteLocalRef, element);
            }
            strings.push(string?);
        }
        Some(strings)
    }
}

/// Starts `cmd` in a new pseudoterminal and returns the file descriptor of the pty master. The
/// process id is written to `processId[0]`. Throws a `RuntimeException` on failure.
///
/// Unlike the original C version (termux/termux-app#5254), every allocation is freed and every
/// JNI reference released on all paths (owned values are dropped automatically), null strings
/// and failing `GetStringUTFChars()` calls are checked, and `processId` is validated before
/// starting the process, so no pty or process is leaked if it can not be returned.
#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_terminal_JNI_createSubprocess(
    env: *mut JNIEnv,
    _clazz: jclass,
    cmd: jstring,
    cwd: jstring,
    args: jobjectArray,
    env_vars: jobjectArray,
    process_id_array: jintArray,
    rows: jint,
    columns: jint,
    cell_width: jint,
    cell_height: jint,
) -> jint {
    unsafe {
        let Some(argv) = get_string_array(env, args) else {
            return throw_runtime_exception(env, c"GetStringUTFChars() failed for argv");
        };
        let Some(env_vars) = get_string_array(env, env_vars) else {
            return throw_runtime_exception(env, c"GetStringUTFChars() failed for env");
        };
        let Some(cwd) = get_string(env, cwd) else {
            return throw_runtime_exception(env, c"GetStringUTFChars() failed for cwd");
        };
        let Some(cmd) = get_string(env, cmd) else {
            return throw_runtime_exception(env, c"GetStringUTFChars() failed for cmd");
        };
        if process_id_array.is_null() || jni!(env, GetArrayLength, process_id_array) < 1 {
            return throw_runtime_exception(
                env,
                c"JNI call GetPrimitiveArrayCritical(processIdArray, &isCopy) failed",
            );
        }
        let environment = pty::build_environment(env_vars);

        let process = match pty::create_subprocess(
            &cmd,
            &cwd,
            &argv,
            &environment,
            rows,
            columns,
            cell_width,
            cell_height,
        ) {
            Ok(process) => process,
            Err(message) => return throw_runtime_exception(env, message),
        };

        jni!(env, SetIntArrayRegion, process_id_array, 0, 1, &process.pid);
        if jni!(env, ExceptionCheck) {
            // Can not happen as the array was checked above, but never leak the process.
            pty::kill_and_close(process);
            return -1;
        }

        process.ptm
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_termux_terminal_JNI_setPtyWindowSize(
    _env: *mut JNIEnv,
    _clazz: jclass,
    fd: jint,
    rows: jint,
    cols: jint,
    cell_width: jint,
    cell_height: jint,
) {
    pty::set_window_size(fd, rows, cols, cell_width, cell_height);
}

/// Not declared in `JNI.java` currently, kept since the C library exported it.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_termux_terminal_JNI_setPtyUTF8Mode(
    _env: *mut JNIEnv,
    _clazz: jclass,
    fd: jint,
) {
    pty::set_utf8_mode(fd);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_termux_terminal_JNI_waitFor(
    _env: *mut JNIEnv,
    _clazz: jclass,
    pid: jint,
) -> jint {
    pty::wait_for(pid)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_termux_terminal_JNI_close(
    _env: *mut JNIEnv,
    _clazz: jclass,
    file_descriptor: jint,
) {
    pty::close(file_descriptor);
}
