import java.util.Locale
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing stays out of the repo. Copy keystore.properties.example to
// keystore.properties (gitignored) and point storeFile at a keystore kept
// outside this tree. The same four values can come from the environment,
// which overrides the file, so CI never needs the properties file.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(environmentName: String, propertyName: String): String? =
    System.getenv(environmentName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("HYPERTROPHY_STORE_FILE", "storeFile")
val releaseStorePassword = signingValue("HYPERTROPHY_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("HYPERTROPHY_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("HYPERTROPHY_KEY_PASSWORD", "keyPassword")
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
if (releaseSigningValues.any { it != null } && releaseSigningValues.any { it == null }) {
    error(
        "Release signing is incomplete. Set storeFile, storePassword, keyAlias, and keyPassword " +
            "in keystore.properties, or the HYPERTROPHY_STORE_FILE, HYPERTROPHY_STORE_PASSWORD, " +
            "HYPERTROPHY_KEY_ALIAS, and HYPERTROPHY_KEY_PASSWORD environment variables.",
    )
}
val releaseKeystore = releaseStoreFile?.let { rootProject.file(it) }

android {
    namespace = "com.forge.hypertrophy"
    // 37.0 is the stable platform. 37.2 is a preview, and the current AndroidX
    // libraries refuse to compile against anything older than 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.forge.hypertrophy"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) {
                if (!releaseKeystore.isFile) {
                    error("Release keystore not found: ${releaseKeystore.absolutePath}")
                }
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

abstract class GenerateSampleProgramAssetTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val destination = outputDirectory.get().asFile.resolve("program.json")
        destination.parentFile.mkdirs()
        source.get().asFile.copyTo(destination, overwrite = true)
    }
}

val sampleProgramJson = rootProject.layout.projectDirectory.file("docs/program.json")
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val taskName = "generate" +
        variant.name.replaceFirstChar { it.titlecase(Locale.US) } +
        "SampleProgramAsset"
    val generate = tasks.register(taskName, GenerateSampleProgramAssetTask::class.java) {
        source.set(sampleProgramJson)
        outputDirectory.set(project.layout.buildDirectory.dir("generated/sampleProgram/${variant.name}"))
    }
    val assets = variant.sources.assets
        ?: error("Variant ${variant.name} has no asset source set")
    assets.addGeneratedSourceDirectory(
        generate,
        GenerateSampleProgramAssetTask::outputDirectory,
    )
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Mechanical half of docs/REVIEW_CHECKLISTS.md. Fails the build on the
 * project rules that a grep can catch: Android imports or ambient time in
 * the domain layer, a destructive Room fallback, network code outside the
 * weather repository, and pounds anywhere in production code.
 */
abstract class CheckProjectRulesTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainSources: DirectoryProperty

    @TaskAction
    fun check() {
        val root = mainSources.get().asFile
        val violations = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val relative = file.relativeTo(root).path
            val inDomain = relative.contains("/domain/")
            val isWeather = relative.endsWith("data/weather/WeatherRepository.kt")
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
                fun flag(rule: String) = violations.add("$relative:${index + 1}: $rule")
                if (inDomain && line.startsWith("import android")) flag("domain imports android.*")
                if (inDomain && Regex("""\b(LocalDate|LocalDateTime|Instant|ZonedDateTime)\.now\(""").containsMatchIn(line)) {
                    flag("domain reads ambient time; use the injected Clock")
                }
                if (inDomain && line.contains("System.currentTimeMillis()")) flag("domain reads ambient time; use the injected Clock")
                if (line.contains("fallbackToDestructiveMigration")) flag("destructive migration fallback is forbidden")
                if (!isWeather && Regex("""\b(HttpURLConnection|OkHttpClient|Retrofit|HttpClient)\b""").containsMatchIn(line)) {
                    flag("network code outside WeatherRepository")
                }
                if (Regex("""\b(lbs?|pounds?)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)) flag("kg only")
            }
        }
        if (violations.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Project rule violations:\n" + violations.joinToString("\n") { "  $it" },
            )
        }
    }
}

val checkProjectRules = tasks.register("checkProjectRules", CheckProjectRulesTask::class.java) {
    group = "verification"
    description = "Fails on project rule violations that can be detected mechanically."
    mainSources.set(layout.projectDirectory.dir("src/main/java"))
}

tasks.named("check") {
    dependsOn(checkProjectRules)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vico.compose.m3)
    implementation(libs.play.services.location)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
