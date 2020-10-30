import javassist.ClassPool
import javassist.CtMethod
import org.apache.tools.ant.filters.FixCrLfFilter
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.support.zipTo
import java.security.MessageDigest
import java.util.*
import kotlin.concurrent.thread

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
}

buildscript {
    dependencies {
        classpath("javassist:javassist:3.9.0.GA")
    }
}

rootProject.allprojects.forEach { it.buildDir.deleteRecursively() }

val properties =
    Properties().apply { load(file("src/main/assets/module.properties").inputStream()) }
val moduleId: String = properties.getProperty("moduleId")
val moduleName: String = properties.getProperty("moduleName")
val moduleAuthor: String = properties.getProperty("moduleAuthor")
val moduleDescription: String = properties.getProperty("moduleDescription")
val moduleVersion: String = properties.getProperty("moduleVersion")
val moduleDexPath: String = "system/framework/$moduleId.dex"
val moduleMainClass: String = properties.getProperty("moduleMainClass")
val targetProcessName: String = properties.getProperty("targetProcessName")
val libraryPath: String =
    properties.getProperty("libraryPath").let { if (it.isEmpty()) moduleId else it }
val automaticInstallation: Boolean = properties.getProperty("automaticInstallation") == "1"

val platform: OperatingSystem = OperatingSystem.current()

android {

    compileSdkVersion(30)
    buildToolsVersion("30.0.2")

    defaultConfig {

        minSdkVersion(23)
        targetSdkVersion(30)

        versionCode = 1
        versionName = moduleVersion

        externalNativeBuild {
            cmake {
                arguments("-DLIBRARY_NAME:STRING=riru_$moduleId")
            }
        }
        checkProperties()
        refreshMainHeader()
        refreshConstants()
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    libraryVariants.all {
        if (name == "release") {
            sdkDirectory.buildModule()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.3.2")
}

fun checkProperties() {
    if (moduleId.isEmpty()) {
        error("moduleId must be fill out!")
    }
    if (moduleMainClass.isEmpty()) {
        error("moduleMainClass must be fill out!")
    }
}

fun refreshMainHeader() =
    targetProcessName.split(";").filter { it.isNotBlank() && it.isNotEmpty() }.run {
        file("src/main/cpp/main.h").writeText(
            file("src/main/resource/template_main_header").readText()
                .format(moduleDexPath, joinToString("\", \"", "\"", "\""), size)
        )
    }

fun refreshConstants() = file("src/main/java/com/wuyr/hookworm/core/Constants.java").writeText(
    file("src/main/resource/template_constants").readText()
        .format(moduleMainClass, libraryPath)
)

fun File.buildModule() {
    refreshConstantsAgain()
    val task = rootProject.project("app").tasks.find { it.name == "assemble" }
        ?: error("Please dependent on to an app module!")
    val buildDir = task.project.buildDir.also { it.deleteRecursively() }
    task.doLast {
        val zipPath = buildDir.resolve("intermediates/magisk/").apply {
            deleteRecursively()
            mkdirs()
        }
        copy {
            into(zipPath)
            processResource()
            processScript()
            processProperties()
            zipTree(File(buildDir, "outputs/apk/release/app-release-unsigned.apk")
                .also { if (!it.exists()) error("${it.name} not found!") }).let { apkFileTree ->
                processLibs(apkFileTree)
                processDex(apkFileTree)
            }
        }
        zipPath.apply {
            resolve("extras.files").writeText("${moduleDexPath}\n")
            fixLineBreaks()
            generateSHA256Sum()
        }
        buildDir.resolve("outputs/module").also { it.mkdirs() }.run {
            val moduleFile = File(this, "${moduleId}.zip")
            zipTo(moduleFile, zipPath)
            if (automaticInstallation) {
                if (isDeviceConnected(this@buildModule)) {
                    if (installModuleFailed(this@buildModule, moduleFile, zipPath)) {
                        openInGUI()
                        error("Module installation failed!")
                    }
                } else {
                    openInGUI()
                    error("Device not connected or connected more than one device!")
                }
            } else {
                openInGUI()
            }
        }
    }
}

fun refreshConstantsAgain() = (project.tasks.find { it.name == "compileReleaseJavaWithJavac" }
    ?: error("Task 'compileReleaseJavaWithJavac' not found!"))
    .doLast {
        val hasSOFile =
            (rootProject.project("app").tasks.find { it.name == "mergeReleaseNativeLibs" }
                ?: error("Task 'mergeReleaseNativeLibs' not found!")).run {
                this is com.android.build.gradle.internal.tasks.MergeNativeLibsTask && externalLibNativeLibs.files.any {
                    it.isDirectory && it.list()?.isNotEmpty() == true
                }
            }
        if (hasSOFile) {
            val classPool = ClassPool.getDefault()
            val constantsClassPath =
                buildDir.resolve("intermediates/javac/release/classes").absolutePath
            classPool.insertClassPath(constantsClassPath)
            classPool.getCtClass("com.wuyr.hookworm.core.Constants").run {
                getDeclaredMethod("hasSOFile").name = "hasSOFileOld"
                addMethod(CtMethod.make("static boolean hasSOFile(){ return true; }", this))
                writeFile(constantsClassPath)
                detach()
            }
        }
    }

fun CopySpec.processResource() = from(file("src/main/resource")) {
    exclude(
        "riru.sh", "module.prop", "riru/module.prop.new",
        "template_constants", "template_main_header"
    )
}

fun CopySpec.processScript() =
    from(file("src/main/resource/riru.sh")) {
        filter { line ->
            line.replace("%%%RIRU_MODULE_ID%%%", moduleId)
                .replace("%%%RIRU_MIN_API_VERSION%%%", "7")
                .replace("%%%RIRU_MIN_VERSION_NAME%%%", moduleVersion)
        }
        filter(FixCrLfFilter::class.java)
    }

fun CopySpec.processProperties() {
    from(file("src/main/resource/module.prop")) {
        filter { line ->
            line.replace("%%%MAGISK_ID%%%", moduleId)
                .replace("%%%MAIGKS_NAME%%%", moduleName)
                .replace("%%%MAGISK_VERSION_NAME%%%", moduleVersion)
                .replace("%%%MAGISK_AUTHOR%%%", moduleAuthor)
                .replace("%%%MAGISK_DESCRIPTION%%%", moduleDescription)
                .replace("%%%TARGET_PROCESS_NAME%%%", targetProcessName)
                .replace("%%%LIBRARY_PATH%%%", libraryPath)
        }
        filter(FixCrLfFilter::class.java)
    }
    from(file("src/main/resource/riru/module.prop.new")) {
        into("riru/")
        filter { line ->
            line.replace("%%%RIRU_NAME%%%", moduleName)
                .replace("%%%RIRU_VERSION_NAME%%%", moduleVersion)
                .replace("%%%RIRU_AUTHOR%%%", moduleAuthor)
                .replace("%%%RIRU_DESCRIPTION%%%", moduleDescription)
                .replace("%%%RIRU_API%%%", "7")
        }
        filter(FixCrLfFilter::class.java)
    }
}

fun CopySpec.processLibs(apkFileTree: FileTree) = from(apkFileTree) {
    include("lib/**")
    eachFile {
        path = path.replace("lib/armeabi-v7a", "system/lib")
            .replace("lib/armeabi", "system/lib")
            .replace("lib/x86_64", "system_x86/lib64")
            .replace("lib/x86", "system_x86/lib")
            .replace("lib/arm64-v8a", "system/lib64")
    }
}

fun CopySpec.processDex(apkFileTree: FileTree) = from(apkFileTree) {
    include("classes.dex")
    eachFile { path = moduleDexPath }
}

fun File.fixLineBreaks() {
    val ignoreFiles = arrayOf("system", "system_x86")
    fun walk(file: File) {
        if (file.isDirectory) {
            if (!ignoreFiles.contains(file.name)) {
                file.listFiles()?.forEach { if (!ignoreFiles.contains(it.name)) walk(it) }
            }
        } else {
            file.readText().run {
                if (contains("\r\n")) file.writeText(replace("\r\n", "\n"))
            }
        }
    }
    walk(this)
}

fun File.generateSHA256Sum() = fileTree(this).matching {
    exclude("customize.sh", "verify.sh", "META-INF")
}.filter { it.isFile }.forEach { file ->
    File(file.absolutePath + ".sha256sum").writeText(
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).run {
            joinToString("") { String.format("%02x", it.toInt() and 0xFF) }
        })
}

fun File.openInGUI() = platform.runCatching {
    Runtime.getRuntime().exec(
        "${
        when {
            isWindows -> "explorer"
            isLinux -> "nautilus"
            isMacOsX -> "open"
            else -> ""
        }
        } ${this@openInGUI}"
    )
}.isSuccess

val platformArgs: Array<String>
    get() = if (platform.isWindows) arrayOf("cmd", "/C") else arrayOf("/bin/bash", "-c")

val File.adb: String get() = if (platform.isWindows) "SET Path=$this/platform-tools&&adb" else "$this/platform-tools/adb"
val File.adbWithoutSetup: String get() = if (platform.isWindows) "adb" else "$this/platform-tools/adb"

fun isDeviceConnected(sdkDirectory: File) = platform.runCatching {
    Runtime.getRuntime().exec(arrayOf(*platformArgs, "${sdkDirectory.adb} devices")).run {
        waitFor()
        inputStream.reader().readLines().size == 3.also { destroy() }
    }
}.getOrDefault(false)

fun installModuleFailed(sdkDirectory: File, moduleFile: File, zipPath: File) =
    "${sdkDirectory.adb} push $moduleFile /data/local/tmp/&&${sdkDirectory.adbWithoutSetup} push $zipPath/META-INF/com/google/android/update-binary /data/local/tmp/&&${sdkDirectory.adbWithoutSetup} shell su".runCatching {
        Runtime.getRuntime().exec(arrayOf(*platformArgs, this)).run {
            thread(isDaemon = true) { readContentSafely(inputStream) { it.p() } }
            thread(isDaemon = true) { readContentSafely(errorStream) { it.p() } }
            outputStream.run {
                write("cd /data/local/tmp&&BOOTMODE=true sh update-binary dummy 1 ${moduleFile.name}&&rm update-binary&&rm ${moduleFile.name}&&reboot\n".toByteArray())
                flush()
                close()
            }
            waitFor()
            destroy()
        }
        false
    }.getOrDefault(true)

fun Process.readContentSafely(inputStream: java.io.InputStream, onReadLine: (String) -> Unit) {
    runCatching {
        inputStream.bufferedReader().use { reader ->
            var line = ""
            while (runCatching { exitValue() }.isFailure && reader.readLine()
                    ?.also { line = it } != null
            ) {
                onReadLine(line)
            }
        }
    }
}

fun Any?.p() = println(this)