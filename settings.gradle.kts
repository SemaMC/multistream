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
    }
}
rootProject.name = "multistream"
include(":app")
include(":core:common")
include(":core:audio")
include(":core:capture")
include(":core:encoding")
include(":core:streaming")
include(":core:widgets")
include(":data:platforms")
include(":feature:settings")
include(":feature:streaming-ui")
