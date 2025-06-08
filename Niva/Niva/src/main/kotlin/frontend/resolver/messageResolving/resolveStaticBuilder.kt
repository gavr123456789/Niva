package main.frontend.resolver.messageResolving

import frontend.resolver.*
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.StaticBuilder
import main.frontend.parser.types.ast.collectExpressionsForDefaultAction


fun Resolver.findBuilderInManyPkg(packages: List<Package>, name: String, errorToken: Token): BuilderMetaData {
    val foundBuilders = mutableListOf<BuilderMetaData>()

    packages.forEach {
        val q = it.builders[name]
        if (q != null)
            foundBuilders.add(q)
    }

    if (foundBuilders.isEmpty()) {
        errorToken.compileError("Can't find builder $name")
    } else if (foundBuilders.count() > 1) {
        // собрать те пакеты в которых есть найденные билдеры
        val pkgToBuilder = mutableMapOf<String, BuilderMetaData>()
        foundBuilders.forEach { pkgToBuilder[it.pkg] = it }

        val pkgsWhereBuiders = pkgToBuilder.keys

        val currentImports = getCurrentImports(errorToken)
        pkgsWhereBuiders.forEach {
            val sasWithoutCurrent = pkgsWhereBuiders - it
            if (currentImports.contains(it) && !currentImports.containsAll(sasWithoutCurrent)) {
                return pkgToBuilder[it]!!
            }
        }
        errorToken.compileError("Can't find builder $name")
    } else {
        return foundBuilders[0]
    }


}

fun Resolver.resolveStaticBuilder(
    statement: StaticBuilder,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
): Pair<Type, MessageMetadata?> {
    stack.push(statement)
    currentLevel++
    val previousAndCurrentScope = (currentScope + previousScope).toMutableMap()

    // resolve receiver
    if (statement.receiverOfBuilder != null) {
        resolveSingle(statement.receiverOfBuilder, previousAndCurrentScope, statement)
    }


    // find in DB
    val pkg = getCurrentPackage(statement.token)
    val msgFromDb = if (statement.receiverOfBuilder == null) {
        val currentProject = projects[currentProjectName]!!

        findBuilderInManyPkg(currentProject.packages.values.toList(), statement.name, statement.token)
    } else {
        val proto = statement.receiverOfBuilder.type!!.protocols.values.find {
            it.builders.contains(statement.name)
        }
        if (proto == null) {
            statement.token.compileError("Can't find builder ${statement.name} for type ${statement.receiver}")
        }
        proto.builders[statement.name]!!
    }

    pkg.addImport(msgFromDb.pkg)

    statement.declaration = msgFromDb.declaration
    statement.msgMetaData = msgFromDb
    statement.type = msgFromDb.returnType


    // default action
    val defaultAction = msgFromDb.defaultAction
    if (defaultAction != null) {
        statement.defaultAction = defaultAction
    }
    // add this
    // there are always some this inside builder
    previousAndCurrentScope["this"] = msgFromDb.forType
    // add args of receiver
    val receiverType = msgFromDb.forType
    if (receiverType is Type.UserLike && receiverType.fields.isNotEmpty()) {
        receiverType.fields.forEach {
            previousAndCurrentScope[it.name] = it.type
        }
    }
    // resolve body
    resolve(statement.statements, (previousAndCurrentScope))


    statement.collectExpressionsForDefaultAction()

    // check the args, because builder always have keyword msg, but it can be unary msg actually
    if (msgFromDb.argTypes.count() != statement.args.count()) {
        statement.token.compileError("You calling builder $statement with ${statement.args.count()} args, but it have ${msgFromDb.argTypes.count()} args at the declaration")
    }
    // resolve arguments
    resolveKwArgs(
        statement,
        statement.args,
        previousAndCurrentScope,
        false,
        msgFromDb.argTypes.map { it.type }
    )


    // end
    currentLevel--
    addToTopLevelStatements(statement)
    stack.pop()

    return Pair(msgFromDb.returnType, msgFromDb)
}
