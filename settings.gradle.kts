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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (offline OCR for receipt PDFs) is published on JitPack only.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.adaptech-cz.*") }
        }
    }
}

rootProject.name = "KaspiTracker"
include(":app")
