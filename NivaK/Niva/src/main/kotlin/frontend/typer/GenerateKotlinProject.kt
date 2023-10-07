package frontend.typer

import addNivaStd
import codogen.codogenKt
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


fun Resolver.generateMainKtCode(): String {
    return codogenKt(topLevelStatements)
}

fun addStdAndPutInMain(ktCode: String): String {
    val code1 = ktCode.addIndentationForEachString(1)
    val mainCode = putInMainKotlinCode(code1)
    val code3 = addNivaStd(mainCode)
    val code4 = "package main\n\n$code3"
    return code4
}

fun Resolver.generatePackages(pathToSource: Path) {
    val commonProject = projects["common"]!!
//    val builder = StringBuilder()
    commonProject.packages.forEach { (k, v) ->

        val code = codogenKt(v.declarations)
        // generate folder for package
        val folderForPackage = (pathToSource / v.packageName).toFile()
        folderForPackage.mkdir()
        // generate file with code
        createCodeKtFile(folderForPackage, v.packageName + ".kt", code)


    }

}


fun Resolver.generateKtProject(pathToSrcKtFolder: String) {
    val path = File(pathToSrcKtFolder)
    // 1 recreate pathToSrcKtFolder
    deleteAndRecreateKotlinFolder(path)
    // 2 generate Main.kt
    val mainCode = addStdAndPutInMain(generateMainKtCode())
    val mainFile = createCodeKtFile(path, "Main.kt", mainCode)
    // 3 generate every package like folders with code
    generatePackages(path.toPath())
}
