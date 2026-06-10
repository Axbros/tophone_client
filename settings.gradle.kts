pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {

    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://artifact.bytedance.com/repository/Volcengine/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Tophone"
include(":app")
