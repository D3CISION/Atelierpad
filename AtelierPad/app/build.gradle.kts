plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "hu.atelierpad.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "hu.atelierpad.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    testOptions { unitTests.isReturnDefaultValues = false }
    sourceSets.getByName("test") {
        java.srcDir("../tests")
        resources.srcDir("../tests/fixtures")
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
