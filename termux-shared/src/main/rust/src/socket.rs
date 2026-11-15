//! `AF_UNIX` `SOCK_STREAM` socket operations, independent of JNI so that they can be unit tested
//! on the host. Error messages match the ones of the original C++ implementation.

use alloc::ffi::CString;
use alloc::format;
use alloc::string::String;
use alloc::vec::Vec;
use core::ffi::{CStr, c_int};
use core::mem::size_of;
use core::ptr;

/// A failure of an operation, returned to Java as a `JniResult` with `retval` -1.
#[derive(Debug, PartialEq, Eq)]
pub struct Failure {
    /// The `errno` of the failed call, or 0.
    pub errno: c_int,
    /// The error message, without the log title prefix.
    pub message: String,
}

impl Failure {
    /// A failure not caused by a failed system call.
    pub fn new(message: String) -> Self {
        Self { errno: 0, message }
    }

    /// A failure of a system call: `<prefix>: <strerror(errno)>`.
    pub fn os(errno: c_int, prefix: &str) -> Self {
        Self {
            errno,
            message: format!("{}: {}", prefix, strerror(errno)),
        }
    }
}

pub type Result<T> = core::result::Result<T, Failure>;

/// The peer credentials of a connected socket and the command line of the peer process.
#[derive(Debug)]
pub struct PeerCred {
    pub pid: libc::pid_t,
    pub uid: libc::uid_t,
    pub gid: libc::gid_t,
}

pub fn errno() -> c_int {
    #[cfg(target_os = "android")]
    unsafe {
        *libc::__errno()
    }
    #[cfg(not(target_os = "android"))]
    unsafe {
        *libc::__errno_location()
    }
}

pub fn strerror(errno: c_int) -> String {
    unsafe { CStr::from_ptr(libc::strerror(errno)) }
        .to_string_lossy()
        .into_owned()
}

/// Current `CLOCK_REALTIME` time in milliseconds.
// time_t and long are 32 bit on 32 bit Android, so the conversions are not useless there.
#[allow(clippy::useless_conversion)]
fn current_time_millis() -> Option<i64> {
    let mut time = libc::timespec {
        tv_sec: 0,
        tv_nsec: 0,
    };
    if unsafe { libc::clock_gettime(libc::CLOCK_REALTIME, &mut time) } == -1 {
        return None;
    }
    Some(i64::from(time.tv_sec) * 1000 + i64::from(time.tv_nsec) / 1_000_000)
}

/// Check the deadline in milliseconds since the epoch, if one is set (> 0).
fn check_deadline(function: &str, deadline: i64, warn: &mut dyn FnMut(String)) -> Result<()> {
    if deadline > 0 {
        match current_time_millis() {
            Some(now) if now > deadline => {
                return Err(Failure::new(format!(
                    "{function}(): Deadline \"{deadline}\" timeout"
                )));
            }
            Some(_) => {}
            None => warn(format!(
                "{function}(): Deadline \"{deadline}\" timeout will not work since failed to get current time"
            )),
        }
    }
    Ok(())
}

pub fn check_backlog(backlog: i32) -> Result<()> {
    if !(1..=500).contains(&backlog) {
        return Err(Failure::new(format!(
            "createServerSocketNative(): Backlog \"{backlog}\" is not between 1-500"
        )));
    }
    Ok(())
}

pub fn create_socket() -> Result<c_int> {
    let fd = unsafe { libc::socket(libc::AF_UNIX, libc::SOCK_STREAM, 0) };
    if fd == -1 {
        return Err(Failure::os(
            errno(),
            "createServerSocketNative(): Create local socket failed",
        ));
    }
    Ok(fd)
}

/// Bind the socket `fd` to `path` and start listening on it. The socket is closed on failure.
///
/// A `path` starting with a null byte is in the abstract namespace.
pub fn bind_and_listen(fd: c_int, path: &[u8], backlog: i32) -> Result<()> {
    // On Linux, sun_path is 108 bytes (UNIX_PATH_MAX) in size, and must be null terminated.
    let mut address: libc::sockaddr_un = unsafe { core::mem::zeroed() };
    if path.len() >= address.sun_path.len()
        || path.len() >= size_of::<libc::sockaddr_un>() - size_of::<libc::sa_family_t>()
    {
        close(fd);
        return Err(Failure::new(
            "createServerSocketNative(): Path passed is too long".into(),
        ));
    }
    address.sun_family = libc::AF_UNIX as libc::sa_family_t;
    for (dst, &src) in address.sun_path.iter_mut().zip(path) {
        *dst = src as _;
    }
    // For the error messages, as a C string: up to the first null byte.
    let printable_path = String::from_utf8_lossy(path.split(|&b| b == 0).next().unwrap_or(&[]));

    let ret = unsafe {
        libc::bind(
            fd,
            &address as *const libc::sockaddr_un as *const libc::sockaddr,
            size_of::<libc::sockaddr_un>() as libc::socklen_t,
        )
    };
    if ret == -1 {
        let error = errno();
        close(fd);
        return Err(Failure::os(
            error,
            &format!(
                "createServerSocketNative(): Bind to local socket at path \"{printable_path}\" with fd {fd} failed"
            ),
        ));
    }

    if unsafe { libc::listen(fd, backlog) } == -1 {
        let error = errno();
        close(fd);
        return Err(Failure::os(
            error,
            &format!(
                "createServerSocketNative(): Listen on local socket at path \"{printable_path}\" with fd {fd} failed"
            ),
        ));
    }
    Ok(())
}

fn close(fd: c_int) {
    unsafe { libc::close(fd) };
}

fn invalid_fd(function: &str, fd: c_int) -> Failure {
    Failure::new(format!("{function}(): Invalid fd \"{fd}\" passed"))
}

pub fn close_socket(fd: c_int) -> Result<()> {
    if fd < 0 {
        return Err(invalid_fd("closeSocketNative", fd));
    }
    if unsafe { libc::close(fd) } == -1 {
        return Err(Failure::os(
            errno(),
            &format!("closeSocketNative(): Failed to close socket fd {fd}"),
        ));
    }
    Ok(())
}

pub fn accept(fd: c_int) -> Result<c_int> {
    if fd < 0 {
        return Err(invalid_fd("acceptNative", fd));
    }
    let client_fd = unsafe { libc::accept(fd, ptr::null_mut(), ptr::null_mut()) };
    if client_fd == -1 {
        return Err(Failure::os(
            errno(),
            &format!("acceptNative(): Failed to accept client on fd {fd}"),
        ));
    }
    Ok(client_fd)
}

pub fn check_fd(function: &str, fd: c_int) -> Result<()> {
    if fd < 0 {
        Err(invalid_fd(function, fd))
    } else {
        Ok(())
    }
}

/// Read from `fd` until `data` is full or end of file is reached, and return the number of
/// bytes read.
///
/// Unlike the original C++ code, each `read()` is limited to the space left in `data`: that
/// code always asked for the full array length, which overflowed the buffer after a short read.
pub fn read(
    fd: c_int,
    data: &mut [u8],
    deadline: i64,
    warn: &mut dyn FnMut(String),
) -> Result<usize> {
    let mut bytes_read = 0;
    while bytes_read < data.len() {
        check_deadline("readNative", deadline, warn)?;

        let remaining = &mut data[bytes_read..];
        let ret = unsafe { libc::read(fd, remaining.as_mut_ptr().cast(), remaining.len()) };
        if ret == -1 {
            return Err(Failure::os(
                errno(),
                &format!("readNative(): Failed to read on fd {fd}"),
            ));
        }
        // EOF, peer closed writing end
        if ret == 0 {
            break;
        }
        bytes_read += ret as usize;
    }
    Ok(bytes_read)
}

/// Send all of `data` to `fd`. `SIGPIPE` is not raised if the peer has closed the connection.
pub fn send(fd: c_int, data: &[u8], deadline: i64, warn: &mut dyn FnMut(String)) -> Result<()> {
    let mut bytes_sent = 0;
    while bytes_sent < data.len() {
        check_deadline("sendNative", deadline, warn)?;

        let remaining = &data[bytes_sent..];
        let ret = unsafe {
            libc::send(
                fd,
                remaining.as_ptr().cast(),
                remaining.len(),
                libc::MSG_NOSIGNAL,
            )
        };
        if ret == -1 {
            return Err(Failure::os(
                errno(),
                &format!("sendNative(): Failed to send on fd {fd}"),
            ));
        }
        bytes_sent += ret as usize;
    }
    Ok(())
}

/// The number of unread bytes in the receive buffer of `fd`.
pub fn available(fd: c_int) -> Result<c_int> {
    check_fd("availableNative", fd)?;
    let mut available: c_int = 0;
    // SIOCINQ is FIONREAD.
    if unsafe { libc::ioctl(fd, libc::FIONREAD as _, &mut available) } == -1 {
        return Err(Failure::os(
            errno(),
            &format!(
                "availableNative(): Failed to get number of unread bytes in the receive buffer of fd {fd}"
            ),
        ));
    }
    Ok(available)
}

fn milliseconds_to_timeval(milliseconds: i32) -> libc::timeval {
    libc::timeval {
        tv_sec: (milliseconds / 1000) as _,
        tv_usec: ((milliseconds % 1000) * 1000) as _,
    }
}

fn set_socket_timeout(fd: c_int, option: c_int, timeout: i32) -> c_int {
    let time = milliseconds_to_timeval(timeout);
    unsafe {
        libc::setsockopt(
            fd,
            libc::SOL_SOCKET,
            option,
            &time as *const libc::timeval as *const libc::c_void,
            size_of::<libc::timeval>() as libc::socklen_t,
        )
    }
}

pub fn set_read_timeout(fd: c_int, timeout: i32) -> Result<()> {
    check_fd("setSocketReadTimeoutNative", fd)?;
    if set_socket_timeout(fd, libc::SO_RCVTIMEO, timeout) == -1 {
        return Err(Failure::os(
            errno(),
            &format!(
                "setSocketReadTimeoutNative(): Failed to set socket receiving (SO_RCVTIMEO) timeout for fd {fd}"
            ),
        ));
    }
    Ok(())
}

pub fn set_send_timeout(fd: c_int, timeout: i32) -> Result<()> {
    check_fd("setSocketSendTimeoutNative", fd)?;
    if set_socket_timeout(fd, libc::SO_SNDTIMEO, timeout) == -1 {
        return Err(Failure::os(
            errno(),
            &format!(
                "setSocketSendTimeoutNative(): Failed to set socket sending (SO_SNDTIMEO) timeout for fd {fd}"
            ),
        ));
    }
    Ok(())
}

/// Get the credentials of the peer process of a connected socket with `SO_PEERCRED`.
pub fn peer_cred(fd: c_int) -> Result<PeerCred> {
    #[repr(C)]
    struct Ucred {
        pid: libc::pid_t,
        uid: libc::uid_t,
        gid: libc::gid_t,
    }

    // Initialize to -1 instead of 0 in case a failed getsockopt() call somehow doesn't report
    // failure and returns the uid of root.
    let mut cred = Ucred {
        pid: -1,
        uid: u32::MAX,
        gid: u32::MAX,
    };
    let mut len = size_of::<Ucred>() as libc::socklen_t;
    let ret = unsafe {
        libc::getsockopt(
            fd,
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            &mut cred as *mut Ucred as *mut libc::c_void,
            &mut len,
        )
    };
    if ret == -1 {
        return Err(Failure::os(
            errno(),
            &format!("getPeerCredNative(): Failed to get peer credentials for fd {fd}"),
        ));
    }
    Ok(PeerCred {
        pid: cred.pid,
        uid: cred.uid,
        gid: cred.gid,
    })
}

/// Read `/proc/[pid]/cmdline` of a process. Empty if it can not be read, which is normally the
/// case for processes of other apps/users.
pub fn process_cmdline(pid: libc::pid_t) -> Vec<u8> {
    let mut cmdline = Vec::new();
    let Ok(path) = CString::new(format!("/proc/{pid}/cmdline")) else {
        return cmdline;
    };
    unsafe {
        let fd = libc::open(path.as_ptr(), libc::O_RDONLY | libc::O_CLOEXEC);
        if fd < 0 {
            return cmdline;
        }
        let mut buffer = [0u8; 8192];
        loop {
            let ret = libc::read(fd, buffer.as_mut_ptr().cast(), buffer.len());
            if ret < 0 && errno() == libc::EINTR {
                continue;
            }
            if ret <= 0 {
                break;
            }
            cmdline.extend_from_slice(&buffer[..ret as usize]);
        }
        libc::close(fd);
    }
    cmdline
}

/// The process name from a `/proc/[pid]/cmdline` value: everything before the first `\0`.
pub fn process_name_from_cmdline(cmdline: &[u8]) -> &[u8] {
    cmdline.split(|&b| b == 0).next().unwrap_or(&[])
}

/// A `/proc/[pid]/cmdline` value with the `\0` argument separators replaced with spaces. The
/// terminating `\0` of the last argument is dropped.
pub fn cmdline_spaced(cmdline: &[u8]) -> Vec<u8> {
    let cmdline = cmdline.strip_suffix(&[0]).unwrap_or(cmdline);
    cmdline
        .iter()
        .map(|&b| if b == 0 { b' ' } else { b })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::os::unix::io::{FromRawFd, IntoRawFd};
    use std::os::unix::net::UnixStream;
    use std::time::Duration;

    fn no_warnings() -> impl FnMut(String) {
        |warning| panic!("unexpected warning: {warning}")
    }

    /// A listening server socket in a temporary directory.
    struct Server {
        fd: c_int,
        path: std::path::PathBuf,
    }

    impl Server {
        fn new(name: &str) -> Self {
            let path =
                std::env::temp_dir().join(format!("termux-ls-{}-{name}", std::process::id()));
            let _ = std::fs::remove_file(&path);
            let fd = create_socket().unwrap();
            bind_and_listen(fd, path.to_str().unwrap().as_bytes(), 50).unwrap();
            Server { fd, path }
        }

        /// Connect a client and accept it. Returns (client, accepted server side fd).
        fn connect(&self) -> (UnixStream, c_int) {
            let client = UnixStream::connect(&self.path).unwrap();
            let accepted = accept(self.fd).unwrap();
            (client, accepted)
        }
    }

    impl Drop for Server {
        fn drop(&mut self) {
            close_socket(self.fd).unwrap();
            let _ = std::fs::remove_file(&self.path);
        }
    }

    #[test]
    fn backlog_must_be_between_1_and_500() {
        assert!(check_backlog(1).is_ok() && check_backlog(500).is_ok());
        assert_eq!(
            check_backlog(0),
            Err(Failure::new(
                "createServerSocketNative(): Backlog \"0\" is not between 1-500".into()
            ))
        );
        assert_eq!(
            check_backlog(501).unwrap_err().message,
            "createServerSocketNative(): Backlog \"501\" is not between 1-500"
        );
    }

    #[test]
    fn rejects_too_long_path() {
        let fd = create_socket().unwrap();
        let inode = fd_inode(fd);
        assert!(inode.is_some());
        let path = [b'a'; 108];
        assert_eq!(
            bind_and_listen(fd, &path, 50),
            Err(Failure::new(
                "createServerSocketNative(): Path passed is too long".into()
            ))
        );
        // The socket was closed. Tests run in parallel, so another test may already have reused
        // the fd number, but it can't refer to the same socket anymore.
        assert_ne!(fd_inode(fd), inode);
    }

    /// The inode of the file `fd` refers to, or `None` if `fd` is not open.
    fn fd_inode(fd: c_int) -> Option<u64> {
        let mut stat: libc::stat = unsafe { core::mem::zeroed() };
        if unsafe { libc::fstat(fd, &mut stat) } != 0 {
            return None;
        }
        Some(stat.st_ino as u64)
    }

    #[test]
    fn bind_failure_reports_errno_and_path() {
        let server = Server::new("bind-failure");
        let fd = create_socket().unwrap();
        let path = server.path.to_str().unwrap();
        let failure = bind_and_listen(fd, path.as_bytes(), 50).unwrap_err();
        assert_eq!(failure.errno, libc::EADDRINUSE);
        assert_eq!(
            failure.message,
            format!(
                "createServerSocketNative(): Bind to local socket at path \"{path}\" with fd {fd} failed: {}",
                strerror(libc::EADDRINUSE)
            )
        );
    }

    #[test]
    fn abstract_namespace_socket() {
        let name = format!("\0termux-ls-test-{}", std::process::id());
        let fd = create_socket().unwrap();
        bind_and_listen(fd, name.as_bytes(), 1).unwrap();
        close_socket(fd).unwrap();
    }

    #[test]
    fn invalid_fds_are_rejected() {
        let mut warn = no_warnings();
        assert_eq!(
            close_socket(-1).unwrap_err().message,
            "closeSocketNative(): Invalid fd \"-1\" passed"
        );
        assert_eq!(
            accept(-2).unwrap_err().message,
            "acceptNative(): Invalid fd \"-2\" passed"
        );
        assert_eq!(
            available(-1).unwrap_err().message,
            "availableNative(): Invalid fd \"-1\" passed"
        );
        assert_eq!(
            set_read_timeout(-1, 1).unwrap_err().message,
            "setSocketReadTimeoutNative(): Invalid fd \"-1\" passed"
        );
        assert_eq!(
            set_send_timeout(-1, 1).unwrap_err().message,
            "setSocketSendTimeoutNative(): Invalid fd \"-1\" passed"
        );
        assert_eq!(
            check_fd("readNative", -1).unwrap_err().message,
            "readNative(): Invalid fd \"-1\" passed"
        );
        // Not validated here but by the JNI wrapper, like in the C++ code.
        assert!(read(-1, &mut [0; 1], 0, &mut warn).is_err());
    }

    #[test]
    fn close_failure_reports_errno() {
        let failure = close_socket(999_999).unwrap_err();
        assert_eq!(failure.errno, libc::EBADF);
        assert_eq!(
            failure.message,
            format!(
                "closeSocketNative(): Failed to close socket fd 999999: {}",
                strerror(libc::EBADF)
            )
        );
    }

    #[test]
    fn reads_until_buffer_is_full_across_short_reads() {
        let server = Server::new("short-reads");
        let (mut client, fd) = server.connect();
        let writer = std::thread::spawn(move || {
            client.write_all(b"0123").unwrap();
            std::thread::sleep(Duration::from_millis(100));
            client.write_all(b"456789ABCDEF").unwrap();
            client
        });
        let mut data = [0u8; 10];
        assert_eq!(read(fd, &mut data, 0, &mut no_warnings()), Ok(10));
        assert_eq!(&data, b"0123456789");
        let _client = writer.join().unwrap();
        // The bytes beyond the buffer are still unread.
        assert_eq!(available(fd), Ok(6));
        close_socket(fd).unwrap();
    }

    #[test]
    fn read_stops_at_end_of_file() {
        let server = Server::new("eof");
        let (mut client, fd) = server.connect();
        client.write_all(b"abc").unwrap();
        drop(client);
        let mut data = [b'x'; 8];
        assert_eq!(read(fd, &mut data, 0, &mut no_warnings()), Ok(3));
        assert_eq!(&data, b"abcxxxxx");
        close_socket(fd).unwrap();
    }

    #[test]
    fn read_and_send_deadline() {
        let server = Server::new("deadline");
        let (_client, fd) = server.connect();
        let past = current_time_millis().unwrap() - 1;
        assert_eq!(
            read(fd, &mut [0; 4], past, &mut no_warnings()),
            Err(Failure::new(format!(
                "readNative(): Deadline \"{past}\" timeout"
            )))
        );
        assert_eq!(
            send(fd, b"data", past, &mut no_warnings()),
            Err(Failure::new(format!(
                "sendNative(): Deadline \"{past}\" timeout"
            )))
        );
        // Empty buffers do not check the deadline, like the C++ code.
        assert_eq!(read(fd, &mut [], past, &mut no_warnings()), Ok(0));
        assert_eq!(send(fd, b"", past, &mut no_warnings()), Ok(()));
        close_socket(fd).unwrap();
    }

    #[test]
    fn read_timeout() {
        let server = Server::new("read-timeout");
        let (_client, fd) = server.connect();
        set_read_timeout(fd, 100).unwrap();
        let start = std::time::Instant::now();
        let failure = read(fd, &mut [0; 4], 0, &mut no_warnings()).unwrap_err();
        assert!(start.elapsed() >= Duration::from_millis(90));
        assert_eq!(failure.errno, libc::EAGAIN);
        assert_eq!(
            failure.message,
            format!(
                "readNative(): Failed to read on fd {fd}: {}",
                strerror(libc::EAGAIN)
            )
        );
        set_send_timeout(fd, 1500).unwrap();
        close_socket(fd).unwrap();
    }

    #[test]
    fn sends_data_and_reports_closed_peer_without_sigpipe() {
        let server = Server::new("send");
        let (mut client, fd) = server.connect();
        send(fd, b"hello", 0, &mut no_warnings()).unwrap();
        let mut received = [0u8; 5];
        client.read_exact(&mut received).unwrap();
        assert_eq!(&received, b"hello");

        drop(client);
        // The first send after the peer closed may still succeed, the next fails with EPIPE.
        let failure = (0..10)
            .find_map(|_| send(fd, &[0; 1024], 0, &mut no_warnings()).err())
            .unwrap();
        assert_eq!(failure.errno, libc::EPIPE);
        assert_eq!(
            failure.message,
            format!(
                "sendNative(): Failed to send on fd {fd}: {}",
                strerror(libc::EPIPE)
            )
        );
        close_socket(fd).unwrap();
    }

    #[test]
    fn peer_credentials_of_own_process() {
        let server = Server::new("peer-cred");
        let (_client, fd) = server.connect();
        let cred = peer_cred(fd).unwrap();
        assert_eq!(cred.pid, std::process::id() as libc::pid_t);
        assert_eq!(cred.uid, unsafe { libc::getuid() });
        assert_eq!(cred.gid, unsafe { libc::getgid() });

        let cmdline = process_cmdline(cred.pid);
        let args: Vec<String> = std::env::args().collect();
        assert_eq!(process_name_from_cmdline(&cmdline), args[0].as_bytes());
        assert_eq!(cmdline_spaced(&cmdline), args.join(" ").into_bytes());
        close_socket(fd).unwrap();

        let failure = peer_cred(999_999).unwrap_err();
        assert_eq!(failure.errno, libc::EBADF);
        assert_eq!(
            failure.message,
            format!(
                "getPeerCredNative(): Failed to get peer credentials for fd 999999: {}",
                strerror(libc::EBADF)
            )
        );
    }

    #[test]
    fn cmdline_parsing_matches_cpp_getline_semantics() {
        assert_eq!(
            process_name_from_cmdline(b"/system/bin/app_process\0-Xzygote\0"),
            b"/system/bin/app_process"
        );
        assert_eq!(process_name_from_cmdline(b"name"), b"name");
        assert_eq!(process_name_from_cmdline(b"\0arg"), b"");
        assert_eq!(cmdline_spaced(b"a\0b\0"), b"a b");
        assert_eq!(cmdline_spaced(b"a\0\0b"), b"a  b");
        assert_eq!(cmdline_spaced(b"a\0\0"), b"a ");
        assert_eq!(cmdline_spaced(b"\0a"), b" a");
        assert_eq!(cmdline_spaced(b"\0"), b"");
        assert_eq!(cmdline_spaced(b"single"), b"single");
        // Processes of other users can not be read.
        assert!(process_cmdline(-1).is_empty());
    }

    #[test]
    fn timeval_conversion() {
        let time = milliseconds_to_timeval(2500);
        assert_eq!((time.tv_sec, time.tv_usec), (2, 500_000));
        let time = milliseconds_to_timeval(999);
        assert_eq!((time.tv_sec, time.tv_usec), (0, 999_000));
    }

    #[test]
    fn accepted_fd_works_with_std() {
        let server = Server::new("std");
        let (mut client, fd) = server.connect();
        let mut accepted = unsafe { UnixStream::from_raw_fd(fd) };
        accepted.write_all(b"x").unwrap();
        let mut byte = [0u8];
        client.read_exact(&mut byte).unwrap();
        assert_eq!(&byte, b"x");
        let _ = accepted.into_raw_fd();
        close_socket(fd).unwrap();
    }
}
