import org.gradle.api.file.FileSystemOperations
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
}

// Every ABI is built by default. A comma separated -Pkboard.abi=arm64-v8a builds fewer,
// which is quicker while developing.
val allAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val selectedAbis: List<String> = providers.gradleProperty("kboard.abi")
    .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
    .getOrElse(allAbis)

// Release signing is optional: F-Droid signs the APKs it builds itself, and CI produces
// unsigned ones unless these variables are set.
val releaseKeystore: String? = providers.environmentVariable("KBOARD_KEYSTORE").orNull

android {
    namespace = "io.github.keyang556.kboard"
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "io.github.keyang556.kboard"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += selectedAbis
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("KBOARD_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KBOARD_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KBOARD_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // A git commit id inside the APK would differ between a release tarball and a
            // clone, breaking reproducible builds.
            vcsInfo {
                include = false
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    // F-Droid rejects the encrypted dependency report Google Play uses.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // x86_64 is built unless -Pkboard.abi leaves it out on purpose; lint cannot tell.
        disable += "ChromeOsAbiSupport"
    }
}

base {
    archivesName = "kboard-${android.defaultConfig.versionName}"
}

/**
 * Compiles libchewing's dictionaries (tsi.dat, word.dat) with the chewing-cli tool, built
 * for this machine by cargo, and gathers them with the keyboard tables libchewing reads.
 * The result is packaged as assets, and copied to the device on first use.
 */
abstract class BuildChewingData : DefaultTask() {
    @get:Internal
    abstract val chewingDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataSources: ConfigurableFileCollection

    @get:Internal
    abstract val cargoTargetDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun build() {
        val chewing = chewingDir.get().asFile
        val dictionaries = chewing.resolve("data/dict/chewing")
        if (!dictionaries.resolve("tsi.csv").isFile) {
            throw GradleException(
                "libchewing's dictionary sources are missing. Run: git submodule update --init --recursive"
            )
        }
        val output = outputDir.get().asFile
        fileSystemOperations.delete { delete(output) }
        output.mkdirs()

        // Not --locked: newer cargo wants to refresh libchewing's own lock file, which only
        // fills in what is missing, as Corrosion's build of the library does too.
        for (name in listOf("tsi", "word")) {
            execOperations.exec {
                workingDir(chewing)
                commandLine(
                    "cargo", "run", "--quiet", "--release",
                    "--package", "chewing-cli",
                    "--target-dir", cargoTargetDir.get().asFile.absolutePath,
                    "--",
                    "init", "--csv",
                    dictionaries.resolve("$name.csv").absolutePath,
                    output.resolve("$name.dat").absolutePath,
                )
            }
        }
        fileSystemOperations.copy {
            from(chewing.resolve("data/misc")) {
                include("swkb.dat", "symbols.dat")
            }
            into(output)
        }
    }
}

val chewingSourceDir = layout.projectDirectory.dir("src/main/cpp/libchewing")

androidComponents {
    onVariants { variant ->
        val taskName = "build${variant.name.replaceFirstChar(Char::uppercaseChar)}ChewingData"
        val buildChewingData = tasks.register<BuildChewingData>(taskName) {
            chewingDir.set(chewingSourceDir)
            dataSources.from(
                chewingSourceDir.file("data/dict/chewing/tsi.csv"),
                chewingSourceDir.file("data/dict/chewing/word.csv"),
                chewingSourceDir.file("data/misc/swkb.dat"),
                chewingSourceDir.file("data/misc/symbols.dat"),
            )
            cargoTargetDir.set(layout.buildDirectory.dir("cargo-host"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(buildChewingData, BuildChewingData::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
