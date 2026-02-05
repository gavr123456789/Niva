// import org.jetbrains.kotlin.cli.common.isWindows
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    kotlin("jvm") version "2.3.0"
    application
    id("org.graalvm.buildtools.native") version "0.11.1"
    id("maven-publish")
    kotlin("plugin.serialization") version "2.3.0"
}

group = "org.example"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.github.irgaly.kfswatch:kfswatch:1.0.0")
    //    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.test { useJUnitPlatform() }

val compactObjectJvmArgs =
    listOf(
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+UseCompactObjectHeaders",
//        "-XX:+UseShenandoahGC", //  jdk.graal.compiler.debug.GraalError: Shenandoah garbage collector is not supported by Graal
//        "-XX:ShenandoahGCMode=generational",
    )

val nativeOptimizationArg = when {
    project.hasProperty("nativeDebug") -> "-Ob"
    project.hasProperty("nativeRelease") -> "-O3"
    else -> "-O3"
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(compactObjectJvmArgs)
}

tasks.named<JavaExec>("run") {
    // always execute run even if nothing changed
    outputs.upToDateWhen { false }
}

tasks.withType<Test>().configureEach {
    jvmArgs(compactObjectJvmArgs)
}

// set emitting java bytecode version for current JVM
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        val currentJavaVersion = "25"//JavaVersion.current().majorVersion
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(currentJavaVersion))
    }
}


val zipInfroProject = tasks.register<Zip>("zipInfroProject") {
    from(layout.projectDirectory.dir("src/main/resources/infroProject"))
    archiveFileName.set("infroProject.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated-resources"))
}

tasks.processResources {
    from(zipInfroProject)
}

//kotlin { jvmToolchain(21) }
// tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
//    compilerOptions.freeCompilerArgs.addAll(listOf("-Xcontext-receivers"))
// }



graalvmNative {
    binaries {
        named("main") {
            mainClass.set("main.MainKt")
        }
    }
    binaries.all {
        imageName.set("niva")
        buildArgs.add(nativeOptimizationArg)
        buildArgs.add("-H:IncludeResources=infroProject\\.zip")

        // temp solution
        //        if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
//                    buildArgs.add("--static")
//                    buildArgs.add("--libc=musl")
        //        }
        this.runtimeArgs()
        buildArgs.add("--no-fallback")
        buildArgs.add("-march=native")
        buildArgs.add("--initialize-at-build-time")
    }
}

application {
    mainClass = "main.MainKt"
    applicationDefaultJvmArgs = compactObjectJvmArgs
}

val checkAndBuildNativeTask = "checkAndBuildNative"
val buildNativeNiva = "buildNativeNiva"
val buildJvmNiva = "buildJvmNiva"
val javaHomeMayBeHere = "Library/Java/JavaVirtualMachines/graalvm-jdk-22.0.2/Contents/Home"

val checkGraalVMTask = "checkGraalVM"

tasks.register<Exec>(checkGraalVMTask) {
    // Use Exec task instead of deprecated Project.exec
    val javaVersionOutput = ByteArrayOutputStream()
    commandLine("java", "--version")
    isIgnoreExitValue = true
    standardOutput = javaVersionOutput
    errorOutput = javaVersionOutput

    doFirst {
        println("Checking GraalVM...")
    }

    val output = javaVersionOutput.toString()
    println("......")
    println(output)
    doLast {
        if (!output.contains("GraalVM")) {
//            throw GradleException(
//                "\tCurrent Java is not GraalVM. Please set JAVA_HOME to GraalVM installation.\n" +
//                    "\tFor mac and linux: java -XshowSettings:properties -version 2>&1 > /dev/null | grep 'java.home'\n" +
//                    "\tFor windows: java -XshowSettings:properties -version 2>&1 | findstr \"java.home\"\n" +
//                    "\tOn mac its probably here: $javaHomeMayBeHere\n" +
//                    "\tThen set it with: \n" +
//                    "\t\tJAVA_HOME=value in bash-like shell \n" +
//                    "\t\tset JAVA_HOME value in fish shell\n" +
//                    "\t\twhere value is  \"java.home = THIS\" from the output of the previous command\n" +
//                    "\tand run `./gradlew buildNativeNiva` again"
//            )
        } else {
            println("GraalVM is found")
        }
    }
}

tasks.register(checkAndBuildNativeTask) { dependsOn(checkGraalVMTask, "nativeCompile") }

tasks.register(buildJvmNiva) {
    dependsOn("installDist", "publishToMavenLocal")

    // Precompute all values during configuration to avoid accessing Task.project at execution time
    val userHome = System.getProperty("user.home")
    val projectDirPath: Path = layout.projectDirectory.asFile.toPath()
    val sourceInfro: Path = projectDirPath.resolve("src/main/resources/infroProject").normalize()
    val nivaHomeDir: Path = File("$userHome/.niva").toPath()
    val nivaJvmBinary: Path = layout.buildDirectory.dir("install/niva").get().asFile.toPath()
    val targetInstallDir: Path = nivaHomeDir.resolve("niva")
    val targetInfro: Path = nivaHomeDir.resolve("infroProject")

    doLast {
        // Local helper to copy directories recursively without capturing script references
        fun copyRecursivelyLocal(source: Path, target: Path) {
            Files.walk(source).forEach { path ->
                val targetPath = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath)
                    }
                } else {
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        // 1) Prepare ~/.niva by copying infroProject
        if (Files.exists(sourceInfro)) {
            if (Files.exists(nivaHomeDir)) {
                // delete existing ~/.niva
                nivaHomeDir.toFile().deleteRecursively()
                println("$nivaHomeDir deleted")
            }
            Files.createDirectories(nivaHomeDir)
            copyRecursivelyLocal(sourceInfro, targetInfro)
            if (OperatingSystem.current() != OperatingSystem.WINDOWS) {
                targetInfro.resolve("gradlew").toFile().setExecutable(true)
            }
            println("$userHome/.niva created")
        } else {
            println("Can't find: $sourceInfro, please run buildNativeNiva from Niva/Niva/Niva dir of the repo")
        }

        // 2) Install JVM distribution to ~/.niva/niva
        Files.createDirectories(targetInstallDir)
        copyRecursivelyLocal(nivaJvmBinary, targetInstallDir)

        // 3) Build infro project using its Gradle wrapper
        val infroDirFile = targetInfro.toFile()
        if (infroDirFile.exists()) {
            val output = ByteArrayOutputStream()
            val isWindows = OperatingSystem.current() == OperatingSystem.WINDOWS
            val cmd = if (isWindows) listOf("./gradlew.bat", "build") else listOf("./gradlew", "build")
            try {
                val pb = ProcessBuilder(cmd)
                    .directory(infroDirFile)
                    .redirectErrorStream(true)
                val process = pb.start()
                process.inputStream.copyTo(output)
                val exit = process.waitFor()
                if (exit != 0) {
                    println("Infro project build failed with exit code $exit\n$output")
                }
            } catch (e: Exception) {
                println("Failed to run wrapper in infroProject: ${e.message}")
            }
        }

        // 4) Print welcome and PATH hints
        val green = "\u001B[32m"
        val purple = "\u001B[35m"
        val pathHint = targetInstallDir.resolve("bin").toString()
        println(
            ("""
            $green
            niva binary has been installed in $targetInstallDir, you can add it to PATH

            Try to compile file main.niva with `"Hello niva" echo` with `niva main.niva`
            First compilation will take time, but others are instant
            Read about niva here: https://gavr123456789.github.io/niva-site/reference.html
            Check examples from examples folder(in repo)

            $purple
            Adding to PATH:

        """.trimIndent()) +
                "\tfish: set -U fish_user_paths $pathHint $" + "fish_user_paths\n" +
                "\tbash: echo 'export PATH=$" + "PATH:${pathHint}' >> ~/.bashrc && source ~/.bashrc\n" +
                "\tzsh: echo 'export PATH=$" + "PATH:${pathHint}' >> ~/.zshrc && source ~/.zshrc\n" +
                "\twindows: setx PATH \"%PATH%;${pathHint}\""
        )
    }
}

val green = "\u001B[32m"
val purple = "\u001B[35m"

val sourceDir = file("${layout.projectDirectory}/src/main/resources/infroProject")
val nivaBinary: Path = file("${layout.buildDirectory.get()}/native/nativeCompile/niva").toPath()
val isWindows = OperatingSystem.current() == OperatingSystem.WINDOWS


fun printNivaWelcome(targetDir: Path, path: String) {
    println(
            """
        $green
        niva binary has been installed in $targetDir, you can add it to PATH

        Try to compile file main.niva with "Hello niva" echo with `niva main.niva`
        First compilation will take time, but others are instant
        Read about niva here: https://gavr123456789.github.io/niva-site/reference.html
        Check examples from examples folder(in repo)

        $purple
        Adding to PATH:

    """.trimIndent() +
                    $$"\tfish: set -U fish_user_paths $$path $fish_user_paths\n" +
                    $$"\tbash: echo 'export PATH=$PATH:$$path' >> ~/.bashrc && source ~/.bashrc\n" +
                    $$"\tzsh: echo 'export PATH=$PATH:$$path' >> ~/.zshrc && source ~/.zshrc" +
                    "\twindows: setx PATH \"%PATH%;$path\""
    )
}

fun buildInfroProject() {
    println("Building test project, first time it can take up to 2 min, thanks to Gradle :(")
    val infroDir = File("${System.getProperty("user.home")}/.niva/infroProject")
    if (infroDir.exists()) {
        val output = ByteArrayOutputStream()
        val cmd = if (isWindows) listOf("./gradlew.bat", "build") else listOf("./gradlew", "build")
        try {
            val pb = ProcessBuilder(cmd)
                .directory(infroDir)
                .redirectErrorStream(true)
            val process = pb.start()
            process.inputStream.copyTo(output)
            val exit = process.waitFor()
            if (exit != 0) {
                println("Infro project build failed with exit code $exit\n$output")
            }
        } catch (e: Exception) {
            println("Failed to run wrapper in infroProject: ${e.message}")
        }
    }
}

tasks.register(buildNativeNiva) {
    dependsOn("publishToMavenLocal", checkAndBuildNativeTask)

    fun moveBinary() {
        if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
            println("You are using linux! binary will be static")
        } else {
            println("No linux = no static binary for u")
        }

        val userHome = System.getProperty("user.home")
        val targetDir = file("$userHome/.niva/bin/niva").toPath()
        Files.createDirectories(targetDir)
        copyRecursively(nivaBinary, targetDir)

        buildInfroProject()

        printNivaWelcome(targetDir, targetDir.toString())
    }


    doLast {
        moveInfroDir()
        moveBinary()
    }
}

fun moveInfroDir() {
    val userHome = System.getProperty("user.home")

    val targetDir = file("$userHome/.niva/infroProject")
    val nivaDir = file("$userHome/.niva")

    if (sourceDir.exists()) {
        if (nivaDir.exists()) {
            nivaDir.deleteRecursively()
            println("$nivaDir deleted")
        }

        Files.createDirectories(nivaDir.toPath())
        copyRecursively(sourceDir.toPath(), targetDir.toPath())
        if (!isWindows) {
            targetDir.toPath().resolve("gradlew").toFile().setExecutable(true)
        }
        println("$userHome/.niva created")
    } else {
        println(
                "Can't find: $sourceDir, please run $buildNativeNiva from Niva/Niva/Niva dir of the repo"
        )
    }
}

/////////

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.gavr123456789"
            artifactId = "niva"
            version = "0.1"

            from(components["java"])
        }
    }
}

fun copyRecursively(source: Path, target: Path) {
    Files.walk(source).forEach { path ->
        val targetPath = target.resolve(source.relativize(path).toString())
        if (Files.isDirectory(path)) {
            if (!Files.exists(targetPath)) {
                Files.createDirectory(targetPath)
            }
        } else {
            Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

////// OLD
// plugins {
//    id("org.graalvm.buildtools.native") version "0.10.1"
// }
//
//
//
// graalvmNative {
//    binaries {
//        named("main") {
//            mainClass.set("main.MainKt")
//        }
//    }
//    binaries.all {
//
//        imageName.set("niva")
//        buildArgs.add("-O4")
//        buildArgs.add("--static")
//        buildArgs.add("--no-fallback")
//        buildArgs.add("-march=native")
//        buildArgs.add("--initialize-at-build-time")
//    }
// }
