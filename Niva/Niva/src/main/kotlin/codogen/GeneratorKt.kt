@file:Suppress("UnusedReceiverParameter")

package main.codogen

import main.utils.addStd
import main.utils.putInMainKotlinCode
import frontend.resolver.CompilationTarget
import frontend.resolver.MAIN_PKG_NAME
import frontend.resolver.Package
import frontend.resolver.Project
import main.frontend.parser.types.ast.Statement
import main.utils.appendnl
import main.utils.targetToRunCommand
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div

// Can generate source files
class GeneratorKt(
    val dependencies: MutableList<String> = mutableListOf()
) {
    companion object {
        const val DEPENDENCIES_TEMPLATE = "//%IMPL%"
        const val TARGET = "%TARGET%"
        const val GRADLE_TEMPLATE = """
plugins {
    kotlin("jvm") version "2.0.0-Beta4"
    application
}

group = "org.example"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    //%IMPL%
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("mainNiva.MainKt")
}

"""

        const val AMPER_TEMPLATE = """
product: %TARGET%/app

dependencies:
//%IMPL%

settings:
  kotlin:
    languageVersion: 2.0
  jvm:
    target: 21
  java:
    source: 21

"""
    }

    fun GRADLE_FAT_JAR_TEMPLATE(jarName: String) = """kotlin {
    jvm {
        compilations {
            val main = getByName("main")
            tasks {
                register<Jar>("fatJar") {
                    group = "application"
                    manifest {
                        attributes["Main-Class"] = "mainNiva.MainKt"
                    }
                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    archiveBaseName.set("$jarName")
                    archiveVersion.set("")

                    from(main.output.classesDirs)
                    dependsOn(configurations.runtimeClasspath)
                    from({
                        configurations.runtimeClasspath.get()
                            .filter { it.name.endsWith("jar") }
                            .map { zipTree(it) }
                    })
                    with(jar.get() as CopySpec)
                }
            }
        }
    }
}
"""

    fun GRADLE_FOR_AMPER_TEMPLATE(workingDir: String, runCommandName: String) =
        "getTasksByName(\"$runCommandName\", true).first().setProperty(\"workingDir\", \"$workingDir\")\n"

    fun GRADLE_PANAMA() = """
repositories {
    maven(url = "https://jitpack.io")
}


tasks.withType(JavaCompile::class.java) {
    options.compilerArgs = listOf("--enable-preview")
}

tasks.withType(JavaExec::class.java) {
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=/usr/lib64:/lib64:/lib:/usr/lib:/lib/x86_64-linux-gnu"
    )
}
"""
}


fun GeneratorKt.addToGradleDependencies(dependenciesList: List<String>) {
    this.dependencies.addAll(dependenciesList)
}


fun GeneratorKt.regenerateGradleForAmper(
    pathToGradle: String,
    runCommandName: String,
    compilationTarget: CompilationTarget,
    jarName: String
) {
    val newGradle = buildString {
        if (compilationTarget == CompilationTarget.jvm) {
            append(GRADLE_FAT_JAR_TEMPLATE(jarName))
        }
        append(GRADLE_FOR_AMPER_TEMPLATE(File(".").absolutePath, runCommandName = runCommandName))
        append(GRADLE_PANAMA())
    }


    val gradleFile = File(pathToGradle)
    gradleFile.writeText(newGradle)
}

@Suppress("unused")
fun GeneratorKt.regenerateGradleOld(pathToGradle: String) {
    val implementations = dependencies.joinToString("\n") {
        "implementation($it)"
    }
    val newGradle = GeneratorKt.GRADLE_TEMPLATE.replace(GeneratorKt.DEPENDENCIES_TEMPLATE, implementations)

    val gradleFile = File(pathToGradle)
    gradleFile.writeText(newGradle)
}

fun GeneratorKt.regenerateAmper(pathToAmper: String, target: CompilationTarget) {
    val implementations = dependencies.joinToString("\n") {
        "  - $it"
    }
    val newGradle = GeneratorKt.AMPER_TEMPLATE
        .replace(GeneratorKt.DEPENDENCIES_TEMPLATE, implementations)
        .replace(GeneratorKt.TARGET, target.name)

    val gradleFile = File(pathToAmper)
    gradleFile.writeText(newGradle)
}

fun GeneratorKt.deleteAndRecreateKotlinFolder(path: File) {
    if (path.deleteRecursively()) {
        path.mkdir()
    } else {
        throw Error("Failed to delete: ${path.absolutePath}")
    }
}

fun GeneratorKt.createCodeKtFile(path: File, fileName: String, code: String): File {
    val baseDir = path.toPath().resolve(fileName).toFile()
    if (baseDir.exists()) {
        println("File already exists: ${baseDir.absolutePath}")
    } else {
        if (baseDir.createNewFile()) {
            baseDir.writeText(code)
        } else {
            throw Error("Failed to create file: ${baseDir.absolutePath}")
        }
    }
    return baseDir
}

fun GeneratorKt.addStdAndPutInMain(
    ktCode: String,
    mainPkg: Package,
    compilationTarget: CompilationTarget,
    pathToInfroProject: String
) =
    buildString {
        append("package ${mainPkg.packageName}\n")
        val code1 = ktCode//.addIndentationForEachString(1) // do not add indent to main because of """ will look strange
        val mainCode = putInMainKotlinCode(code1, compilationTarget, pathToInfroProject)
        val code3 = addStd(mainCode, compilationTarget)
        append(mainPkg.generateImports(), "\n")
        append(code3, "\n")
    }


fun GeneratorKt.generatePackages(pathToSource: Path, notBindedPackages: List<Package>) {
//    val builder = StringBuilder()
    notBindedPackages.forEach { v ->
        val code = codegenKt(v.declarations, pkg = v)
        // generate folder for package
        val folderForPackage = (pathToSource / v.packageName).toFile()
        folderForPackage.mkdir()
        // generate file with code
        createCodeKtFile(folderForPackage, v.packageName + ".kt", code)
    }

}

fun Package.generateImports() = buildString {
    imports.forEach {
        appendnl("import $it.*")
    }
    concreteImports.forEach {
        appendnl("import $it")
    }
}

fun GeneratorKt.generateKtProject(
    pathToDotNivaFolder: String,
    pathToGradle: String,
    pathToAmper: String,
    mainProject: Project,
    topLevelStatements: List<Statement>,
    compilationTarget: CompilationTarget,
    mainFileName: String, // using for binaryName
    pathToInfroProject: String
) {
    val notBindPackages = mutableSetOf<Package>()
    val bindPackagesWithNeededImport = mutableSetOf<String>()
    val pkgNameToNeededImports = mutableMapOf<String, Set<String>>()

    mainProject.packages.values.forEach {
        if (it.isBinding ) {
            if (it.neededImports.isNotEmpty()) {
                bindPackagesWithNeededImport.add(it.packageName)
                pkgNameToNeededImports[it.packageName] = it.neededImports
            }
        } else
            notBindPackages.add(it)
    }
    notBindPackages.forEach { pkg ->
        // remove imports of empty packages from other packages
        if (pkg.declarations.isEmpty() && pkg.packageName != MAIN_PKG_NAME) {
            notBindPackages.forEach { pkg2 ->
                pkg2.imports -= pkg.packageName
            }
        }

        // if pkg1 imports some other pkg2 with needed imports, then add this imports to pkg1
        val pkgsWithNeededImportsInCurrentImports = pkg.imports.intersect(bindPackagesWithNeededImport)
        pkgsWithNeededImportsInCurrentImports.forEach {
            pkg.concreteImports.addAll(pkgNameToNeededImports[it]!!)
        }
    }


    val path = File(pathToDotNivaFolder)
    // 1 recreate pathToSrcKtFolder
    deleteAndRecreateKotlinFolder(path)
    // 2 generate Main.kt
    val mainPkg = mainProject.packages[MAIN_PKG_NAME]!!


    val mainCode = addStdAndPutInMain(codegenKt(topLevelStatements), mainPkg, compilationTarget, pathToInfroProject)
    createCodeKtFile(path, "Main.kt", mainCode)

    // 3 generate every package like folders with code
    generatePackages(path.toPath(), notBindPackages.toList())


    // 4 regenerate amper
    regenerateAmper(pathToAmper, compilationTarget)

    // 4 regenerate gradle
    regenerateGradleForAmper(pathToGradle, runCommandName = targetToRunCommand(compilationTarget), compilationTarget,
        mainFileName)
}

fun codegenKt(statements: List<Statement>, indent: Int = 0, pkg: Package? = null): String = buildString {
    if (pkg != null) {

        append("package ${pkg.packageName}\n\n")
        if (pkg.packageName != "core")
            append("import $MAIN_PKG_NAME.*\n")

        append(pkg.generateImports())

    }
    val generator = GeneratorKt()
    statements.forEach {
        append(generator.generateKtStatement(it, indent), "\n")
    }

}

