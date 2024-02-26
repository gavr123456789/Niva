package main.frontend.resolver

import frontend.resolver.Resolver
import frontend.resolver.Type
import main.frontend.parser.types.ast.StaticBuilderDeclaration


fun Resolver.resolveStaticBuilderDeclaration(
    st: StaticBuilderDeclaration,
    needResolveOnlyBody: Boolean,
    previousScope: MutableMap<String, Type>,
) {

}
