fn main() {
    println!("cargo:rerun-if-changed=assets/icon.ico");
    // Give the .exe its icon in Explorer and the taskbar.
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        let mut res = winresource::WindowsResource::new();
        res.set_icon("assets/icon.ico");
        res.set("ProductName", "Neko");
        res.set("FileDescription", "Neko — MangaDex reader");
        if let Err(e) = res.compile() {
            println!("cargo:warning=couldn't embed the app icon: {e}");
        }
    }
}
