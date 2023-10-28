package frontend.typer

import addNivaStd
import codogen.generateKtStatement
import frontend.parser.types.ast.Statement
import frontend.util.addIndentationForEachString
import putInMainKotlinCode
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div

fun deleteAndRecreateKotlinFolder(path: File) {
    if (path.deleteRecursively()) {
//        println("Deleted: ${path.absolutePath}")
        path.mkdir()
    } else {
        throw Error("Failed to delete: ${path.absolutePath}")
    }
}

fun createCodeKtFile(path: File, fileName: String, code: String): File {
    val baseDir = path.toPath().resolve(fileName).toFile()
    if (baseDir.exists()) {
        println("File already exists: ${baseDir.absolutePath}")
    } else {
        if (baseDir.createNewFile()) {
//            println("File created: ${baseDir.absolutePath}")
            baseDir.writeText(code)
        } else {
            throw Error("Failed to create file: ${baseDir.absolutePath}")
        }
    }
    return baseDir
}

fun addStdAndPutInMain(ktCode: String, mainPkg: Package) = buildString {
    append("package main\n")
    val code1 = ktCode.addIndentationForEachString(1)
    val mainCode = putInMainKotlinCode(code1)
    val code3 = addNivaStd(mainCode)
    append(mainPkg.generateImports(), "\n")
    append(code3, "\n")
}
//fun addStdAndPutInMain(ktCode: String, mainPkg: Package): String {
//    val code1 = ktCode.addIndentationForEachString(1)
//    val mainCode = putInMainKotlinCode(code1)
//    val code3 = addNivaStd(mainCode)
//    val code4 = "package main\n\n$code3"
//    return code4
//}

fun Resolver.generatePackages(pathToSource: Path, notBindedPackages: List<Package>) {
//    val builder = StringBuilder()
    notBindedPackages.forEach { v ->


        val code = codogenKt(v.declarations, pkg = v)
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

fun Resolver.generateKtProject(pathToSrcKtFolder: String) {
    val mainProject = projects["common"]!!
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
    val mainCode = addStdAndPutInMain(codogenKt(topLevelStatements), mainPkg)
    val mainFile = createCodeKtFile(path, "Main.kt", mainCode)
    // 3 generate every package like folders with code


    generatePackages(path.toPath(), notBindedPackages)
}

fun codogenKt(statements: List<Statement>, indent: Int = 0, pkg: Package? = null): String = buildString {
    if (pkg != null) {

        append("package ${pkg.packageName}\n\n")
        if (pkg.packageName != "core")
            append("import main.*\n")

        append(pkg.generateImports())

    }
    statements.forEach {
        append(generateKtStatement(it, indent), "\n")
    }

}
