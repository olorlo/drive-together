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
        // Pleos Playground SDK (Vehicle 등)
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroupByRegex("ai\\.pleos\\.playground(\\..+)?") }
        }
    }
}

rootProject.name = "gachigayo"
include(":app")
