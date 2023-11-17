plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.squareup.sqldelight")
}

android {
    namespace = "tachiyomi.data"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    sqldelight {
        database("Database") {
            packageName = "tachiyomi.data"
            dialect = "sqlite:3.24"
            schemaOutputDirectory = project.file("./src/main/sqldelight")
            sourceFolders = listOf("sqldelight")
        }
        database("AnimeDatabase") {
            packageName = "tachiyomi.mi.data"
            dialect = "sqlite:3.24"
            schemaOutputDirectory = project.file("./src/main/sqldelightanime")
            sourceFolders = listOf("sqldelightanime")
        }
    }
}

dependencies {
    implementation(project(":source-api"))
    implementation(project(":domain"))
    implementation(project(":core"))

    api(libs.sqldelight.android.driver)
    api(libs.sqldelight.coroutines)
    api(libs.sqldelight.android.paging)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xcontext-receivers",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
