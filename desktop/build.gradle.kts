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
    implementation(libs.okhttp)
    implementation(projects.shared)

    testImplementation(kotlinx.test.junit5)
    testImplementation(cmp.ui.test)
}

compose.desktop {
    application {
        mainClass = "org.nekomanga.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
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
