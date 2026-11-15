fn main() {
    // Align ELF LOAD segments to 16 KB so the library works on devices using 16 KB memory pages.
    // https://developer.android.com/guide/practices/page-sizes
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("android") {
        println!("cargo:rustc-cdylib-link-arg=-Wl,-z,max-page-size=16384");
        // Fail at link time instead of at dlopen() time if a symbol can not be resolved.
        println!("cargo:rustc-cdylib-link-arg=-Wl,-z,defs");
    }
    println!("cargo:rerun-if-changed=build.rs");
}
