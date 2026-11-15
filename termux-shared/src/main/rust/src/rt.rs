//! Minimal `no_std` runtime: a global allocator backed by the platform `malloc()` and a panic
//! handler that aborts.
//!
//! Using `core` + `alloc` instead of `std` keeps the library small, as `std` would pull in its
//! panic and backtrace printing machinery. Host unit tests are built with `std` instead, see
//! `lib.rs`.

use core::alloc::{GlobalAlloc, Layout};

struct LibcAllocator;

/// The alignment that `malloc()` guarantees, same as what `std` assumes for these targets.
#[cfg(target_pointer_width = "64")]
const MIN_ALIGN: usize = 16;
#[cfg(not(target_pointer_width = "64"))]
const MIN_ALIGN: usize = 8;

unsafe impl GlobalAlloc for LibcAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        if layout.align() <= MIN_ALIGN && layout.align() <= layout.size() {
            unsafe { libc::malloc(layout.size()) as *mut u8 }
        } else {
            let mut out: *mut libc::c_void = core::ptr::null_mut();
            let align = layout.align().max(core::mem::size_of::<usize>());
            if unsafe { libc::posix_memalign(&mut out, align, layout.size()) } != 0 {
                return core::ptr::null_mut();
            }
            out as *mut u8
        }
    }

    unsafe fn dealloc(&self, ptr: *mut u8, _layout: Layout) {
        unsafe { libc::free(ptr as *mut libc::c_void) }
    }

    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        if layout.align() <= MIN_ALIGN && layout.align() <= new_size {
            unsafe { libc::realloc(ptr as *mut libc::c_void, new_size) as *mut u8 }
        } else {
            let new_layout = unsafe { Layout::from_size_align_unchecked(new_size, layout.align()) };
            let new_ptr = unsafe { self.alloc(new_layout) };
            if !new_ptr.is_null() {
                let size = layout.size().min(new_size);
                unsafe {
                    core::ptr::copy_nonoverlapping(ptr, new_ptr, size);
                    self.dealloc(ptr, layout);
                }
            }
            new_ptr
        }
    }
}

#[global_allocator]
static ALLOCATOR: LibcAllocator = LibcAllocator;

#[panic_handler]
fn panic(_info: &core::panic::PanicInfo) -> ! {
    // The code is written to not panic. If it ever does, crash like a failed C assert() would.
    unsafe { libc::abort() }
}

// The precompiled `core` and `alloc` libraries are built with `panic = "unwind"`, so their
// cleanup landing pads still reference the unwinder, which `std` would provide. They are never
// used, since a panic aborts instead of unwinding and no foreign exception can unwind through the
// JNI functions. Without these definitions, linking fails with `-z defs` (see build.rs), and
// without that `dlopen()` would fail with "cannot locate symbol".

#[unsafe(no_mangle)]
extern "C" fn rust_eh_personality() {}

#[unsafe(no_mangle)]
extern "C" fn _Unwind_Resume(_exception: *mut core::ffi::c_void) -> ! {
    unsafe { libc::abort() }
}
