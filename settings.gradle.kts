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

// 主应用（提供 module-system）
includeBuild("../neton-application")

// 会员模块
includeBuild("../neton-application-module-member")
