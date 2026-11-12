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

    testImplementation(kotlinx.test.junit5)
    testImplementation(cmp.ui.test)
}

compose.desktop {
    application {
        mainClass = "org.nekomanga.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Neko"
            packageVersion = "1.0.0"
            description = "Unofficial MangaDex reader"
            vendor = "NekoMangaOrg"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
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
