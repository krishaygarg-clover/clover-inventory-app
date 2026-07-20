import org.gradle.kotlin.dsl.create
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            val projectDir = project.projectDir
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.json)
                implementation(libs.ktor.client.negotiation)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.constraintlayout)
                implementation(libs.material)
                implementation("androidx.activity:activity-compose:1.8.0")
            }
        }
        val wasmJsMain by getting {
            dependencies {
            }
        }
    }
}

android {
    namespace = "com.example.helloworld"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.helloworld"
        minSdk = 24
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 4. Tells the Lint checker to ignore Google Play Store rules
    lint {
        disable.add("ExpiredTargetSdkVersion")
        abortOnError = false
    }

    // 5. Applies your local keystore using only the V1 scheme
    signingConfigs {
        create("cloverConfig") {
            storeFile = file("/Users/krishayg/Desktop/Untitled.jks")
            storePassword = "Krishayg5!"
            keyAlias = "key0"
            keyPassword = "Krishayg5!"

            enableV1Signing = true
            enableV2Signing = false
            enableV3Signing = false
            enableV4Signing = false
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("cloverConfig")
            optimization {
                enable = false
            }
        }
    }

    // 6. Safe to use Java 11 again
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
