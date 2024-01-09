package main.frontend.resolver

import frontend.parser.types.ast.StaticBuilderDeclaration
import frontend.resolver.Resolver
import frontend.resolver.Type

fun Resolver.resolveStaticBuilderDeclaration(
    st: StaticBuilderDeclaration,
    needResolveOnlyBody: Boolean,
    previousScope: MutableMap<String, Type>,
) {

}
