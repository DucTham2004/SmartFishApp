// build.gradle.kts (File gốc của Project, KHÔNG PHẢI file trong thư mục 'app')

plugins {
    // Thay thế "alias(libs.plugins...)" bằng các dòng "id(...) version "..."
    id("com.android.application") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20" apply false
}