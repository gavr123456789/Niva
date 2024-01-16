@file:Suppress("UnusedReceiverParameter")

package codogen

import main.utils.addStd
import main.utils.putInMainKotlinCode
import frontend.parser.types.ast.Statement
import frontend.resolver.CompilationTarget
import frontend.resolver.MAIN_PKG_NAME
import frontend.resolver.Package
import frontend.resolver.Project
import frontend.util.addIndentationForEachString
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
    kotlin("jvm") version "1.9.20-Beta"
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
"""
    }

    fun GRADLE_FOR_AMPER_TEMPLATE(workingDir: String, runCommandName: String) =
        "getTasksByName(\"$runCommandName\", true).first().setProperty(\"workingDir\", \"$workingDir\")\n"
}

fun GeneratorKt.addToGradleDependencies(dependenciesList: List<String>) {
    this.dependencies.addAll(dependenciesList)
}


fun GeneratorKt.regenerateGradleForAmper(pathToGradle: String, runCommandName: String) {
    val newGradle = GRADLE_FOR_AMPER_TEMPLATE(File(".").absolutePath, runCommandName = runCommandName)
    val gradleFile = File(pathToGradle)
    gradleFile.writeText(newGradle)
}


fun GeneratorKt.regenerateGradle(pathToGradle: String) {
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

fun GeneratorKt.addStdAndPutInMain(ktCode: String, mainPkg: Package, compilationTarget: CompilationTarget) =
    buildString {
        append("package ${mainPkg.packageName}\n")
        val code1 = ktCode.addIndentationForEachString(1)
        val mainCode = putInMainKotlinCode(code1)
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
    compilationTarget: CompilationTarget
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


    val mainCode = addStdAndPutInMain(codegenKt(topLevelStatements), mainPkg, compilationTarget)
    createCodeKtFile(path, "Main.kt", mainCode)

    // 3 generate every package like folders with code
    generatePackages(path.toPath(), notBindPackages.toList())


    // 4 regenerate amper
    regenerateAmper(pathToAmper, compilationTarget)

    // 4 regenerate gradle
    regenerateGradleForAmper(pathToGradle, runCommandName = targetToRunCommand(compilationTarget))
}

fun codegenKt(statements: List<Statement>, indent: Int = 0, pkg: Package? = null): String = buildString {
    if (pkg != null) {

        append("package ${pkg.packageName}\n\n")
        if (pkg.packageName != "core")
            append("import $MAIN_PKG_NAME.*\n")

        append(pkg.generateImports())

    }
    statements.forEach {
        append(GeneratorKt().generateKtStatement(it, indent), "\n")
    }

}

