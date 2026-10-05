import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.rikka.tools.materialthemebuilder)
    alias(libs.plugins.google.dagger.hilt.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

apply(plugin = "kotlin-kapt")

kapt {
    generateStubs = true
    correctErrorTypes = true
}

materialThemeBuilder {
    themes {
        for ((name, color) in listOf(
            "Default" to "6750A4",
            "Red" to "F44336",
            "Pink" to "E91E63",
            "Purple" to "9C27B0",
            "DeepPurple" to "673AB7",
            "Indigo" to "3F51B5",
            "Blue" to "2196F3",
            "LightBlue" to "03A9F4",
            "Cyan" to "00BCD4",
            "Teal" to "009688",
            "Green" to "4FAF50",
            "LightGreen" to "8BC3A4",
            "Lime" to "CDDC39",
            "Yellow" to "FFEB3B",
            "Amber" to "FFC107",
            "Orange" to "FF9800",
            "DeepOrange" to "FF5722",
            "Brown" to "795548",
            "BlueGrey" to "607D8F",
            "Sakura" to "FF9CA8"
        )) {
            create("Material$name") {
                lightThemeFormat = "ThemeOverlay.Light.%s"
                darkThemeFormat = "ThemeOverlay.Dark.%s"
                primaryColor = "#$color"
            }
        }
    }
    // Add Material Design 3 color tokens (such as palettePrimary100) in generated theme
    // rikka.material >= 2.0.0 provides such attributes
    generatePalette = true
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    rootProject.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

// ===== 发行版本（唯一真源：仓库根目录 version.properties，发布只改那个文件）=====
// 规则：只有 beta 阶段主动推进版本号，main 继承 beta 的版本号；debug 快速迭代不涨号。
val releaseProps = Properties().also { it.load(rootProject.file("version.properties").inputStream()) }
val verCode = releaseProps.getProperty("VERSION_CODE").trim().toInt()
val verTag = releaseProps.getProperty("VERSION_NAME").trim()
// 发行渠道：CI 按分支传 -Pchannel=release|beta|debug；本地不传默认 release
val channel = (findProperty("channel") as String?)?.takeIf { it.isNotBlank() } ?: "release"
// versionName 统一前缀（与仓库同名）：c001apk_next-V1.0.1-release
val apkPrefix = "c001apk_next"

android {
    // 注意：namespace 决定 R / ViewBinding / DataBinding 生成类的包名，
    // 源码里全是 import com.example.c001apk.R / com.example.c001apk.databinding.*，不能跟着改名
    namespace = "com.example.c001apk"
    compileSdk = 34

    defaultConfig {
        // 包名同样保持不变：改 applicationId 等于换一个 App，老用户无法覆盖安装
        applicationId = "com.example.c001apk"
        minSdk = 24
        targetSdk = 34
        versionCode = verCode
        // 完整 versionName = 前缀-版本号-渠道，渠道后缀由 buildTypes.versionNameSuffix 追加
        versionName = "$apkPrefix-$verTag"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProperties = Properties().also {
        val properties = rootProject.file("local.properties")
        if (properties.exists())
            it.load(properties.inputStream())
    }
    val config = localProperties.getProperty("KEYSTORE_PATH")?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
            keyAlias = localProperties.getProperty("KEY_ALIAS")
            keyPassword = localProperties.getProperty("KEY_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        all {
            signingConfig = config ?: signingConfigs["debug"]
            // debug 频道的包 CI 是用 release 变体打的（BuildConfig.DEBUG=false），
            // 但排障需要看云端报文，所以这里单独开一个开关
            buildConfigField("boolean", "HTTP_LOG", (channel == "debug" || name == "debug").toString())
        }
        release {
            // 拼出完整版本名：c001apk_next-V1.0.1-release（beta 分支为 -beta）
            versionNameSuffix = "-$channel"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // 调试包恒为 c001apk_next-V1.0.1-debug
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
    defaultConfig {
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
//            abiFilters.add("armeabi")
//            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    applicationVariants.configureEach {
        // APK 文件名与 versionName 严格一致：c001apk_next-V1.0.1-release(10000).apk
        val apkFileName = "$versionName($versionCode)"
        outputs.configureEach {
            (this as? ApkVariantOutputImpl)?.outputFileName = "$apkFileName.apk"
        }
    }
}

configurations.configureEach {
    exclude("androidx.appcompat", "appcompat")
}

dependencies {
    androidTestImplementation(libs.androidx.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.android.flexbox)
    implementation(libs.google.android.material)
    implementation(libs.google.dagger.hilt.android)
    ksp(libs.google.dagger.hilt.android.compiler)
    implementation(libs.rikkax.borderview)
    implementation(libs.rikkax.material.preference)
    implementation(libs.rikkax.material)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.glide.okhttp3.integration)
    implementation(libs.glide.transformations)
    implementation("com.github.QWEA0:liquidglass:v2.0.11")
    implementation(project(":mojito"))
    implementation(project(":SketchImageViewLoader"))
    implementation(project(":GlideImageLoader"))
    implementation(libs.appcenter.analytics)
    implementation(libs.appcenter.crashes)
    implementation(libs.drakeet.about)
    implementation(libs.jbcrypt)
    implementation(libs.jsoup)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.image.glide)
    testImplementation(libs.junit)
    implementation(libs.oss.android.sdk)
    implementation(libs.utilcode)

}