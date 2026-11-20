import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api("com.squareup.retrofit2:retrofit:2.9.0")
    api("com.squareup.retrofit2:converter-gson:2.9.0")
    api("com.google.code.gson:gson:2.10.1")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.okhttp3:logging-interceptor:4.12.0")
    api("io.reactivex.rxjava2:rxjava:2.2.21")
    api("com.jayway.jsonpath:json-path:2.4.0")
    api("com.github.florianingerl.util:regex:1.1.11")
    api("com.grack:nanojson:1.7")
    api("commons-io:commons-io:2.8.0")
    api("androidx.annotation:annotation:1.9.1")
    api("org.graalvm.polyglot:polyglot:24.1.1")
    api("org.graalvm.js:js:24.1.1")
    api("org.slf4j:slf4j-api:2.0.17")
    api("net.sf.kxml:kxml2:2.3.0")
    api("org.json:json:20240303")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
    systemProperty("java.awt.headless", "true")
}
