package codogen

import addNivaStd
import frontend.parser.types.ast.Statement
import frontend.typer.Package
import frontend.typer.Project
import frontend.util.addIndentationForEachString
import putInMainKotlinCode
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div

class GeneratorKt(
    val dependencies: MutableList<String> = mutableListOf()
) {
    companion object {
        const val DEPENDENCIES_TEMPLATE = "//%IMPL%"
        const val GRADLE_TEMPLATE = """
plugins {
    kotlin("jvm") version "1.9.20-Beta"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

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
    jvmToolchain(8)
}

application {
    mainClass.set("main.MainKt")
}

"""
        val ALWAYS_IMPORTS = listOf(
            ""
        )
    }
}

fun GeneratorKt.loadPackages(dependenciesList: List<String>) {
    this.dependencies.addAll(dependenciesList)
}

fun GeneratorKt.regenerateGradle(pathToGradle: String) {
    val implementations = dependencies.joinToString("\n") {
        "implementation($it)"
    }
    val newGradle = GeneratorKt.GRADLE_TEMPLATE.replace(GeneratorKt.DEPENDENCIES_TEMPLATE, implementations)

    val gradleFile = File(pathToGradle)
    gradleFile.writeText(newGradle)
//    TODO()
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

fun GeneratorKt.addStdAndPutInMain(ktCode: String, mainPkg: Package) = buildString {
    append("package main\n")
    val code1 = ktCode.addIndentationForEachString(1)
    val mainCode = putInMainKotlinCode(code1)
    val code3 = addNivaStd(mainCode)
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

    currentImports.forEach {
        append("import $it.*\n")
    }
}

fun GeneratorKt.generateKtProject(
    pathToSrcKtFolder: String,
    pathToGradle: String,
    mainProject: Project,
    topLevelStatements: List<Statement>,
) {
    // remove imports of empty packages from other packages
    val notBindedPackages = mainProject.packages.values.filter { !it.isBinding }
    notBindedPackages.forEach { pkg ->
        if (pkg.declarations.isEmpty()) {
            notBindedPackages.forEach { pkg2 ->
                pkg2.currentImports -= pkg.packageName
            }
        }
    }


    val path = File(pathToSrcKtFolder)
    // 1 recreate pathToSrcKtFolder
    deleteAndRecreateKotlinFolder(path)
    // 2 generate Main.kt
    val mainPkg = mainProject.packages["main"]!!
    val mainCode = addStdAndPutInMain(codegenKt(topLevelStatements), mainPkg)
    val mainFile = createCodeKtFile(path, "Main.kt", mainCode)

    // 3 generate every package like folders with code
    generatePackages(path.toPath(), notBindedPackages)

    // 4 regenerate gradle
    regenerateGradle(pathToGradle)
}

fun codegenKt(statements: List<Statement>, indent: Int = 0, pkg: Package? = null): String = buildString {
    if (pkg != null) {

        append("package ${pkg.packageName}\n\n")
        if (pkg.packageName != "core")
            append("import main.*\n")

        append(pkg.generateImports())

    }
    statements.forEach {
        append(GeneratorKt().generateKtStatement(it, indent), "\n")
    }

}

