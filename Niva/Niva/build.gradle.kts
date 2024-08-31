import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("org.graalvm.buildtools.native") version "0.10.1"
    id("maven-publish")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("io.github.irgaly.kfswatch:kfswatch:1.0.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
    testImplementation(kotlin("test"))
}


tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-O4")
            mainClass.set("main.MainKt")
        }
    }
    binaries.all {

        imageName.set("niva")
        buildArgs.add("-O4")

        if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux) {
            buildArgs.add("--static")
        }
        this.runtimeArgs()
        buildArgs.add("--no-fallback")
        buildArgs.add("-march=native")
        buildArgs.add("--initialize-at-build-time")
    }
}

application {
    mainClass = "main.MainKt"
}


val checkAndBuildNativeTask = "checkAndBuildNative"
val buildNativeNiva = "buildNativeNiva"
val buildJvmNiva = "buildJvmNiva"
val javaHomeMayBeHere = "Library/Java/JavaVirtualMachines/graalvm-jdk-22.0.2/Contents/Home"

val checkGraalVMTask = "checkGraalVM"

tasks.register(checkGraalVMTask) {
    doFirst {
        println("Checking GraalVM...")
        val javaVersionOutput = ByteArrayOutputStream()
        exec {
            commandLine("java", "-version")
            standardOutput = javaVersionOutput
            errorOutput = javaVersionOutput
            isIgnoreExitValue = true
        }
        val output = javaVersionOutput.toString()
        if (!output.contains("GraalVM")) {
            throw GradleException("\tCurrent Java is not GraalVM. Please set JAVA_HOME to GraalVM installation.\n" +
                    "\tFor mac and linux: java -XshowSettings:properties -version 2>&1 > /dev/null | grep 'java.home'\n" +
                    "\tFor windows: java -XshowSettings:properties -version 2>&1 | findstr \"java.home\"\n" +
                    "\tOn mac its probably here: $javaHomeMayBeHere\n\n" +
                    "\tThen set it with: \n" +
                    "\t\tJAVA_HOME=value in bash-like shell \n" +
                    "\t\tset JAVA_HOME value in fish shell\n" +
                    "\t\twhere value is  \"java.home = THIS\" from the output of the previous command\n" +
                    "\tand run `./gradlew buildNativeNiva` again")
        } else {
            println("GraalVM is found")
        }
    }
}

tasks.register(checkAndBuildNativeTask) {
    dependsOn(checkGraalVMTask, "nativeCompile")
}

tasks.register(buildJvmNiva) {
    dependsOn("installDist", "publishToMavenLocal")

    fun moveJvm() {
        val userHome = System.getProperty("user.home")
        val nivaJvmBinary = file("${layout.buildDirectory.get()}/install/niva").toPath()
        val targetDir = file("$userHome/.niva/niva").toPath()
        Files.createDirectories(targetDir)
        copyRecursively(nivaJvmBinary, targetDir)

        printNivaWelcome(targetDir, targetDir.toString() + "/bin")
    }

    doLast {
        moveInfroDir()
        moveJvm()

        buildInfroProject()

    }
}

val green = "\u001B[32m"
val purple = "\u001B[35m"
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
                "\tfish: set -U fish_user_paths ${path} \$fish_user_paths\n" +
                "\tbash: echo 'export PATH=\$PATH:$path' >> ~/.bashrc && source ~/.bashrc\n" +
                "\tzsh: echo 'export PATH=\$PATH:$path' >> ~/.zshrc && source ~/.zshrc"
    )
}

fun buildInfroProject() {
    val infroDir = File("${System.getProperty("user.home")}/.niva/infroProject")
    if (infroDir.exists()) {
        val javaVersionOutput = ByteArrayOutputStream()
        exec {
            this.workingDir = infroDir
            commandLine("./gradlew", "build")
            standardOutput = javaVersionOutput
            errorOutput = javaVersionOutput
            isIgnoreExitValue = true
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
        val nivaBinary = file("${layout.buildDirectory.get()}/native/nativeCompile/niva").toPath()
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

    val sourceDir = file("${layout.projectDirectory}/../infroProject")
    val targetDir = file("$userHome/.niva/infroProject")
    val nivaDir = file("$userHome/.niva")



    if (sourceDir.exists()) {
        if (nivaDir.exists()) {
            nivaDir.deleteRecursively()
            println("$nivaDir deleted")
        }

        Files.createDirectories(nivaDir.toPath())
        copyRecursively(sourceDir.toPath(), targetDir.toPath())
        println("$userHome/.niva created")
    } else {
        println("Can't find: $sourceDir, please run $buildNativeNiva from Niva/Niva/Niva dir of the repo")
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

//////OLD
//plugins {
//    id("org.graalvm.buildtools.native") version "0.10.1"
//}
//
//
//
//graalvmNative {
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
//}
