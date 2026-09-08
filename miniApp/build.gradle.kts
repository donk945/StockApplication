import java.nio.file.Paths

plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        browser {
            webpackTask {
                outputFileName = "miniApp.js"
            }
            commonWebpackConfig {
                output?.library = null
                devtool = null
            }
        }
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open.core-render-web:base:${Version.getKuiklyVersion()}")
                implementation("com.tencent.kuikly-open.core-render-web:miniapp:${Version.getKuiklyVersion()}")
            }
        }
    }
}

val businessPathName = "shared"

fun copyLocalJSBundle(buildSubPath: String) {
    val destDir = Paths.get(project.projectDir.absolutePath, "dist", "business").toFile()
    if (!destDir.exists()) {
        destDir.mkdirs()
    } else {
        destDir.deleteRecursively()
        destDir.mkdirs()
    }
    val sourceDir = Paths.get(
        project.rootDir.absolutePath,
        businessPathName,
        "build/dist/js", buildSubPath
    ).toFile()
    if (sourceDir.exists()) {
        project.copy {
            from(sourceDir) {
                include("nativevue2.js")
            }
            into(destDir)
        }
    }
}

project.afterEvaluate {
    tasks.register("generateWebpackConfig") {
        group = "kuikly"
        doLast {
            val configDir = File(project.projectDir, "webpack.config.d")
            configDir.mkdirs()
            File(configDir, "config.js").writeText("config.target = 'node';\n")
        }
    }
    tasks.named("compileKotlinJs") {
        dependsOn("generateWebpackConfig")
    }
    tasks.register<Copy>("syncRenderProductionToDist") {
        from("$buildDir/kotlin-webpack/js/productionExecutable")
        into("$projectDir/dist/lib")
        include("**/*.js", "**/*.d.ts")
    }
    tasks.named("jsBrowserProductionWebpack") {
        dependsOn("generateWebpackConfig")
        finalizedBy("syncRenderProductionToDist")
    }
    tasks.register("jsMiniAppProductionWebpack") {
        group = "kuikly"
        dependsOn("jsBrowserProductionWebpack")
        doLast {
            copyLocalJSBundle("productionExecutable")
        }
    }
}
