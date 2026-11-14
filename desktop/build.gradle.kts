import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id(kotlinx.plugins.jvm.get().pluginId)
    alias(kotlinx.plugins.compose.compiler)
    alias(cmp.plugins.compose)
    alias(libs.plugins.ktfmt)
}

// Match the jvmTarget the root build sets for every Kotlin compile task.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

ktfmt {
    kotlinLangStyle()
    removeUnusedImports = true
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(cmp.material3)
    implementation(cmp.icons.extended)
    implementation(cmp.haze)
    implementation(cmp.coil.compose)
    implementation(cmp.coil.okhttp)
    implementation(cmp.coroutines.swing)
    implementation(cmp.jna)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(projects.shared)

    testImplementation(kotlinx.test.junit5)
    testImplementation(cmp.ui.test)
    testImplementation(kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "org.nekomanga.desktop.MainKt"
        jvmArgs += "-Dbeako.version=${providers.gradleProperty("beakoVersion").get()}"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            // Bundle the whole Java runtime. The automatic module list left out jdk.httpserver
            // (sign-in crashed in 0.1.0) and anything only extensions use; CI's packaged
            // `--self-test` checks the result.
            includeAllModules = true
            packageName = "Beako"
            packageVersion = providers.gradleProperty("beakoVersion").get()
            description = "Manga reader for Windows, based on Neko"
            vendor = "NekoMangaOrg"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                iconFile.set(project.file("packaging/neko.ico"))
                menu = true
                shortcut = true
                dirChooser = true
                // Fixed so new installers upgrade the existing install instead of adding a second
                // one.
                upgradeUuid = "c5e9dde3-9154-41c4-84cd-ace0d2dd143f"
            }
        }
    }
}
