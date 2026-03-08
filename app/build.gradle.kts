plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin.get() // 加这一行启用 Compose
}

val APP_VERSION_NAME: String by project
val APP_VERSION_CODE: String by project
val APP_ID: String by project

android {
    compileSdk = libs.versions.compile.sdk.version.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.version.get().toInt()
        namespace = APP_ID
        applicationId = APP_ID
        versionCode = APP_VERSION_CODE.toInt()
        versionName = APP_VERSION_NAME
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        compose = true          // 加这一行
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compilerextension.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable.add("GradleDependency")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.libraryAndroid)
    implementation(projects.libraryCompose)
    implementation(projects.libraryKotlin)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraint.layout)
    implementation(libs.androidx.core.ktx)

    // 加 Compose 相关（BOM 统一版本）
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)  // Activity → Compose

    // 加 DataStore 用于保存按钮列表
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
}
