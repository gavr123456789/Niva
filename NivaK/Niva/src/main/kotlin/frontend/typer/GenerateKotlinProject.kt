package frontend.typer

import addNivaStd
import codogen.codogenKt
import frontend.util.addIndentationForEachString
import putInMainKotlinCode
import java.io.File
import kotlin.io.path.div
import java.nio.file.Path as Path

fun deleteAndRecreateKotlinFolder(path: File) {
    if (path.deleteRecursively()) {
        println("Deleted: ${path.absolutePath}")
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
            println("File created: ${baseDir.absolutePath}")
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
    val code2 = putInMainKotlinCode(code1)
    val code3 = code2.addNivaStd()
    return code3
}

fun Resolver.generatePackages(pathToSource: Path) {
    val commonProject = projects["common"]!!
//    val builder = StringBuilder()
    commonProject.packages.forEach { (k, v) ->

        val q = codogenKt(v.declarations)
        println()
        // generate folder for package
        val w = (pathToSource / v.packageName).toFile()
        w.mkdir()
        // generate file with code
        createCodeKtFile(w, v.packageName + ".kt", q)


    }

}



fun Resolver.generateKtProject(pathToSrcKtFolder: String) {
    val path = File(pathToSrcKtFolder)
    // 1 recreate pathToSrcKtFolder
    deleteAndRecreateKotlinFolder(path)
    // 2 generate Main.kt
    val mainCode = addStdAndPutInMain(generateMainKtCode())
    val mainFile = createCodeKtFile(path, "Main.kt", mainCode)
    println()
    // 3 generate every package like folders with code
    generatePackages(path.toPath())
}
