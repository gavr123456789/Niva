package main.frontend.resolver.messageResolving

import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.addToTopLevelStatements
import frontend.resolver.getCurrentPackage
import frontend.resolver.resolve
import frontend.resolver.resolveSingle
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

    // resolve receiver
    if (statement.receiverOfBuilder != null) {
        resolveSingle(statement.receiverOfBuilder, previousScope, statement)
    }


    // find in DB
    val pkg = getCurrentPackage(statement.token)
    val builderFromDB = if (statement.receiverOfBuilder == null) {
        pkg.builders[statement.name]}
    else {
        //pkg.types[statement.receiver.type!!.name]
        val proto = statement.receiverOfBuilder.type!!.protocols.values.find {
            it.builders.contains(statement.name)
        }
        if (proto == null) {
            statement.token.compileError("Can't find builder ${statement.name} for type ${statement.receiver}")
        }
        proto.builders[statement.name]!!
    }

    if (builderFromDB == null) {
        statement.token.compileError("Can't find builder ${statement.name}, builders of this pkg: ${pkg.builders.keys}")
    }
    statement.declaration = builderFromDB.declaration
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
