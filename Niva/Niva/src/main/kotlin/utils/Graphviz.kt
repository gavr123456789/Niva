package main.utils

import frontend.resolver.Resolver
import java.io.File

private class DirNode(val name: String) {
    val subDirs = mutableMapOf<String, DirNode>()
    val packages = mutableListOf<String>()
}

private fun printDir(node: DirNode, idPrefix: String, greenPackages: Set<String>) {
    val isRoot = node.name == "root" && idPrefix == "cluster"

    if (!isRoot) {
        val safeName = node.name.replace(Regex("[^a-zA-Z0-9_]"), "_")
        println("  subgraph ${idPrefix}_$safeName {")
        println("    style=\"filled,rounded\";")
        println("    label = \"${node.name}\";")
    }

    node.packages.forEach { pkgName ->
        if (pkgName in greenPackages) {
            println("    \"$pkgName\" [style=filled, fillcolor=green];")
        } else {
            println("    \"$pkgName\";")
        }
    }

    node.subDirs.forEach { (_, childNode) ->
        printDir(childNode, "${idPrefix}_${node.name.replace(Regex("[^a-zA-Z0-9_]"), "_")}", greenPackages)
    }

    if (!isRoot) {
        println("  }")
    }
}

fun findPackageByFile(resolver: Resolver, filePath: String): String? {
    val absPath = File(filePath).absolutePath
    for (proj in resolver.projects.values) {
        for (pkg in proj.packages.values) {
            val decl = pkg.declarations.find { it.token.file.absolutePath == absPath }
            if (decl != null) return pkg.packageName
        }
    }
    return null
}

fun graphviz(
    pm: PathManager,
    args: MutableList<String>,
    resolver: Resolver
) {
    val firstArg = args.getOrNull(1)
    if (firstArg == null) {
        generateGraphviz(resolver)
        return
    }

    val file = File(firstArg)
    val (pkgName, depth) = if (file.exists()) {
        parseFileArgs(firstArg, args, pm, resolver)
    } else {
        parseNonFileArgs(firstArg, args, pm, resolver)
    }
    generateGraphviz(resolver, pkgName, depth)
}

private fun resolveMainPackageName(resolver: Resolver, pathToFile: String): String? {
    val potentialRoot = "${File(pathToFile).nameWithoutExtension}Niva"
    val rootExists = resolver.projects.values.any { it.packages.containsKey(potentialRoot) }
    return if (rootExists) potentialRoot else findPackageByFile(resolver, pathToFile)
}

private fun parseFileArgs(
    filePath: String,
    args: List<String>,
    pm: PathManager,
    resolver: Resolver
): Pair<String?, Int?> {
    val pathToFile = pm.pathToNivaMainFile
    val isMainFile = File(filePath).absolutePath == File(pathToFile).absolutePath
    val secondArg = args.getOrNull(2)

    if (secondArg != null) {
        val d = secondArg.toIntOrNull()
        if (d != null) {
            // FILE INT -> Focus on File's package with Depth
            val pkgName = if (isMainFile) {
                resolveMainPackageName(resolver, filePath)
            } else {
                findPackageByFile(resolver, filePath)
            }
            return pkgName to d
        } else {
            // FILE PKGNAME
            val pkgName = secondArg
            val thirdArg = args.getOrNull(3)
            val depth = thirdArg?.toIntOrNull()
            return pkgName to depth
        }
    } else {
        // FILE ONLY
        if (!isMainFile) {
             return findPackageByFile(resolver, filePath) to null
        } else {
            // main file, no extra args -> full graph
            return null to null
        }
    }
}

private fun parseNonFileArgs(
    firstArg: String,
    args: List<String>,
    pm: PathManager,
    resolver: Resolver
): Pair<String?, Int?> {
    val d = firstArg.toIntOrNull()
    if (d != null) {
        // INT -> Depth for Main Package
        val pkgName = resolveMainPackageName(resolver, pm.pathToNivaMainFile)
        return pkgName to d
    } else {
        // PKGNAME
        val pkgName = firstArg
        val secondArg = args.getOrNull(2)
        val depth = secondArg?.toIntOrNull()
        return pkgName to depth
    }
}


fun generateGraphviz(resolver: Resolver, focusPkgName: String? = null, depth: Int? = null) {
    val allPackages = resolver.projects.values.flatMap { it.packages.values }
    val pkgMap = allPackages.associateBy { it.packageName }

    val packagesToTraverse = if (focusPkgName != null) {
        val result = mutableSetOf<frontend.resolver.Package>()
        // Pair of Package and its level (distance from root)
        val queue = ArrayDeque<Pair<frontend.resolver.Package, Int>>()

        val startPkg = pkgMap[focusPkgName]
        if (startPkg != null) {
            queue.add(startPkg to 0)
        }

        while (queue.isNotEmpty()) {
            val (current, currentLevel) = queue.removeFirst()
            if (result.add(current)) {
                // If depth is specified, only traverse children if currentLevel < depth
                if (depth == null || currentLevel < depth) {
                    val imports = (current.imports + current.concreteImports + current.importsFromUse)
                        .filter { it != "common" && it != "core" }

                    imports.forEach { importName ->
                        pkgMap[importName]?.let { queue.add(it to currentLevel + 1) }
                    }
                }
            }
        }
        result
    } else {
        allPackages
    }

    println("digraph G {")
    
    // Build directory tree
    val root = DirNode("root")
    val cwd = File(".").canonicalFile.toPath()

    packagesToTraverse.forEach { pkg ->
        if (pkg.packageName == "common" || pkg.packageName == "core") return@forEach

        val file = pkg.declarations.firstOrNull()?.token?.file
        if (file != null) {
            val parentPath = file.parentFile
            val pathList = if (parentPath != null) {
                try {
                    val filePath = parentPath.canonicalFile.toPath()
                    cwd.relativize(filePath).toList().map { it.toString() }
                } catch (_: IllegalArgumentException) {
                    // Fallback to absolute path components if cannot relativize
                    parentPath.absolutePath.split(File.separator).filter { it.isNotEmpty() }
                }
            } else {
                emptyList()
            }
            
            var current = root
            pathList.forEach { part ->
                if (part != "." && part.isNotEmpty()) {
                    current = current.subDirs.getOrPut(part) { DirNode(part) }
                }
            }
            current.packages.add(pkg.packageName)
        } else {
             // If no file, put in root
             root.packages.add(pkg.packageName)
        }
    }

    val greenPackages = packagesToTraverse.filter { pkg ->
        val imports = pkg.imports + pkg.concreteImports + pkg.importsFromUse
        imports.none { it != "common" && it != "core" }
    }.map { it.packageName }.toSet()

    printDir(root, "cluster", greenPackages)

    packagesToTraverse.forEach { pkg ->
        val pkgName = pkg.packageName
        if (pkgName != "common" && pkgName != "core") {
            val allImports = pkg.imports + pkg.concreteImports + pkg.importsFromUse

            allImports.forEach { importedPkg ->
                if (pkgName != importedPkg && importedPkg != "common" && importedPkg != "core") {
                    println("  \"$pkgName\" -> \"$importedPkg\";")
                }
            }
        }
    }
    println("}")
}
