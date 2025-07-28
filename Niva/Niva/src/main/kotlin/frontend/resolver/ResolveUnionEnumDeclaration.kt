package main.frontend.typer

import frontend.resolver.*
import main.utils.RED
import main.utils.WHITE
import main.utils.YEL
import main.codogen.collectAllGenericsFromBranches
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.EnumDeclarationRoot
import main.frontend.parser.types.ast.UnionRootDeclaration
import main.frontend.util.childContainSameFieldsAsParent
import main.frontend.util.setDiff

fun Resolver.resolveUnionDeclaration(statement: UnionRootDeclaration, isError: Boolean, dynamicType: Type) {
    val rootType2 =
        statement.toType(
            currentPackageName,
            typeTable,
            typeDB,
            isUnion = true,
            isError = isError
        ) as Type.UnionRootType//fix

    val realType = if (statement.pkg != null) {
        val realPkgOfUnionRoot = this.findPackageOrError(statement.pkg, statement.token)
        // find type with the same name inside this pkg
        val w = realPkgOfUnionRoot.types[rootType2.name] as Type.UnionRootType
        w
    } else null

    val rootType = realType ?: rootType2
    val addToFromDynamic = { currentType: Type ->
        val dynamicProtocol = createDynamicProtocol(currentType, dynamicType = dynamicType)
        currentType.protocols["dynamic"] = dynamicProtocol
    }

    val branches = mutableListOf<Type.Union>()
    val genericsOfBranches = mutableSetOf<Type>()
    statement.branches.forEach {
        // check if type already exist, and it doesn't have fields,
        // than it's not a new type, but branch with branches
        val tok = it.token
        val pkg = getCurrentPackage(tok)
        val alreadyRegisteredType = typeAlreadyRegisteredInCurrentPkg(it.typeName, pkg, tok)

        if (alreadyRegisteredType == null && it.isRoot) { //
            // this is forward declaration of included union!
            // and it's not resolved, yet
            // otherwise it's a usual branch
            unResolvedTypeDeclarations.add(pkg.packageName, statement)
            return
        }


        val branchType = (if (alreadyRegisteredType is Type.Union && it.isRoot) {
            if (!childContainSameFieldsAsParent(alreadyRegisteredType.fields, rootType.fields, statement.token)) {
                it.token.compileError("Union inside other union declaration must have same fields, $YEL${alreadyRegisteredType.name} ${WHITE}${alreadyRegisteredType.fields.map { "${it.name}: ${it.type}" }} $RED!=${YEL} ${rootType.name}${RED} $WHITE${rootType.fields.map { "${it.name}: ${it.type}" }}")
            }
            alreadyRegisteredType
        } else {
            val branchType =
                it.toType(
                    currentPackageName,
                    typeTable,
                    typeDB,
                    unionRootType = rootType,
                    isError = isError
                ) as Type.UnionBranchType


            if (alreadyRegisteredType == null)
                addNewType(branchType, it, alreadyCheckedOnUnique = false)


            branchType
        }).also { branchType ->
            branchType.parent = rootType
            val set = mutableSetOf<Type.UnknownGenericType>()
            rootType.collectGenericParamsRecursivelyFRFR(set)
            branchType.addAllTypeArguments(set)
            1
        }


        branchType.fields += rootType.fields

        addToFromDynamic(branchType)
        branches.add(branchType)
        genericsOfBranches.addAll(branchType.typeArgumentList)
    }

    addToFromDynamic(rootType)
    addNewType(rootType, statement)

    rootType.branches = branches
    rootType.addAllTypeArguments(genericsOfBranches)

    /// generics
    // add generics from branches
    val allGenerics = statement.collectAllGenericsFromBranches() + statement.genericFields
    statement.genericFields.clear()
    statement.genericFields.addAll(allGenerics)

    if (isError) {
        val error = typeDB.userTypes["Error"]!!.find { it.pkg == "core" }!!
        rootType.parent = error
        // add protocol with throw that returns Nothing!Self
        branches.forEach {
            val w = createExceptionForCustomErrors(it)
            it.protocols.putAll(w)
        }
    }

}

fun Resolver.resolveEnumDeclaration(statement: EnumDeclarationRoot, previousScope: MutableMap<String, Type>) {
    // resolve types of fields of root
    val rootType = statement.toType(currentPackageName, typeTable, typeDB, isEnum = true) as Type.EnumRootType


    // TODO check that this enum in unique in this package
    val namesOfRootFields = rootType.fields.map { it.name }.toSet()
    val branches = mutableListOf<Type.EnumBranchType>()
//    val genericsOfBranches = mutableSetOf<Type>()
    statement.branches.forEach {
        val brachFields = it.fieldsValues.map { x -> x.name }.toSet()
        val diff = setDiff(namesOfRootFields, brachFields)
        if (diff.isNotEmpty()) {
            it.token.compileError("Some enum fields are missing in${YEL} ${it.typeName}$RED: $YEL$diff")
        }

        val branchType =
            it.toType(currentPackageName, typeTable, typeDB, enumRootType = rootType) as Type.EnumBranchType
        branchType.parent = rootType

        // Check fields
        it.fieldsValues.forEach { fieldAST ->

            currentLevel++
            try {
                resolveSingle((fieldAST.value), previousScope)

            } catch (_: Throwable) {
                unResolvedTypeDeclarations.add(getCurrentPackage(it.token).packageName, statement)
                return
            }
            currentLevel--
            val rootFieldWithSameName = rootType.fields.find { x -> x.name == fieldAST.name }
                ?: fieldAST.token.compileError("Each branch of enum must define values for each field,${YEL} ${rootType.name} ${WHITE}${rootType.fields.map { x -> x.name }}")

            if (!compare2Types(fieldAST.value.type!!, rootFieldWithSameName.type, fieldAST.token)) {
                fieldAST.token.compileError("In enum branch: `$YEL${it.typeName}$RED` field `$WHITE${fieldAST.name}$RED` has type `$YEL${fieldAST.value.type}$RED` but `$YEL${rootFieldWithSameName.type}$RED` expected")
            }

        }

        branchType.fields += rootType.fields
        branches.add(branchType)
    }

    addNewType(rootType, statement) // we're adding type after, because branch resolving can fail

    rootType.branches = branches

}
