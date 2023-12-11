package main.frontend.typer

import codogen.collectAllGenericsFromBranches
import frontend.meta.compileError
import frontend.parser.types.ast.EnumDeclarationRoot
import frontend.parser.types.ast.UnionDeclaration
import frontend.resolver.*
import frontend.util.containSameFields
import frontend.util.setDiff

fun Resolver.resolveUnionDeclaration(statement: UnionDeclaration, previousScope: MutableMap<String, Type>) {
    val rootType =
        statement.toType(currentPackageName, typeTable, typeDB, isUnion = true) as Type.UserUnionRootType//fix
    addNewType(rootType, statement)


    val branches = mutableListOf<Type.UserUnionBranchType>()
    val genericsOfBranches = mutableSetOf<Type>()
    statement.branches.forEach {
        val branchType =
            it.toType(
                currentPackageName,
                typeTable,
                typeDB,
                unionRootType = rootType
            ) as Type.UserUnionBranchType//fix
        branchType.parent = rootType


        // check if type already exist, and it doesn't have fields,
        // than it's not a new type, but branch with branches
        val tok = it.token
        val alreadyRegisteredType = typeAlreadyRegisteredInCurrentPkg(branchType, getCurrentPackage(tok), tok)
        if (alreadyRegisteredType == null) {
            addNewType(branchType, it, checkedOnUniq = true)
        } else {

            // check that it has the same fields as current root
            if (!containSameFields(alreadyRegisteredType.fields, rootType.fields)) {
                it.token.compileError("Union Root inside other union declaration must have same fields, ${branchType.name}: ${branchType.fields.map { it.name }} != ${rootType.name}: ${rootType.fields.map { it.name }}")
            }
            it.isRoot = true
        }

        branchType.fields += rootType.fields
        branches.add(branchType)
        genericsOfBranches.addAll(branchType.typeArgumentList)
    }
    rootType.branches = branches
    rootType.typeArgumentList += genericsOfBranches


    /// generics
    // add generics from branches
    val allGenerics = statement.collectAllGenericsFromBranches() + statement.genericFields
    statement.genericFields.clear()
    statement.genericFields.addAll(allGenerics)
    // not only to statement, but to Type too
}

fun Resolver.resolveEnumDeclaration(statement: EnumDeclarationRoot, previousScope: MutableMap<String, Type>) {
    // resolve types of fields of root
    val rootType = statement.toType(currentPackageName, typeTable, typeDB, isEnum = true) as Type.UserEnumRootType
    addNewType(rootType, statement)

    // TODO check that this enum in unique in this package
    val namesOfRootFields = rootType.fields.map { it.name }.toSet()
    val branches = mutableListOf<Type.UserEnumBranchType>()
//    val genericsOfBranches = mutableSetOf<Type>()
    statement.branches.forEach {
        val brachFields = it.fieldsValues.map { x -> x.name }.toSet()
        val diff = setDiff(namesOfRootFields, brachFields)
        if (diff.isNotEmpty()) {
            it.token.compileError("Some enum fields are missing in ${it.typeName}: $diff")
        }

        val branchType =
            it.toType(currentPackageName, typeTable, typeDB, enumRootType = rootType) as Type.UserEnumBranchType
        branchType.parent = rootType

        // Check fields
        it.fieldsValues.forEach { fieldAST ->
            currentLevel++
            resolve(listOf(fieldAST.value), previousScope)
            currentLevel--
            val rootFieldWithSameName = rootType.fields.find { x -> x.name == fieldAST.name }
                ?: fieldAST.token.compileError("Each branch of enum must define values for each field, ${rootType.name} ${rootType.fields.map { x -> x.name }}")

            if (!compare2Types(fieldAST.value.type!!, rootFieldWithSameName.type)) {
                fieldAST.token.compileError("In enum branch: `${it.typeName}` field `${fieldAST.name}` has type `${fieldAST.value.type}` but `${rootFieldWithSameName.type}` expected")
            }

        }

        branchType.fields += rootType.fields
        branches.add(branchType)
    }

    rootType.branches = branches

}
