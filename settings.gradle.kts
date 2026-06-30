pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = "module-payment"

// 框架
includeBuild("../neton")

// canonicalize：统一用 ../../Neton/ 前缀指向 Neton canonical 工作区（跨工作区一致）

// 会员模块（canonical）
includeBuild("../../Neton/neton-application-module-member")
