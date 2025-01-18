@file:Suppress("UnusedReceiverParameter")

package main.codogen

import frontend.resolver.CompilationTarget
import frontend.resolver.MAIN_PKG_NAME
import frontend.resolver.Package
import frontend.resolver.Project
import frontend.resolver.Resolver
import main.codogen.GeneratorKt.Companion.GRADLE_IMPORTS
import main.frontend.parser.types.ast.Declaration
import main.frontend.parser.types.ast.InternalTypes
import main.frontend.parser.types.ast.MessageDeclaration
import main.frontend.parser.types.ast.MessageDeclarationUnary
import main.frontend.parser.types.ast.Statement
import main.utils.MILL_BUILD
import main.utils.addStd
import main.utils.appendnl
import main.utils.generateMillProjectTemplateIfNotExist
import main.utils.putInMainKotlinCode
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div



// Can generate source files
class GeneratorKt(
    val dependencies: MutableList<String> = mutableListOf(),
//    val plugins: MutableList<String> = mutableListOf()
) {
    companion object {
        const val DEPENDENCIES_TEMPLATE = "//%IMPL%"
        const val TARGET = "%TARGET%"
        const val GRADLE_IMPORTS = "import org.gradle.api.tasks.testing.logging.TestExceptionFormat\n" +
                "import org.gradle.api.tasks.testing.logging.TestLogEvent\n\n"
//        const val GRADLE_TEMPLATE = """
//plugins {
//    kotlin("jvm") version "2.0.0-Beta4"
//    application
//}
//
//group = "org.example"
//version = "0.0.1"
//
//repositories {
//    mavenCentral()
//}
//
//dependencies {
//    testImplementation(kotlin("test"))
//}
//
//dependencies {
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
//    //%IMPL%
//}
//
//tasks.test {
//    useJUnitPlatform()
//}
//
//kotlin {
//    jvmToolchain(21)
//}
//
//application {
//    mainClass.set("mainNiva.MainKt")
//}
//
//"""

        const val AMPER_TEMPLATE = """
product: %TARGET%/app

dependencies:
//%IMPL%

test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:2.0.21

settings:
  compose: enabled
  kotlin:
    serialization:
      format: json
      
  jvm:
    release: 21

"""
    }

    fun GRADLE_FAT_JAR_TEMPLATE(jarName: String) = """
kotlin {
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
    fun GRADLE_OPTIONS(workingDir: String) = """
repositories {
    maven(url = "https://jitpack.io")
}


tasks.withType(JavaCompile::class.java) {
    options.compilerArgs = listOf("--enable-preview")
}

tasks.withType(JavaExec::class.java) {
    jvmArgs(
        "-Dsun.java2d.uiScale=2.0",
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=/usr/lib64:/lib64:/lib:/usr/lib:/lib/x86_64-linux-gnu"
    )
    setProperty("workingDir", ""${'"'}$workingDir""${'"'})
    standardInput = System.`in`
}

allprojects {
    tasks.withType(Test::class.java) {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true

            showExceptions = true
            showStackTraces = false
            showStandardStreams = true
            events = setOf(TestLogEvent.PASSED , TestLogEvent.SKIPPED, TestLogEvent.FAILED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR)
        }
    }
}

"""
}


fun GeneratorKt.addToGradleDependencies(dependenciesList: List<String>) {
    this.dependencies.addAll(dependenciesList)
}


fun GeneratorKt.regenerateGradleForAmper(
    pathToGradle: String,
    compilationTarget: CompilationTarget,
    jarName: String
) {
    val newGradle = buildString {
        append(GRADLE_IMPORTS)
        if (compilationTarget == CompilationTarget.jvm || compilationTarget == CompilationTarget.jvmCompose) {
            append(GRADLE_FAT_JAR_TEMPLATE(jarName))
        }
//        append(GRADLE_FOR_AMPER_TEMPLATE(File(".").absolutePath, runCommandName = runCommandName))
        append(GRADLE_OPTIONS(File(".").absolutePath))
    }


    val gradleFile = File(pathToGradle)
    gradleFile.writeText(newGradle)
}

//@Suppress("unused")
//fun GeneratorKt.regenerateGradleOld(pathToGradle: String) {
//    val implementations = dependencies.joinToString("\n") {
//        "implementation($it)"
//    }
//    val newGradle = GeneratorKt.GRADLE_TEMPLATE.replace(GeneratorKt.DEPENDENCIES_TEMPLATE, implementations)
//
//    val gradleFile = File(pathToGradle)
//    gradleFile.writeText(newGradle)
//}

fun GeneratorKt.regenerateMill(pathToMill: String) {
    val deps = buildString {
        if (dependencies.isNotEmpty()) {
            appendLine("def ivyDeps = Agg(")
            appendLine(
                dependencies.joinToString(",\n") {
                    "    ivy$it"
                }
            )
            appendLine(")")
        }
    }
    val newMillBuildFile = MILL_BUILD
        .replace(GeneratorKt.DEPENDENCIES_TEMPLATE, deps)
    File(pathToMill).writeText(newMillBuildFile)
}
fun GeneratorKt.regenerateAmper(pathToAmper: String, target: CompilationTarget) {
    val deps = dependencies.joinToString("\n") {
        "  - ${it.removeSurrounding("\"")}" // replace `- "qqqq"` with `- qqqq`
    }
    val newGradle = GeneratorKt.AMPER_TEMPLATE
        .replace(GeneratorKt.DEPENDENCIES_TEMPLATE, deps)
        .replace(GeneratorKt.TARGET, target.targetName).let {
            if (target != CompilationTarget.jvmCompose)
                it.replace("compose: enabled", "")
            else
                it
        }

    File(pathToAmper).writeText(newGradle)
}

fun GeneratorKt.deleteAndRecreateFolder(path: File) {
    if (path.deleteRecursively()) {
        path.mkdir()
    } else {
        throw Error("Failed to delete: ${path.absolutePath}")
    }
}

fun GeneratorKt.createCodeKtFile(path: File, fileName: String, code: String): File {
    fun createFileInDirectory(file: File): Boolean {
        val parentDir = file.parentFile

        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        return file.createNewFile()
    }

    val pathToNivaFile = path.toPath().resolve(fileName).toFile()
    if (pathToNivaFile.exists()) {
        println("File already exists: ${pathToNivaFile.absolutePath}")
    } else {
        if (createFileInDirectory(pathToNivaFile)) {
            pathToNivaFile.writeText(code)
        } else {
            throw Error("Failed to create file: ${pathToNivaFile.absolutePath}")
        }
    }
    return pathToNivaFile
}

fun GeneratorKt.addStdAndPutInMain(
    ktCode: String,
    mainPkg: Package,
    compilationTarget: CompilationTarget,
    pathToInfroProject: String
) =
    buildString {
        appendLine("@file:Suppress(\"NOTHING_TO_INLINE\")")
        append("package ${mainPkg.packageName}\n")
        val code1 =
            ktCode//.addIndentationForEachString(1) // do not add indent to main because of """ will look strange
        val mainCode = putInMainKotlinCode(code1, compilationTarget, pathToInfroProject)
        val code3 = addStd(mainCode, compilationTarget)
        append(mainPkg.generateImports(), "\n")
        append(code3, "\n")
    }


fun GeneratorKt.generatePackages(pathToSource: Path, notBindedPackages: List<Package>, isTestsRun: Boolean) {
//    val builder = StringBuilder()
    val src = pathToSource / "src"

    val pkgs1 = notBindedPackages.filter { it.declarations.isNotEmpty() }

    val testPackages = mutableMapOf<String, Package>()


    val addIfAbsent = { pkgName: String, statement: Declaration, imports: MutableSet<String> ->
        val q = testPackages[pkgName]
        if (q != null) {
            q.declarations.add(statement)
        } else {
            testPackages[pkgName] = Package(pkgName , declarations = mutableListOf(statement), imports = imports) // + "Test"
        }
    }

    // move all declarations for Test to testDeclarations
    val testType = Resolver.defaultTypes[InternalTypes.Test]!!
    pkgs1.forEach {
        val iter = it.declarations.iterator()
        while (iter.hasNext()) {
            val q = iter.next()
            if (q is MessageDeclarationUnary && q.forType == testType) {
                if (isTestsRun)
                    addIfAbsent(it.packageName, q, it.imports)
                iter.remove()
            }
        }
    }


    val generate = { v: Package ->
        val code = codegenKt(v.declarations, pkg = v)
        // generate folder for package
        val folderForPackage = (src / v.packageName).toFile()
        folderForPackage.mkdir()
        // generate file with code
        createCodeKtFile(folderForPackage, v.packageName + ".kt", code)
    }


    pkgs1.forEach { v ->
        generate(v)
    }

    if (isTestsRun) {
        val tests = pathToSource / "test"

        val generateTest = { v: Package ->
            val code = codegenKt(v.declarations, pkg = v, forTest = true)
            // generate folder for package
            val folderForPackage = (tests / v.packageName).toFile()
            folderForPackage.mkdir()
            // generate file with code
            val codeInsideTestClass = buildString {
                append(code)
            }
            createCodeKtFile(folderForPackage, v.packageName + ".kt", codeInsideTestClass)
        }

        testPackages.values.forEach { v ->
            generateTest(v)
        }
    }




}

fun Package.generateImports() = buildString {
    val collectAll = (imports + importsFromUse).filter { it != this@generateImports.packageName}


    collectAll.forEach {
        appendnl("import $it.*")
    }
    concreteImports.forEach {
        appendnl("import $it")
    }

}


enum class BuildSystem {
    Gradle, Amper, Mill
}

fun GeneratorKt.generateKtProject(
    pathToDotNivaFolder: String,
    pathToGradle: String,
    pathToAmper: String,
    pathToMill: String,
    mainProject: Project,
    topLevelStatements: List<Statement>,
    compilationTarget: CompilationTarget,
    mainFileName: String, // using for binaryName
    pathToInfroProject: String,
    isTestsRun: Boolean = false,
    buildSystem: BuildSystem
) {
    val notBindPackages = mutableSetOf<Package>()
    val bindPackagesWithNeededImport = mutableSetOf<String>()
    val pkgNameToNeededImports = mutableMapOf<String, Set<String>>()

    val addImpordsToEachPackage = {
        mainProject.packages.values.forEach {
            if (it.isBinding) {
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
    }
    addImpordsToEachPackage()

    fun generateProj(pathToInfroProj: Path, generateBuildFile: () -> Unit) {
        val pathToDotNivaFolder = pathToInfroProj
        val pathToSrc = File("$pathToDotNivaFolder/src")
        val pathToTests = File("$pathToDotNivaFolder/test")
        // 1 recreate pathToSrcKtFolder
        deleteAndRecreateFolder(pathToSrc)
        deleteAndRecreateFolder(pathToTests)
        // 2 generate Main.kt
        val mainPkg = mainProject.packages[MAIN_PKG_NAME]!!
        val mainCode = addStdAndPutInMain(codegenKt(topLevelStatements), mainPkg, compilationTarget, pathToInfroProject)
        createCodeKtFile(pathToSrc, "Main.kt", mainCode)
        // 3 generate every package like folders with code
        generatePackages(pathToInfroProj, notBindPackages.toList(), isTestsRun)
        generateBuildFile()
    }

    when (buildSystem) {
        // Amper using home/.niva/infroProject to generate code
        BuildSystem.Amper -> {
            val path = File(pathToDotNivaFolder)
            generateProj(path.toPath()) {
                // 4 regenerate amper
                regenerateAmper(pathToAmper, compilationTarget)
                // 4 regenerate amper's gradle
                regenerateGradleForAmper(
                    pathToGradle,
                    compilationTarget,
                    mainFileName
                )
            }
        }
        // Mill is using the current folder of the niva project
        BuildSystem.Mill -> {
            generateMillProjectTemplateIfNotExist(pathToInfroProject)

            val path = File(pathToDotNivaFolder)
            generateProj(path.toPath() / "niva") {
                // 4 regenerate mill
                regenerateMill(pathToMill)
            }
        }
        BuildSystem.Gradle ->
            TODO("Deprecated")
    }

}

fun codegenKt(statements: List<Statement>, indent: Int = 0, pkg: Package? = null, forTest: Boolean = false): String = buildString {
    if (statements.isEmpty()) {
        if (pkg != null)
            append("package ${pkg.packageName}\n\n")
        return@buildString
    }

    if (pkg != null) {

        append("package ${pkg.packageName}\n\n")
        if (pkg.packageName != "core")
            append("import $MAIN_PKG_NAME.*\n")
        if (forTest) {
            append(
                """
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
""")
//            appendnl("import $it.*")

        }
        append(pkg.generateImports())
    }
    val generator = GeneratorKt()

    if (forTest) {
        append("class ", pkg!!.packageName + "Test", "{\n")
        statements.forEach {
            appendLine("@Test")
            val md = it as MessageDeclaration
            val qw = codegenKt(md.body, indent + 2)
            append("fun ", md.name, "()", " {", "\n")
            appendLine(qw)
            append("    }")
        }
        append("\n}\n") // close class
    } else {
        statements.forEach {
            appendLine(generator.generateKtStatement(it, indent))
        }
    }

}

