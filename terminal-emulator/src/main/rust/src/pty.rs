//! Pseudoterminal creation and subprocess management, independent of JNI so that it can be
//! unit tested on the host.

use alloc::ffi::CString;
use alloc::vec::Vec;
use core::ffi::{CStr, c_char, c_int};
use core::ptr;

unsafe extern "C" {
    static mut environ: *const *const c_char;
}

/// A subprocess running with a pseudoterminal slave as its stdin, stdout and stderr.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Subprocess {
    /// The file descriptor of the opened `/dev/ptmx` master device.
    pub ptm: c_int,
    /// The process id of the subprocess.
    pub pid: libc::pid_t,
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

fn winsize(rows: i32, columns: i32, cell_width: i32, cell_height: i32) -> libc::winsize {
    // Same truncating conversions as the `(unsigned short)` casts of the original C code.
    libc::winsize {
        ws_row: rows as u16,
        ws_col: columns as u16,
        ws_xpixel: columns.wrapping_mul(cell_width) as u16,
        ws_ypixel: rows.wrapping_mul(cell_height) as u16,
    }
}

/// Build the environment of the subprocess from `NAME=value` entries the same way as calling
/// `clearenv()` and then `putenv()` for each entry would: entries without a `=` or with an empty
/// name are ignored, and for duplicate names the last value wins at the position of the first.
///
/// This is done before `fork()`, as `putenv()` allocates memory, which is not allowed in the
/// child of a multithreaded process.
pub fn build_environment(vars: Vec<CString>) -> Vec<CString> {
    let mut environment: Vec<CString> = Vec::with_capacity(vars.len());
    for var in vars {
        let bytes = var.as_bytes();
        let name_len = match bytes.iter().position(|&b| b == b'=') {
            Some(0) | None => continue,
            Some(i) => i + 1, // Including the `=`.
        };
        let name = &bytes[..name_len];
        match environment
            .iter_mut()
            .find(|e| e.as_bytes().starts_with(name))
        {
            Some(existing) => *existing = var,
            None => environment.push(var),
        }
    }
    environment
}

/// Build the `perror()` prefix `<function>("<arg>")` used when a call fails in the child.
fn error_prefix(function: &[u8], arg: &CStr) -> Vec<u8> {
    let mut prefix = Vec::with_capacity(function.len() + arg.to_bytes().len() + 4);
    prefix.extend_from_slice(function);
    prefix.extend_from_slice(b"(\"");
    prefix.extend_from_slice(arg.to_bytes());
    prefix.extend_from_slice(b"\")");
    prefix
}

/// Create a pseudoterminal and start `cmd` in a new session with the pseudoterminal slave as its
/// controlling terminal, stdin, stdout and stderr.
///
/// `argv` is passed as-is to `execvp()` (an empty `argv` is passed as a null pointer, like the
/// original C implementation did) and `environment` is the complete environment, see
/// [`build_environment()`].
///
/// On failure, returns the message for the `RuntimeException` to throw.
#[allow(clippy::too_many_arguments)]
pub fn create_subprocess(
    cmd: &CStr,
    cwd: &CStr,
    argv: &[CString],
    environment: &[CString],
    rows: i32,
    columns: i32,
    cell_width: i32,
    cell_height: i32,
) -> Result<Subprocess, &'static CStr> {
    // Everything the child needs is allocated here, before fork(). After fork() in a
    // multithreaded process like the JVM, the child may only call async-signal-safe functions
    // until it calls execve(), since other threads may have held locks, e.g. of malloc().
    let argv_ptrs: Vec<*const c_char> = argv
        .iter()
        .map(|arg| arg.as_ptr())
        .chain(core::iter::once(ptr::null()))
        .collect();
    let argv_ptr = if argv.is_empty() {
        ptr::null()
    } else {
        argv_ptrs.as_ptr()
    };
    let envp: Vec<*const c_char> = environment
        .iter()
        .map(|var| var.as_ptr())
        .chain(core::iter::once(ptr::null()))
        .collect();
    let chdir_error_prefix = error_prefix(b"chdir", cwd);
    let exec_error_prefix = error_prefix(b"exec", cmd);

    unsafe {
        let ptm = libc::open(c"/dev/ptmx".as_ptr(), libc::O_RDWR | libc::O_CLOEXEC);
        if ptm < 0 {
            return Err(c"Cannot open /dev/ptmx");
        }

        let mut devname = [0 as c_char; 64];
        if libc::grantpt(ptm) != 0
            || libc::unlockpt(ptm) != 0
            || libc::ptsname_r(ptm, devname.as_mut_ptr(), devname.len()) != 0
        {
            libc::close(ptm);
            return Err(c"Cannot grantpt()/unlockpt()/ptsname_r() on /dev/ptmx");
        }

        // Enable UTF-8 mode and disable flow control to prevent Ctrl+S from locking up the display.
        let mut tios: libc::termios = core::mem::zeroed();
        libc::tcgetattr(ptm, &mut tios);
        tios.c_iflag |= libc::IUTF8;
        tios.c_iflag &= !(libc::IXON | libc::IXOFF);
        libc::tcsetattr(ptm, libc::TCSANOW, &tios);

        // Set initial winsize.
        let size = winsize(rows, columns, cell_width, cell_height);
        libc::ioctl(ptm, libc::TIOCSWINSZ as _, &size);

        let pid = libc::fork();
        if pid < 0 {
            libc::close(ptm);
            return Err(c"Fork failed");
        } else if pid == 0 {
            exec_in_child(
                ptm,
                devname.as_ptr(),
                cmd.as_ptr(),
                cwd.as_ptr(),
                argv_ptr,
                envp.as_ptr(),
                &chdir_error_prefix,
                &exec_error_prefix,
            );
        }

        Ok(Subprocess { ptm, pid })
    }
}

/// Runs in the child after `fork()`. Only async-signal-safe libc functions may be used here: no
/// memory allocation, no locks, no stdio, and nothing that can panic.
#[allow(clippy::too_many_arguments)]
unsafe fn exec_in_child(
    ptm: c_int,
    devname: *const c_char,
    cmd: *const c_char,
    cwd: *const c_char,
    argv: *const *const c_char,
    envp: *const *const c_char,
    chdir_error_prefix: &[u8],
    exec_error_prefix: &[u8],
) -> ! {
    unsafe {
        reset_signal_dispositions();

        // Clear signals which the Android java process may have blocked. With the raw system
        // call, like above, and its 64 bit mask, which also unblocks real-time signals on 32 bit
        // Android where sigset_t only has 32 bits.
        let empty_mask = 0u64;
        libc::syscall(
            libc::SYS_rt_sigprocmask,
            libc::SIG_SETMASK,
            &empty_mask as *const u64,
            ptr::null_mut::<libc::c_void>(),
            KERNEL_SIGSET_SIZE,
        );

        libc::close(ptm);
        libc::setsid();

        // Opening the slave after setsid() makes it the controlling terminal of the new session.
        let pts = libc::open(devname, libc::O_RDWR);
        if pts < 0 {
            // The original C code called exit(-1).
            libc::_exit(255);
        }

        libc::dup2(pts, 0);
        libc::dup2(pts, 1);
        libc::dup2(pts, 2);

        close_fds_above_stderr();

        environ = envp;

        if libc::chdir(cwd) != 0 {
            print_error(chdir_error_prefix);
        }
        libc::execvp(cmd, argv);
        // Show terminal output about failing exec() call.
        print_error(exec_error_prefix);
        libc::_exit(1);
    }
}

/// `NSIG` of both bionic and glibc: signals are numbered 1 to 64.
const NSIG: c_int = 65;

/// The size of the kernel's `sigset_t`, for the raw signal system calls: 64 signals.
const KERNEL_SIGSET_SIZE: usize = 8;

/// Reset the disposition of every signal to `SIG_DFL`.
///
/// Signals ignored by the app process would otherwise stay ignored in the shell and everything
/// it runs, since `execve()` only resets signals with handlers, and a shell can not reset signals
/// that were ignored on entry. For example, an ignored `SIGHUP` keeps `ssh`, `sleep` etc. running
/// after the session is exited and the pty is hung up (termux/termux-app#935).
///
/// Done before unblocking signals so that no handler inherited from the app can run in the
/// child. Signals with a handler are reset by `execve()` anyway.
///
/// This uses the raw `rt_sigaction` system call instead of `sigaction()`: in an app process,
/// `sigaction()` is interposed by ART's libsigchain, which does not change the disposition of the
/// signals claimed by ART, and for debuggable apps logs a stack trace when `SIGSEGV` is set to
/// `SIG_DFL`, which is not async-signal-safe.
unsafe fn reset_signal_dispositions() {
    // The kernel's struct sigaction, all zero: handler SIG_DFL (0), no flags, no restorer and an
    // empty mask. It is at most 32 bytes on all architectures, the kernel only reads its size.
    let default_action = [0u64; 4];
    for signal in 1..NSIG {
        if signal == libc::SIGKILL || signal == libc::SIGSTOP {
            continue;
        }
        unsafe {
            libc::syscall(
                libc::SYS_rt_sigaction,
                signal,
                default_action.as_ptr(),
                ptr::null_mut::<libc::c_void>(),
                KERNEL_SIGSET_SIZE,
            )
        };
    }
}

/// Write `<prefix>: <strerror(errno)>\n` to stderr like `perror()`, but async-signal-safe.
fn print_error(prefix: &[u8]) {
    let error = errno();
    let mut message = [0u8; 256];
    unsafe {
        libc::strerror_r(
            error,
            message.as_mut_ptr() as *mut c_char,
            message.len() - 1,
        )
    };
    let message_len = message.iter().position(|&b| b == 0).unwrap_or(0);
    write_all(2, prefix);
    write_all(2, b": ");
    write_all(2, message.get(..message_len).unwrap_or(&[]));
    write_all(2, b"\n");
}

fn write_all(fd: c_int, mut bytes: &[u8]) {
    while !bytes.is_empty() {
        let ret = unsafe { libc::write(fd, bytes.as_ptr() as *const libc::c_void, bytes.len()) };
        if ret < 0 {
            if errno() == libc::EINTR {
                continue;
            }
            return;
        }
        bytes = bytes.get(ret as usize..).unwrap_or(&[]);
    }
}

/// Parse a `/proc/self/fd` entry name into a file descriptor number.
fn parse_fd(name: &[u8]) -> Option<c_int> {
    if name.is_empty() {
        return None;
    }
    let mut fd: c_int = 0;
    for &b in name {
        if !b.is_ascii_digit() {
            return None;
        }
        fd = fd.checked_mul(10)?.checked_add((b - b'0') as c_int)?;
    }
    Some(fd)
}

/// Close all file descriptors greater than 2 inherited from the parent.
///
/// This lists `/proc/self/fd` with the raw `getdents64` system call (`opendir()` allocates).
/// `close_range()` is not used: on Android versions before 14 it is not in the seccomp allowlist
/// for apps, and calling it would kill the process with `SIGSYS` instead of returning `ENOSYS`.
unsafe fn close_fds_above_stderr() {
    // Must be 8 byte aligned for struct linux_dirent64.
    #[repr(C, align(8))]
    struct DirentBuffer([u8; 4096]);

    // Offsets in struct linux_dirent64 { u64 d_ino; s64 d_off; u16 d_reclen; u8 d_type; char d_name[]; }.
    const RECLEN_OFFSET: usize = 16;
    const NAME_OFFSET: usize = 19;

    unsafe {
        let dir_fd = libc::open(
            c"/proc/self/fd".as_ptr(),
            libc::O_RDONLY | libc::O_DIRECTORY | libc::O_CLOEXEC,
        );
        if dir_fd < 0 {
            // Fall back to trying every possible file descriptor.
            let mut limit: libc::rlimit = core::mem::zeroed();
            let max_fd = if libc::getrlimit(libc::RLIMIT_NOFILE, &mut limit) == 0 {
                limit.rlim_cur.min(65536) as c_int
            } else {
                1024
            };
            for fd in 3..max_fd {
                libc::close(fd);
            }
            return;
        }

        let mut buffer = DirentBuffer([0; 4096]);
        loop {
            let read = libc::syscall(
                libc::SYS_getdents64,
                dir_fd,
                buffer.0.as_mut_ptr(),
                buffer.0.len(),
            );
            if read <= 0 {
                break;
            }
            let entries = buffer.0.get(..read as usize).unwrap_or(&[]);
            let mut offset = 0;
            while let Some(reclen) = entries.get(offset + RECLEN_OFFSET..offset + RECLEN_OFFSET + 2)
            {
                let reclen = u16::from_ne_bytes([reclen[0], reclen[1]]) as usize;
                if reclen == 0 {
                    break;
                }
                if let Some(name) = entries.get(offset + NAME_OFFSET..offset + reclen) {
                    let name_len = name.iter().position(|&b| b == 0).unwrap_or(name.len());
                    if let Some(fd) = parse_fd(name.get(..name_len).unwrap_or(&[]))
                        && fd > 2
                        && fd != dir_fd
                    {
                        libc::close(fd);
                    }
                }
                offset += reclen;
            }
        }
        libc::close(dir_fd);
    }
}

pub fn set_window_size(fd: c_int, rows: i32, columns: i32, cell_width: i32, cell_height: i32) {
    let size = winsize(rows, columns, cell_width, cell_height);
    unsafe { libc::ioctl(fd, libc::TIOCSWINSZ as _, &size) };
}

pub fn set_utf8_mode(fd: c_int) {
    unsafe {
        let mut tios: libc::termios = core::mem::zeroed();
        libc::tcgetattr(fd, &mut tios);
        if tios.c_iflag & libc::IUTF8 == 0 {
            tios.c_iflag |= libc::IUTF8;
            libc::tcsetattr(fd, libc::TCSANOW, &tios);
        }
    }
}

/// Wait for the process to exit. Returns the exit status if it exited normally or the negated
/// signal number if it was killed by a signal.
pub fn wait_for(pid: libc::pid_t) -> c_int {
    let mut status: c_int = 0;
    loop {
        let ret = unsafe { libc::waitpid(pid, &mut status, 0) };
        if ret == -1 && errno() == libc::EINTR {
            continue;
        }
        break;
    }
    if libc::WIFEXITED(status) {
        libc::WEXITSTATUS(status)
    } else if libc::WIFSIGNALED(status) {
        -libc::WTERMSIG(status)
    } else {
        // Should never happen - waitpid(2) says "One of the first three macros will evaluate to a
        // non-zero (true) value".
        0
    }
}

/// Kill and reap a subprocess and close its pty, for when it can not be handed over to Java.
pub fn kill_and_close(process: Subprocess) {
    unsafe { libc::kill(process.pid, libc::SIGKILL) };
    wait_for(process.pid);
    close(process.ptm);
}

pub fn close(fd: c_int) {
    unsafe { libc::close(fd) };
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::string::String;

    fn cstrings(strings: &[&str]) -> Vec<CString> {
        strings.iter().map(|s| CString::new(*s).unwrap()).collect()
    }

    /// Read everything from the pty master until the slave side is closed.
    fn read_all(ptm: c_int) -> String {
        let mut output = Vec::new();
        let mut buffer = [0u8; 4096];
        loop {
            let ret =
                unsafe { libc::read(ptm, buffer.as_mut_ptr() as *mut libc::c_void, buffer.len()) };
            if ret < 0 && errno() == libc::EINTR {
                continue;
            }
            // EIO once all slave file descriptors are closed.
            if ret <= 0 {
                break;
            }
            output.extend_from_slice(&buffer[..ret as usize]);
        }
        String::from_utf8(output).unwrap().replace("\r\n", "\n")
    }

    fn run(cwd: &str, argv: &[&str], environment: &[&str]) -> (String, c_int) {
        let argv = cstrings(argv);
        let environment = build_environment(cstrings(environment));
        let process = create_subprocess(
            &argv[0],
            &CString::new(cwd).unwrap(),
            &argv,
            &environment,
            24,
            80,
            10,
            20,
        )
        .unwrap();
        assert!(process.ptm > 2 && process.pid > 0);
        let output = read_all(process.ptm);
        let status = wait_for(process.pid);
        close(process.ptm);
        (output, status)
    }

    fn sh(script: &str) -> (String, c_int) {
        run("/", &["/bin/sh", "-c", script], &["PATH=/usr/bin:/bin"])
    }

    #[test]
    fn environment_matches_putenv() {
        let environment = build_environment(cstrings(&[
            "A=1",
            "B=2",
            "NO_EQUALS",
            "=empty_name",
            "A=3",
            "C=",
            "AB=4",
            "B=5",
        ]));
        assert_eq!(environment, cstrings(&["A=3", "B=5", "C=", "AB=4"]));
        assert!(build_environment(Vec::new()).is_empty());
    }

    #[test]
    fn parses_fd_names() {
        assert_eq!(parse_fd(b"0"), Some(0));
        assert_eq!(parse_fd(b"123"), Some(123));
        assert_eq!(parse_fd(b"."), None);
        assert_eq!(parse_fd(b".."), None);
        assert_eq!(parse_fd(b""), None);
        assert_eq!(parse_fd(b"99999999999"), None);
    }

    #[test]
    fn reports_exit_code() {
        assert_eq!(sh("exit 3"), (String::new(), 3));
        assert_eq!(sh("exit 0").1, 0);
    }

    #[test]
    fn reports_terminating_signal() {
        assert_eq!(sh("kill -9 $$").1, -9);
        assert_eq!(sh("kill -15 $$").1, -15);
    }

    #[test]
    fn passes_argv_environment_and_cwd() {
        let (output, status) = run(
            "/tmp",
            &[
                "/bin/sh",
                "-c",
                "printf '%s|%s|%s|%s|%s' \"$0\" \"$1\" \"$FOO\" \"$BAR\" \"$(pwd)\"",
                "zero",
                "one",
            ],
            &["PATH=/usr/bin:/bin", "FOO=first", "BAR=bar", "FOO=last"],
        );
        assert_eq!(output, "zero|one|last|bar|/tmp");
        assert_eq!(status, 0);
    }

    #[test]
    fn environment_is_replaced() {
        let (output, _) = run(
            "/",
            &["/bin/sh", "-c", "env"],
            &["PATH=/usr/bin:/bin", "ONLY=this"],
        );
        let mut vars: Vec<&str> = output.lines().filter(|l| !l.is_empty()).collect();
        vars.sort();
        // The shell may add a few variables of its own, but nothing from the parent may leak.
        assert!(vars.contains(&"ONLY=this"), "{vars:?}");
        assert!(
            !vars
                .iter()
                .any(|v| v.starts_with("HOME=") || v.starts_with("CARGO")),
            "{vars:?}"
        );
    }

    #[test]
    fn execvp_searches_path_of_new_environment() {
        // A relative command is looked up in the PATH of the environment passed to the child.
        let (output, status) = run("/", &["sh", "-c", "echo found"], &["PATH=/bin:/usr/bin"]);
        assert_eq!((output.as_str(), status), ("found\n", 0));
    }

    #[test]
    fn exec_failure_is_shown_in_terminal() {
        let (output, status) = run("/", &["/nonexistent/binary"], &[]);
        assert_eq!(
            output,
            "exec(\"/nonexistent/binary\"): No such file or directory\n"
        );
        assert_eq!(status, 1);
    }

    #[test]
    fn chdir_failure_is_shown_in_terminal() {
        let (output, status) = run(
            "/nonexistent/dir",
            &["/bin/sh", "-c", "echo ran in $(pwd)"],
            &["PATH=/usr/bin:/bin"],
        );
        // The command still runs, in the inherited working directory.
        let inherited_cwd = std::env::current_dir().unwrap();
        let expected = format!(
            "chdir(\"/nonexistent/dir\"): No such file or directory\nran in {}\n",
            inherited_cwd.display()
        );
        assert_eq!(output, expected);
        assert_eq!(status, 0);
    }

    #[test]
    fn pty_is_controlling_terminal_with_initial_size_and_modes() {
        let (output, status) =
            sh("stty size; stty -a; test -t 0 && test -t 1 && test -t 2 && echo tty");
        assert_eq!(status, 0);
        assert!(output.starts_with("24 80\n"), "{output}");
        assert!(output.contains(" iutf8"), "{output}");
        assert!(
            output.contains("-ixon") && output.contains("-ixoff"),
            "{output}"
        );
        assert!(output.ends_with("tty\n"), "{output}");
        // The shell is a session leader with the pty as controlling terminal: /dev/tty works.
        assert_eq!(sh("echo hello > /dev/tty").0, "hello\n");
    }

    #[test]
    fn resizes_window() {
        let argv = cstrings(&["/bin/sh", "-c", "read line; stty size"]);
        let process = create_subprocess(
            &argv[0],
            c"/",
            &argv,
            &cstrings(&["PATH=/usr/bin:/bin"]),
            24,
            80,
            10,
            20,
        )
        .unwrap();
        set_window_size(process.ptm, 50, 132, 7, 9);
        let mut size: libc::winsize = unsafe { core::mem::zeroed() };
        unsafe { libc::ioctl(process.ptm, libc::TIOCGWINSZ as _, &mut size) };
        assert_eq!(
            (size.ws_row, size.ws_col, size.ws_xpixel, size.ws_ypixel),
            (50, 132, 132 * 7, 50 * 9)
        );
        unsafe { libc::write(process.ptm, b"\n".as_ptr() as *const libc::c_void, 1) };
        let output = read_all(process.ptm);
        assert!(output.ends_with("50 132\n"), "{output}");
        assert_eq!(wait_for(process.pid), 0);
        close(process.ptm);
    }

    #[test]
    fn utf8_mode() {
        let argv = cstrings(&["/bin/sh", "-c", "read line"]);
        let process = create_subprocess(&argv[0], c"/", &argv, &[], 24, 80, 10, 20).unwrap();
        let mut tios: libc::termios = unsafe { core::mem::zeroed() };
        unsafe { libc::tcgetattr(process.ptm, &mut tios) };
        tios.c_iflag &= !libc::IUTF8;
        unsafe { libc::tcsetattr(process.ptm, libc::TCSANOW, &tios) };
        set_utf8_mode(process.ptm);
        unsafe { libc::tcgetattr(process.ptm, &mut tios) };
        assert_ne!(tios.c_iflag & libc::IUTF8, 0);
        unsafe { libc::write(process.ptm, b"\n".as_ptr() as *const libc::c_void, 1) };
        read_all(process.ptm);
        assert_eq!(wait_for(process.pid), 0);
        close(process.ptm);
    }

    /// Serializes the tests changing process wide signal dispositions.
    static SIGNAL_DISPOSITION_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

    fn ignore_signal(signal: c_int) -> libc::sighandler_t {
        unsafe { libc::signal(signal, libc::SIG_IGN) }
    }

    #[test]
    fn ignored_signals_are_not_inherited() {
        let _lock = SIGNAL_DISPOSITION_LOCK.lock().unwrap();
        // The Rust test harness already ignores SIGPIPE, like the Android app process may.
        let previous = ignore_signal(libc::SIGHUP);
        let (output, status) = sh("grep -E '^Sig(Ign|Blk)' /proc/self/status");
        unsafe { libc::signal(libc::SIGHUP, previous) };
        assert_eq!(status, 0);
        assert_eq!(
            output,
            "SigBlk:\t0000000000000000\nSigIgn:\t0000000000000000\n"
        );
    }

    fn is_dead(pid: libc::pid_t) -> bool {
        if unsafe { libc::kill(pid, 0) } == -1 && errno() == libc::ESRCH {
            return true;
        }
        // Reparented to a subreaper that has not reaped it yet.
        std::fs::read_to_string(format!("/proc/{pid}/stat"))
            .map(|stat| {
                stat.rsplit(')')
                    .next()
                    .unwrap_or("")
                    .trim_start()
                    .starts_with('Z')
            })
            .unwrap_or(true)
    }

    /// termux/termux-app#935: closing the pty must hang up the session and kill its processes,
    /// even if the parent process ignores SIGHUP.
    #[test]
    fn closing_pty_hangs_up_background_processes() {
        let _lock = SIGNAL_DISPOSITION_LOCK.lock().unwrap();
        let previous = ignore_signal(libc::SIGHUP);
        let argv = cstrings(&["/bin/sh", "-c", "sleep 30 & echo $!; wait"]);
        let process = create_subprocess(
            &argv[0],
            c"/",
            &argv,
            &cstrings(&["PATH=/usr/bin:/bin"]),
            24,
            80,
            10,
            20,
        );
        unsafe { libc::signal(libc::SIGHUP, previous) };
        let process = process.unwrap();

        let mut line = Vec::new();
        let mut byte = 0u8;
        while unsafe { libc::read(process.ptm, (&raw mut byte).cast(), 1) } == 1 && byte != b'\n' {
            line.push(byte);
        }
        let sleep_pid: libc::pid_t = String::from_utf8(line).unwrap().trim().parse().unwrap();
        assert!(!is_dead(sleep_pid));

        close(process.ptm);
        assert_eq!(wait_for(process.pid), -libc::SIGHUP);
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(5);
        while !is_dead(sleep_pid) && std::time::Instant::now() < deadline {
            std::thread::sleep(std::time::Duration::from_millis(20));
        }
        assert!(
            is_dead(sleep_pid),
            "background sleep {sleep_pid} survived the hangup"
        );
    }

    #[test]
    fn inherited_fds_are_closed_and_signals_unblocked() {
        let path = format!("/tmp/termux-pty-test-{}", std::process::id());
        let cpath = CString::new(path.clone()).unwrap();
        // Not O_CLOEXEC, so it would be inherited if not closed explicitly.
        let fd = unsafe { libc::open(cpath.as_ptr(), libc::O_CREAT | libc::O_RDWR, 0o600) };
        assert!(fd > 2);
        // Block a signal in this thread, the child must not inherit the mask.
        let mut set: libc::sigset_t = unsafe { core::mem::zeroed() };
        unsafe {
            libc::sigemptyset(&mut set);
            libc::sigaddset(&mut set, libc::SIGUSR1);
            libc::pthread_sigmask(libc::SIG_BLOCK, &set, ptr::null_mut());
        }
        let (output, status) = sh("ls -l /proc/$$/fd; grep SigBlk /proc/self/status");
        unsafe {
            libc::pthread_sigmask(libc::SIG_UNBLOCK, &set, ptr::null_mut());
            libc::close(fd);
            libc::unlink(cpath.as_ptr());
        }
        assert_eq!(status, 0);
        assert!(!output.contains(&path), "{output}");
        assert!(output.contains("SigBlk:\t0000000000000000"), "{output}");
        // Only stdin, stdout and stderr (plus whatever ls itself opened) are open.
        let fds: Vec<&str> = output.lines().filter(|l| l.contains(" -> ")).collect();
        assert!(
            fds.iter()
                .all(|l| l.contains("/dev/pts/") || l.contains("/proc/")),
            "{fds:?}"
        );
    }
}
