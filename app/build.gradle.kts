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
        versionCode = 1
        versionName = "0.2.0"
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

    testImplementation("junit:junit:4.13.2")
}
