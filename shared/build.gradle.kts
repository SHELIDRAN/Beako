plugins {
    id(kotlinx.plugins.jvm.get().pluginId)
    alias(kotlinx.plugins.serialization)
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
    api(kotlinx.coroutines.core)
    api(kotlinx.serialization.json)
    implementation(kotlinx.serialization.protobuf)
    implementation(libs.okhttp)

    testImplementation(kotlinx.test.junit5)
    testImplementation(kotlinx.coroutines.test)
}

tasks.withType<Test> {
    // Lets `BEAKO_LIVE=1 ./gradlew :shared:test` run the live API tests.
    inputs.property("beakoLive", providers.environmentVariable("BEAKO_LIVE").orElse(""))
    testLogging { showStandardStreams = true }
}
