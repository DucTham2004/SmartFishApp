plugins {
    id("com.android.application") // THAY ĐỔI: Xóa alias, dùng id
    id("org.jetbrains.kotlin.android") // THAY ĐỔI: Xóa alias, dùng id
    id("org.jetbrains.kotlin.plugin.serialization") // THAY ĐỔI: Đổi cách khai báo và xóa version
}

android {
    namespace = "com.example.smartfish"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smartfish"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.core:core-ktx:1.10.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.Dhaval2404:ColorPicker:2.3")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.activity:activity:1.10.0") //Or your desired OkHttp 4.x version
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

}