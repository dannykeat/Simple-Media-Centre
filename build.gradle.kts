buildscript {
    dependencies {
        // Coil 3.6.3 requires Kotlin 2.4 metadata support.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
}
