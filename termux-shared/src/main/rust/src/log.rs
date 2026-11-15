//! Logging to logcat with the `local-socket` tag, or to stderr in host unit tests.

#[cfg(target_os = "android")]
mod platform {
    use alloc::ffi::CString;
    use core::ffi::{c_char, c_int};

    const ANDROID_LOG_WARN: c_int = 5;
    const ANDROID_LOG_ERROR: c_int = 6;

    #[link(name = "log")]
    unsafe extern "C" {
        fn __android_log_write(priority: c_int, tag: *const c_char, text: *const c_char) -> c_int;
    }

    fn write(priority: c_int, message: &str) {
        // Like a C string, the message ends at the first null character.
        let message = message.split('\0').next().unwrap_or("");
        if let Ok(message) = CString::new(message) {
            unsafe { __android_log_write(priority, c"local-socket".as_ptr(), message.as_ptr()) };
        }
    }

    pub fn error(message: &str) {
        write(ANDROID_LOG_ERROR, message);
    }

    pub fn warn(message: &str) {
        write(ANDROID_LOG_WARN, message);
    }
}

#[cfg(not(target_os = "android"))]
mod platform {
    pub fn error(message: &str) {
        #[cfg(test)]
        std::eprintln!("E local-socket: {message}");
        #[cfg(not(test))]
        let _ = message;
    }

    pub fn warn(message: &str) {
        #[cfg(test)]
        std::eprintln!("W local-socket: {message}");
        #[cfg(not(test))]
        let _ = message;
    }
}

pub use platform::{error, warn};
