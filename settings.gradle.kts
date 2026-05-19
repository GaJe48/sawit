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
        maven {
            val dependencyText = providers.exec {
                commandLine("cargo", "metadata", "--format-version", "1", "--manifest-path",
                    File(rootDir, "lms-rust/Cargo.toml").absolutePath)
            }.standardOutput.asText.get()

            @Suppress("UNCHECKED_CAST")
            val dependencyJson = groovy.json.JsonSlurper().parseText(dependencyText) as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val packages = dependencyJson["packages"] as List<Map<String, Any>>
            val manifestPath = packages.first { it["name"] == "rustls-platform-verifier-android" }["manifest_path"] as String
            url = uri(File(File(manifestPath).parentFile, "maven"))

            content {
                includeGroup("rustls")
            }
        }
    }
}

rootProject.name = "LMS Unindra"
include(":app")
