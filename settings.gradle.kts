pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven ("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/public")
        // 荣耀官方 SDK 仓库：随心握 SmartGripKit 只发在这里，Maven Central 上查不到
        maven("https://developer.hihonor.com/repo")
        // JCenter 已于 2021 年下线，但 me.panpf:sketch-gif 2.7.1（SketchImageViewLoader 用）
        // 及 pl.droidsonroids:relinker 等老库只发布在 JCenter，Maven Central 上查不到，
        // CI 冷缓存下会直接解析失败；用阿里云的 JCenter 缓存镜像兜底（放最后，仅兜底）。
        maven("https://maven.aliyun.com/repository/jcenter")
        gradlePluginPortal()
    }
}

rootProject.name = "c001apk"
include(":app", ":mojito", ":SketchImageViewLoader", ":GlideImageLoader")
 