import com.android.build.api.dsl.ExternalNativeCmakeOptions
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

if (!file("module.properties").exists()) {
    error("Please copy \"module.properties.sample\" and rename to \"module.properties\" and fill in the module information!")
}

val versionsProp =
    Properties().apply { load(file("src/main/resource/versions.properties").inputStream()) }
val moduleCompileSdkVersion = versionsProp.getProperty("compileSdkVersion").toInt()
val moduleMinSdkVersion = versionsProp.getProperty("minSdkVersion").toInt()
val moduleTargetSdkVersion = versionsProp.getProperty("targetSdkVersion").toInt()
val cmakeVersion: String = versionsProp.getProperty("cmakeVersion")
val corektxVersion: String = versionsProp.getProperty("core-ktxVersion")
val maxRiruApiVersionCode = versionsProp.getProperty("maxRiruApiVersionCode").toInt()
val minRiruApiVersionCode = versionsProp.getProperty("minRiruApiVersionCode").toInt()
val minRiruApiVersionName: String = versionsProp.getProperty("minRiruApiVersionName")

val moduleProp =
    Properties().apply { load(file("module.properties").inputStream()) }
val moduleId: String = moduleProp.getProperty("moduleId")
val moduleName: String = moduleProp.getProperty("moduleName")
val moduleAuthor: String = moduleProp.getProperty("moduleAuthor")
val moduleDescription: String = moduleProp.getProperty("moduleDescription")
val moduleVersionName: String = moduleProp.getProperty("moduleVersionName")
val moduleVersionCode: String = moduleProp.getProperty("moduleVersionCode")
val moduleMainClass: String = moduleProp.getProperty("moduleMainClass")
val targetProcessName: String = moduleProp.getProperty("targetProcessName")
val libraryPath: String =
    moduleProp.getProperty("libraryPath").let { if (it.isEmpty()) moduleId else it }
val automaticInstallation: Boolean =
    moduleProp.getProperty("automaticInstallation").let { it == "1" || it == "true" }
val debug: Boolean = moduleProp.getProperty("debug").let { it == "1" || it == "true" }
val moduleDexPath: String = "${if (debug) "data/local/tmp" else "system/framework"}/$moduleId.dex"
val hookwormMainClass = "com/wuyr/hookworm/core/Main"
val targetProcessNameList =
    targetProcessName.split(";").filter { it.isNotBlank() && it.isNotEmpty() }
val processNameArray = targetProcessNameList.joinToString("\", \"", "{\"", "\"}")
val processNameArraySize = targetProcessNameList.size
val platform: OperatingSystem = OperatingSystem.current()

checkProperties()
rootProject.allprojects.forEach { it.buildDir.deleteRecursively() }

android {
    compileSdkVersion(moduleCompileSdkVersion)
    defaultConfig {
        minSdkVersion(moduleMinSdkVersion)
        targetSdkVersion(moduleTargetSdkVersion)
        externalNativeBuild { cmake { addDefinitions() } }
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
            version = cmakeVersion
        }
    }

    libraryVariants.all {
        if (name == "release") {
            sdkDirectory.buildModule()
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:$corektxVersion")
}

fun checkProperties() {
    if (moduleId.isEmpty()) {
        error("moduleId must be fill out!")
    }
    if (moduleMainClass.isEmpty()) {
        error("moduleMainClass must be fill out!")
    }
}

fun ExternalNativeCmakeOptions.addDefinitions() = arguments(
    "-DDEX_PATH=\"$moduleDexPath\"",
    "-DMAIN_CLASS=\"$hookwormMainClass\"",
    "-DPROCESS_NAME_ARRAY=$processNameArray",
    "-DPROCESS_NAME_ARRAY_SIZE=$processNameArraySize",
    "-DMODULE_NAME=riru_$moduleId",
    "-DRIRU_MODULE_API_VERSION=$maxRiruApiVersionCode",
    "-DRIRU_MODULE_VERSION=$moduleVersionCode",
    "-DRIRU_MODULE_VERSION_NAME=\"$moduleVersionName\""
)

var magiskDir = ""

fun File.buildModule() {
    initModuleInfo()
    val task = rootProject.project("app").tasks.find { it.name == "assemble" }
        ?: error("Please dependent on to an app module!")
    val buildDir = task.project.buildDir.also { it.deleteRecursively() }
    task.doLast {
        val zipPath = buildDir.resolve("intermediates/magisk/").apply {
            deleteRecursively()
            magiskDir = absolutePath
            mkdirs()
        }
        copy {
            into(zipPath)
            processResource()
            processScript()
            zipTree(File(buildDir, "outputs/apk/release/app-release-unsigned.apk")
                .also { if (!it.exists()) error("${it.name} not found!") }).let { apkFileTree ->
                processLibs(apkFileTree)
                processDex(apkFileTree)
            }
        }
        zipPath.apply {
            val prop = "name=$moduleName\n" +
                    "version=$moduleVersionName\n" +
                    "versionCode=$moduleVersionCode\n" +
                    "author=$moduleAuthor\n" +
                    "description=$moduleDescription"
            resolve("module.prop").writeText(
                "id=$moduleId\n$prop\ntarget_process_name=$targetProcessName\nlibrary_path=$libraryPath"
            )
            resolve("riru").apply {
                mkdir()
                resolve("module.prop.new").writeText(
                    "$prop\nminApi=$minRiruApiVersionCode"
                )
            }
            resolve("extras.files").run {
                if (debug) {
                    createNewFile()
                } else {
                    writeText("${moduleDexPath}\n")
                }
            }
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

fun initModuleInfo() = (project.tasks.find { it.name == "compileReleaseJavaWithJavac" }
    ?: error("Task 'compileReleaseJavaWithJavac' not found!"))
    .doLast {
        val classPool = ClassPool.getDefault()
        val moduleInfoClassPath =
            buildDir.resolve("intermediates/javac/release/classes").absolutePath
        classPool.insertClassPath(moduleInfoClassPath)
        classPool.getCtClass("com.wuyr.hookworm.core.ModuleInfo").run {
            getDeclaredMethod("getMainClass").name = "getMainClassOld"
            addMethod(
                CtMethod.make("static String getMainClass(){ return \"$moduleMainClass\"; }", this)
            )

            if (debug) {
                getDeclaredMethod("isDebug").name = "isDebugOld"
                addMethod(CtMethod.make("static boolean isDebug(){ return true; }", this))
            }

            val hasSOFile =
                (rootProject.project("app").tasks.find { it.name == "mergeReleaseNativeLibs" }
                    ?: error("Task 'mergeReleaseNativeLibs' not found!")).run {
                    this is com.android.build.gradle.internal.tasks.MergeNativeLibsTask && externalLibNativeLibs.files.any {
                        it.isDirectory && it.list()?.isNotEmpty() == true
                    }
                }
            if (hasSOFile) {
                getDeclaredMethod("hasSOFile").name = "hasSOFileOld"
                addMethod(CtMethod.make("static boolean hasSOFile(){ return true; }", this))

                getDeclaredMethod("getSOPath").name = "getSOPathOld"
                addMethod(
                    CtMethod.make(
                        "static String getSOPath(){ return \"$libraryPath\"; }",
                        this
                    )
                )
            }
            writeFile(moduleInfoClassPath)
            detach()
        }
    }

fun CopySpec.processResource() = from(file("src/main/resource")) {
    exclude("riru.sh", "versions.properties")
}

fun CopySpec.processScript() =
    from(file("src/main/resource/riru.sh")) {
        filter { line ->
            line.replace("%%%RIRU_MODULE_ID%%%", moduleId)
                .replace("%%%RIRU_MIN_API_VERSION%%%", minRiruApiVersionCode.toString())
                .replace("%%%RIRU_MIN_VERSION_NAME%%%", minRiruApiVersionName)
        }
        filter(FixCrLfFilter::class.java)
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
    val ignoreDirs = arrayOf("system", "system_x86")
    val ignoreSuffix = arrayOf("so", "dex")
    fun walk(file: File) {
        if (file.isDirectory) {
            if (!ignoreDirs.contains(file.name)) {
                file.listFiles()?.forEach { if (!ignoreDirs.contains(it.name)) walk(it) }
            }
        } else {
            if (ignoreSuffix.none { file.absolutePath.endsWith(it) }) {
                file.readText().run {
                    if (contains("\r\n")) file.writeText(replace("\r\n", "\n"))
                }
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

fun isDeviceConnected(sdkDirectory: File) =
    exec("${sdkDirectory.adb} devices").count { it == '\n' } == 3

fun exec(command: String) = runCatching {
    Runtime.getRuntime().exec(arrayOf(*platformArgs, command)).run {
        waitFor()
        inputStream.reader().readText().also { destroy() }
    }
}.getOrDefault("")

fun installModuleFailed(sdkDirectory: File, moduleFile: File, zipPath: File) =
    if (debug && (exec("${sdkDirectory.adb} shell ls $moduleDexPath&&echo 1").contains("1"))) {
        if (exec("${sdkDirectory.adb} push $magiskDir/$moduleDexPath /data/local/tmp/&&echo 1".also { it.p() })
                .contains("1")
        ) {
            targetProcessNameList.forEach { processName ->
                exec("adb shell su -c killall $processName".also { it.p() })
            }
            "*********************************".p()
            "Module installation is completed.".p()
            "*********************************".p()
            false
        } else {
            "*********************************".p()
            "Module installation failed! Please try again.".p()
            "*********************************".p()
            true
        }
    } else {
        exec("${sdkDirectory.adb} shell rm /data/local/tmp/$moduleId.dex".also { it.p() })
        if (debug) {
            exec("${sdkDirectory.adb} push $magiskDir/$moduleDexPath /data/local/tmp/").p()
        }
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
    }

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