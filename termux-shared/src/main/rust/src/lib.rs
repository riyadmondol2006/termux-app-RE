//! JNI library for `com.termux.shared.net.socket.local.LocalSocketManager`: `AF_UNIX` local
//! server sockets used by the termux-am socket server and others. Loaded with
//! `System.loadLibrary("local-socket")`.
//!
//! Every native method returns a `com.termux.shared.jni.models.JniResult`. On success, `retval`
//! and `errno` are 0, `errmsg` is empty and `intData` holds the result, if any. On failure,
//! `retval` is -1, `errno` is the errno of the failed call or 0, and `errmsg` is the error
//! message prefixed with `<logTitle>: `. If a JNI call throws, null is returned and the exception
//! is left pending for the Java caller.
//!
//! The library is `no_std` (with `alloc`) to stay small, unit tests are built with `std`.

#![cfg_attr(not(test), no_std)]
#![allow(non_snake_case)]
// The exported functions are only called by the JVM, with valid JNI arguments.
#![allow(clippy::missing_safety_doc)]

extern crate alloc;

mod log;
#[cfg(not(test))]
mod rt;
mod socket;

use alloc::format;
use alloc::string::String;
use alloc::vec::Vec;
use core::ffi::{CStr, c_int};
use core::ptr;

use jni_sys::{
    JNI_ABORT, JNIEnv, jbyte, jbyteArray, jclass, jint, jlong, jobject, jsize, jstring, jvalue,
};

use socket::Failure;

/// Call a function of the JNI function table: `jni!(env, FindClass, name)`.
macro_rules! jni {
    ($env:expr, $function:ident $(, $arg:expr)* $(,)?) => {
        ((**$env).v1_6.$function)($env $(, $arg)*)
    };
}

unsafe fn exception_check(env: *mut JNIEnv) -> bool {
    unsafe { jni!(env, ExceptionCheck) }
}

unsafe fn delete_local_ref(env: *mut JNIEnv, object: jobject) {
    if !object.is_null() {
        unsafe { jni!(env, DeleteLocalRef, object) };
    }
}

/// Convert a Java string to a Rust string like `String.getBytes(UTF_8)` would, which the original
/// C++ code used: unpaired surrogates are replaced with `?`.
unsafe fn get_string(env: *mut JNIEnv, string: jstring) -> String {
    unsafe {
        let length = jni!(env, GetStringLength, string);
        let mut chars: Vec<u16> = Vec::with_capacity(length.max(0) as usize);
        jni!(env, GetStringRegion, string, 0, length, chars.as_mut_ptr());
        if exception_check(env) {
            return String::new();
        }
        chars.set_len(length.max(0) as usize);
        char::decode_utf16(chars)
            .map(|c| c.unwrap_or('?'))
            .collect()
    }
}

/// Create a Java string. Like `NewStringUTF(string.c_str())` it ends at the first null
/// character, but it is created from UTF-16, so invalid modified UTF-8 can not abort the VM.
unsafe fn new_string(env: *mut JNIEnv, string: &str) -> jstring {
    let string = string.split('\0').next().unwrap_or("");
    let chars: Vec<u16> = string.encode_utf16().collect();
    unsafe { jni!(env, NewString, chars.as_ptr(), chars.len() as jsize) }
}

/// `"<title>: <message>"`, or just the message if `title` is null.
unsafe fn title_and_message(env: *mut JNIEnv, title: jstring, message: &str) -> String {
    if title.is_null() {
        return message.into();
    }
    let mut titled = unsafe { get_string(env, title) };
    titled.push_str(": ");
    titled.push_str(message);
    titled
}

fn jni_result_string(retval: c_int, errno: c_int, errmsg: &str, int_data: c_int) -> String {
    format!("retval={retval}, errno={errno}, errmsg=\"{errmsg}\", intData={int_data}")
}

/// Create a `com.termux.shared.jni.models.JniResult`. Returns null if creating it failed, with
/// an exception pending if a JNI call threw.
unsafe fn jni_result(
    env: *mut JNIEnv,
    title: jstring,
    retval: c_int,
    errno: c_int,
    errmsg: &str,
    int_data: c_int,
) -> jobject {
    unsafe {
        let class = jni!(
            env,
            FindClass,
            c"com/termux/shared/jni/models/JniResult".as_ptr()
        );
        if exception_check(env) {
            return ptr::null_mut();
        }
        if class.is_null() {
            log::error(&title_and_message(
                env,
                title,
                &format!(
                    "Failed to find JniResult class to create object for {}",
                    jni_result_string(retval, errno, errmsg, int_data)
                ),
            ));
            return ptr::null_mut();
        }

        let constructor = jni!(
            env,
            GetMethodID,
            class,
            c"<init>".as_ptr(),
            c"(IILjava/lang/String;I)V".as_ptr()
        );
        if exception_check(env) {
            delete_local_ref(env, class);
            return ptr::null_mut();
        }
        if constructor.is_null() {
            delete_local_ref(env, class);
            log::error(&title_and_message(
                env,
                title,
                &format!(
                    "Failed to get constructor for JniResult class to create object for {}",
                    jni_result_string(retval, errno, errmsg, int_data)
                ),
            ));
            return ptr::null_mut();
        }

        let errmsg = if errmsg.is_empty() {
            String::new()
        } else {
            title_and_message(env, title, errmsg)
        };
        let jerrmsg = new_string(env, &errmsg);
        if exception_check(env) {
            delete_local_ref(env, class);
            return ptr::null_mut();
        }

        let args = [
            jvalue { i: retval },
            jvalue { i: errno },
            jvalue { l: jerrmsg },
            jvalue { i: int_data },
        ];
        let object = jni!(env, NewObjectA, class, constructor, args.as_ptr());
        delete_local_ref(env, jerrmsg);
        delete_local_ref(env, class);
        if exception_check(env) {
            return ptr::null_mut();
        }
        if object.is_null() {
            log::error(&title_and_message(
                env,
                title,
                &format!(
                    "Failed to get JniResult object for {}",
                    jni_result_string(retval, errno, &errmsg, int_data)
                ),
            ));
            return ptr::null_mut();
        }
        object
    }
}

/// A successful `JniResult` with `intData` set to `int_data`.
unsafe fn success(env: *mut JNIEnv, title: jstring, int_data: c_int) -> jobject {
    unsafe { jni_result(env, title, 0, 0, "", int_data) }
}

/// A failed `JniResult` with `retval` -1.
unsafe fn failure(env: *mut JNIEnv, title: jstring, failure: Failure) -> jobject {
    unsafe { jni_result(env, title, -1, failure.errno, &failure.message, 0) }
}

unsafe fn to_jni_result(
    env: *mut JNIEnv,
    title: jstring,
    result: socket::Result<c_int>,
) -> jobject {
    unsafe {
        match result {
            Ok(int_data) => success(env, title, int_data),
            Err(error) => failure(env, title, error),
        }
    }
}

/// The elements of a Java `byte[]` from `GetByteArrayElements()`, released when dropped with
/// `mode` (0 to copy back changes, `JNI_ABORT` to discard them).
struct ByteArrayElements {
    env: *mut JNIEnv,
    array: jbyteArray,
    elements: *mut jbyte,
    length: usize,
    mode: jint,
}

/// Why getting the elements of an array failed.
enum ByteArrayError {
    /// A JNI exception is pending.
    Exception,
    /// The array or the elements are null.
    Null,
}

impl ByteArrayElements {
    unsafe fn get(env: *mut JNIEnv, array: jbyteArray, mode: jint) -> Result<Self, ByteArrayError> {
        if array.is_null() {
            return Err(ByteArrayError::Null);
        }
        unsafe {
            let elements = jni!(env, GetByteArrayElements, array, ptr::null_mut());
            if exception_check(env) {
                return Err(ByteArrayError::Exception);
            }
            if elements.is_null() {
                return Err(ByteArrayError::Null);
            }
            let length = jni!(env, GetArrayLength, array).max(0) as usize;
            Ok(Self {
                env,
                array,
                elements,
                length,
                mode,
            })
        }
    }

    fn as_slice(&self) -> &[u8] {
        unsafe { core::slice::from_raw_parts(self.elements as *const u8, self.length) }
    }

    fn as_mut_slice(&mut self) -> &mut [u8] {
        unsafe { core::slice::from_raw_parts_mut(self.elements as *mut u8, self.length) }
    }
}

impl Drop for ByteArrayElements {
    fn drop(&mut self) {
        unsafe {
            jni!(
                self.env,
                ReleaseByteArrayElements,
                self.array,
                self.elements,
                self.mode
            )
        };
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_createServerSocketNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    path_array: jbyteArray,
    backlog: jint,
) -> jobject {
    unsafe {
        if let Err(error) = socket::check_backlog(backlog) {
            return failure(env, log_title, error);
        }

        let fd = match socket::create_socket() {
            Ok(fd) => fd,
            Err(error) => return failure(env, log_title, error),
        };

        let result = match ByteArrayElements::get(env, path_array, JNI_ABORT) {
            Ok(path) => socket::bind_and_listen(fd, path.as_slice(), backlog),
            Err(ByteArrayError::Exception) => {
                libc::close(fd);
                return ptr::null_mut();
            }
            Err(ByteArrayError::Null) => {
                libc::close(fd);
                Err(Failure::new(
                    "createServerSocketNative(): Path passed is null".into(),
                ))
            }
        };

        // Return success and server socket fd in JniResult.intData field
        to_jni_result(env, log_title, result.map(|()| fd))
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_closeSocketNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
) -> jobject {
    unsafe { to_jni_result(env, log_title, socket::close_socket(fd).map(|()| 0)) }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_acceptNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
) -> jobject {
    // Return success and client socket fd in JniResult.intData field
    unsafe { to_jni_result(env, log_title, socket::accept(fd)) }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_readNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
    data_array: jbyteArray,
    deadline: jlong,
) -> jobject {
    unsafe {
        if let Err(error) = socket::check_fd("readNative", fd) {
            return failure(env, log_title, error);
        }

        // Released with mode 0 to copy back the data read, also on failure.
        let result = match ByteArrayElements::get(env, data_array, 0) {
            Ok(mut data) => socket::read(fd, data.as_mut_slice(), deadline, &mut |warning| {
                log::warn(&title_and_message(env, log_title, &warning))
            }),
            Err(ByteArrayError::Exception) => return ptr::null_mut(),
            Err(ByteArrayError::Null) => {
                Err(Failure::new("readNative(): data passed is null".into()))
            }
        };

        // Return success and bytes read in JniResult.intData field
        to_jni_result(env, log_title, result.map(|bytes_read| bytes_read as c_int))
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_sendNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
    data_array: jbyteArray,
    deadline: jlong,
) -> jobject {
    unsafe {
        if let Err(error) = socket::check_fd("sendNative", fd) {
            return failure(env, log_title, error);
        }

        let result = match ByteArrayElements::get(env, data_array, JNI_ABORT) {
            Ok(data) => socket::send(fd, data.as_slice(), deadline, &mut |warning| {
                log::warn(&title_and_message(env, log_title, &warning))
            }),
            Err(ByteArrayError::Exception) => return ptr::null_mut(),
            Err(ByteArrayError::Null) => {
                Err(Failure::new("sendNative(): data passed is null".into()))
            }
        };

        to_jni_result(env, log_title, result.map(|()| 0))
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_availableNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
) -> jobject {
    // Return success and bytes available in JniResult.intData field
    unsafe { to_jni_result(env, log_title, socket::available(fd)) }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_setSocketReadTimeoutNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
    timeout: jint,
) -> jobject {
    unsafe {
        to_jni_result(
            env,
            log_title,
            socket::set_read_timeout(fd, timeout).map(|()| 0),
        )
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_setSocketSendTimeoutNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
    timeout: jint,
) -> jobject {
    unsafe {
        to_jni_result(
            env,
            log_title,
            socket::set_send_timeout(fd, timeout).map(|()| 0),
        )
    }
}

/// Why setting a field of the `PeerCred` object failed.
enum FieldError {
    /// A JNI exception is pending.
    Exception,
    Message(String),
}

/// `Class.getName()` of `class`.
unsafe fn class_name(env: *mut JNIEnv, class: jclass) -> Result<String, FieldError> {
    unsafe {
        let class_class = jni!(env, FindClass, c"java/lang/Class".as_ptr());
        if exception_check(env) || class_class.is_null() {
            return Err(FieldError::Exception);
        }
        let get_name = jni!(
            env,
            GetMethodID,
            class_class,
            c"getName".as_ptr(),
            c"()Ljava/lang/String;".as_ptr()
        );
        delete_local_ref(env, class_class);
        if exception_check(env) || get_name.is_null() {
            return Err(FieldError::Exception);
        }
        let name = jni!(env, CallObjectMethodA, class, get_name, ptr::null());
        if exception_check(env) || name.is_null() {
            return Err(FieldError::Exception);
        }
        let name_string = get_string(env, name);
        delete_local_ref(env, name);
        Ok(name_string)
    }
}

unsafe fn set_int_field(
    env: *mut JNIEnv,
    object: jobject,
    class: jclass,
    name: &CStr,
    value: c_int,
) -> Result<(), FieldError> {
    unsafe {
        let field = jni!(env, GetFieldID, class, name.as_ptr(), c"I".as_ptr());
        if exception_check(env) {
            return Err(FieldError::Exception);
        }
        if field.is_null() {
            return Err(FieldError::Message(format!(
                "Failed to get int \"{}\" field of \"{}\" class to set value \"{value}\"",
                name.to_string_lossy(),
                class_name(env, class)?
            )));
        }
        jni!(env, SetIntField, object, field, value);
        if exception_check(env) {
            return Err(FieldError::Exception);
        }
        Ok(())
    }
}

unsafe fn set_string_field(
    env: *mut JNIEnv,
    object: jobject,
    class: jclass,
    name: &CStr,
    value: &str,
) -> Result<(), FieldError> {
    unsafe {
        let field = jni!(
            env,
            GetFieldID,
            class,
            name.as_ptr(),
            c"Ljava/lang/String;".as_ptr()
        );
        if exception_check(env) {
            return Err(FieldError::Exception);
        }
        if field.is_null() {
            return Err(FieldError::Message(format!(
                "Failed to get String \"{}\" field of \"{}\" class to set value \"{value}\"",
                name.to_string_lossy(),
                class_name(env, class)?
            )));
        }
        let string = new_string(env, value);
        if exception_check(env) {
            return Err(FieldError::Exception);
        }
        jni!(env, SetObjectField, object, field, string);
        delete_local_ref(env, string);
        if exception_check(env) {
            return Err(FieldError::Exception);
        }
        Ok(())
    }
}

/// Fill the `pid`, `uid`, `gid` fields and, if `/proc/[pid]/cmdline` of the peer is readable, the
/// `pname` and `cmdline` fields of a `com.termux.shared.net.socket.local.PeerCred`.
unsafe fn fill_peer_cred(
    env: *mut JNIEnv,
    peer_cred: jobject,
    class: jclass,
    cred: &socket::PeerCred,
) -> Result<(), FieldError> {
    unsafe {
        set_int_field(env, peer_cred, class, c"pid", cred.pid)?;
        set_int_field(env, peer_cred, class, c"uid", cred.uid as c_int)?;
        set_int_field(env, peer_cred, class, c"gid", cred.gid as c_int)?;

        // Processes of other users/apps are not normally accessible.
        let cmdline = socket::process_cmdline(cred.pid);
        if !cmdline.is_empty() {
            let name = String::from_utf8_lossy(socket::process_name_from_cmdline(&cmdline));
            set_string_field(env, peer_cred, class, c"pname", &name)?;
            let spaced = socket::cmdline_spaced(&cmdline);
            set_string_field(
                env,
                peer_cred,
                class,
                c"cmdline",
                &String::from_utf8_lossy(&spaced),
            )?;
        }
        Ok(())
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "system" fn Java_com_termux_shared_net_socket_local_LocalSocketManager_getPeerCredNative(
    env: *mut JNIEnv,
    _clazz: jclass,
    log_title: jstring,
    fd: jint,
    peer_cred: jobject,
) -> jobject {
    unsafe {
        if let Err(error) = socket::check_fd("getPeerCredNative", fd) {
            return failure(env, log_title, error);
        }
        if peer_cred.is_null() {
            return failure(
                env,
                log_title,
                Failure::new("getPeerCredNative(): peerCred passed is null".into()),
            );
        }

        let cred = match socket::peer_cred(fd) {
            Ok(cred) => cred,
            Err(error) => return failure(env, log_title, error),
        };

        let class = jni!(env, GetObjectClass, peer_cred);
        if exception_check(env) {
            return ptr::null_mut();
        }
        if class.is_null() {
            return failure(
                env,
                log_title,
                Failure::os(
                    socket::errno(),
                    "getPeerCredNative(): Failed to get PeerCred class",
                ),
            );
        }

        let result = fill_peer_cred(env, peer_cred, class, &cred);
        delete_local_ref(env, class);
        match result {
            // Return success since PeerCred was filled successfully
            Ok(()) => success(env, log_title, 0),
            Err(FieldError::Exception) => ptr::null_mut(),
            Err(FieldError::Message(message)) => failure(
                env,
                log_title,
                Failure::new(format!("getPeerCredNative(): {message}")),
            ),
        }
    }
}
