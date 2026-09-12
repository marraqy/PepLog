import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keys = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
android {
    namespace = "app.peptides.journal"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.peptides.journal"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.6.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (keys.isNotEmpty()) signingConfigs.create("localRelease") {
        storeFile = rootProject.file(keys.getProperty("storeFile"))
        storePassword = keys.getProperty("storePassword")
        keyAlias = keys.getProperty("keyAlias")
        keyPassword = keys.getProperty("keyPassword")
    }
    buildTypes { release {
        if (keys.isNotEmpty()) signingConfig = signingConfigs.getByName("localRelease")
        isMinifyEnabled = false
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    bundle { language { enableSplit = false } }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
// Both variants export the same Room schema file.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach { mustRunAfter("kspDebugKotlin") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
