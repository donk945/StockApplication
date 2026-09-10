import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    kotlin("multiplatform")
}

val hostBuiltinKeyDir = File(layout.buildDirectory.get().asFile, "generated/builtinApiKey/kotlin")
DeepSeekKeyGen.writeKotlin(
    hostBuiltinKeyDir,
    "com.hfad.stockapplication.h5",
    "HostBuiltinKey",
    DeepSeekKeyGen.readKey(rootProject.projectDir),
)

kotlin {
    js(IR) {
        browser {
            webpackTask {
                outputFileName = "h5App.js"
            }
            commonWebpackConfig {
                output?.library = null
            }
        }
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            kotlin.srcDir(hostBuiltinKeyDir)
            dependencies {
                implementation("com.tencent.kuikly-open.core-render-web:base:${Version.getKuiklyVersion()}")
                implementation("com.tencent.kuikly-open.core-render-web:h5:${Version.getKuiklyVersion()}")
            }
        }
    }
}

val businessPathName = "shared"

fun copyLocalJSBundle(buildSubPath: String) {
    val destDir = Paths.get(project.buildDir.absolutePath, buildSubPath, "page").toFile()
    if (!destDir.exists()) {
        destDir.mkdirs()
    } else {
        destDir.deleteRecursively()
    }
    val zipFile = Paths.get(
        project.rootDir.absolutePath,
        businessPathName,
        "build", "outputs", "kuikly", "js", "release", "local", "nativevue2.zip"
    ).toFile()
    val zipDir = Paths.get(project.buildDir.absolutePath, buildSubPath, "kotlin2js").toFile()
    if (!zipDir.exists()) {
        zipDir.mkdirs()
    } else {
        zipDir.deleteRecursively()
    }
    if (zipFile.exists()) {
        project.copy {
            from(project.zipTree(zipFile))
            into(zipDir)
        }
        project.copy {
            from(zipDir) {
                include("nativevue2.js")
            }
            into(destDir)
        }
    }
    zipDir.deleteRecursively()
}

fun generateLocalHtml(buildSubPath: String) {
    val filePath = Paths.get(project.buildDir.absolutePath, buildSubPath, "index.html")
    if (Files.exists(filePath)) {
        val fileContent = Files.readString(filePath)
        val updatedContent = fileContent.replace("http://127.0.0.1:8083/nativevue2.js", "page/nativevue2.js")
        Files.writeString(filePath, updatedContent, StandardCharsets.UTF_8)
    }
}

project.afterEvaluate {
    tasks.register("publishLocalJSBundle") {
        group = "kuikly"
        dependsOn("jsBrowserDistribution")
        doFirst {
            copyLocalJSBundle("dist/js/productionExecutable")
        }
        doLast {
            generateLocalHtml("dist/js/productionExecutable")
        }
    }
}
