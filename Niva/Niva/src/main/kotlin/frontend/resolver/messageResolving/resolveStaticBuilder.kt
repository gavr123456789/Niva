package main.frontend.resolver.messageResolving

import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.addToTopLevelStatements
import frontend.resolver.getCurrentPackage
import frontend.resolver.resolve
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.StaticBuilder
import main.frontend.parser.types.ast.collectExpressionsForDefaultAction


fun Resolver.resolveStaticBuilder(
    statement: StaticBuilder,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
) {
    stack.push(statement)
    currentLevel++
    val previousAndCurrentScope = (currentScope + previousScope).toMutableMap()

    // find in DB
    val pkg = getCurrentPackage(statement.token)
    val builderFromDB = pkg.builders[statement.name]
    if (builderFromDB == null) {
        statement.token.compileError("Can't find builder ${statement.name}, builders of this pkg: ${pkg.builders.keys}")
    }
    statement.type = builderFromDB.returnType


    // default action
    val defaultAction = builderFromDB.defaultAction
    if (defaultAction != null) {
        statement.defaultAction = defaultAction
        previousAndCurrentScope["this"] = builderFromDB.forType
    }
    // add this
    // resolve body
    resolve(statement.statements, (previousAndCurrentScope))


    statement.collectExpressionsForDefaultAction()


    // resolve arguments
    resolveKwArgs(
        statement,
        statement.args,
        previousAndCurrentScope,
        true,
        builderFromDB.argTypes.map { it.type }
    )


    // end
    currentLevel--
    addToTopLevelStatements(statement)
    stack.pop()
}
