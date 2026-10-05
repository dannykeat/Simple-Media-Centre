plugins {
    id("com.android.application")
}

android {
    namespace = "org.simplemediacentre"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.simplemediacentre"
        minSdk = 23
        targetSdk = 37
        versionCode = 2
        versionName = "0.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("io.coil-kt.coil3:coil:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.coil-kt.coil3:coil-video:3.6.3")

    testImplementation("junit:junit:4.13.2")
}
