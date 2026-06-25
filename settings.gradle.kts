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
        // MLCChat repository (if using their prebuilt AARs or custom maven repo)
        maven { url = uri("https://mlc.ai/wheels") }
    }
}

rootProject.name = "Meetcord.ai"
include(":app")
