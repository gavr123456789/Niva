package main.frontend.typer

import frontend.resolver.*
import main.utils.RED
import main.utils.WHITE
import main.utils.YEL
import main.codogen.collectAllGenericsFromBranches
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.EnumDeclarationRoot
import main.frontend.parser.types.ast.UnionRootDeclaration
import main.frontend.util.containSameFields
import main.frontend.util.setDiff

fun Resolver.resolveUnionDeclaration(statement: UnionRootDeclaration) {
    val rootType2 =
        statement.toType(currentPackageName, typeTable, typeDB, isUnion = true) as Type.UserUnionRootType//fix

    val realType = if (statement.pkg != null) {
        val realPkgOfUnionRoot = this.findPackageOrError(statement.pkg, statement.token)
        // find type with the same name inside this pkg
        val w = realPkgOfUnionRoot.types[rootType2.name] as Type.UserUnionRootType
        w
    } else null

    val rootType = realType ?: rootType2

    addNewType(rootType, statement)

    val branches = mutableListOf<Type.Union>()
    val genericsOfBranches = mutableSetOf<Type>()
    statement.branches.forEach {
        // check if type already exist, and it doesn't have fields,
        // than it's not a new type, but branch with branches
        val tok = it.token
        val alreadyRegisteredType = typeAlreadyRegisteredInCurrentPkg(it.typeName, getCurrentPackage(tok), tok)
        val branchType = if (alreadyRegisteredType == null) {
            val branchType =
                it.toType(
                    currentPackageName,
                    typeTable,
                    typeDB,
                    unionRootType = rootType
                ) as Type.UserUnionBranchType
            branchType.parent = rootType

            addNewType(branchType, it, checkedOnUniq = true)
            branchType
        } else {
            // type is found, this is UNION ROOT
            if (alreadyRegisteredType is Type.UserUnionRootType) {
                alreadyRegisteredType.parent = rootType

                // check that it has the same fields as current root
                if (!containSameFields(alreadyRegisteredType.fields, rootType.fields)) {
                    it.token.compileError("Union Root inside other union declaration must have same fields, $YEL${alreadyRegisteredType.name}: ${WHITE}${alreadyRegisteredType.fields.map { it.name }} $RED!=${YEL} ${rootType.name}${RED}: $WHITE${rootType.fields.map { it.name }}")
                }
                it.branches = alreadyRegisteredType.branches
                it.isRoot = true
                alreadyRegisteredType

            } else {
                it.token.compileError("Compiler thinks: Something strange, $alreadyRegisteredType is not a union root, but inside branches")
            }
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
            it.token.compileError("Some enum fields are missing in${YEL} ${it.typeName}$RED: $YEL$diff")
        }

        val branchType =
            it.toType(currentPackageName, typeTable, typeDB, enumRootType = rootType) as Type.UserEnumBranchType
        branchType.parent = rootType

        // Check fields
        it.fieldsValues.forEach { fieldAST ->
            currentLevel++
            resolveSingle((fieldAST.value), previousScope)
            currentLevel--
            val rootFieldWithSameName = rootType.fields.find { x -> x.name == fieldAST.name }
                ?: fieldAST.token.compileError("Each branch of enum must define values for each field,${YEL} ${rootType.name} ${WHITE}${rootType.fields.map { x -> x.name }}")

            if (!compare2Types(fieldAST.value.type!!, rootFieldWithSameName.type)) {
                fieldAST.token.compileError("In enum branch: `$YEL${it.typeName}$RED` field `$WHITE${fieldAST.name}$RED` has type `$YEL${fieldAST.value.type}$RED` but `$YEL${rootFieldWithSameName.type}$RED` expected")
            }

        }

        branchType.fields += rootType.fields
        branches.add(branchType)
    }

    rootType.branches = branches

}
