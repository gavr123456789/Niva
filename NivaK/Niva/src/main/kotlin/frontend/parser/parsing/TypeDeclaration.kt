package frontend.parser.parsing

import frontend.meta.TokenType
import frontend.meta.compileError
import frontend.parser.types.ast.*

fun Parser.typeDeclaration(): TypeDeclaration {
    // type Person name: string generic age: int

    // type Person
    //   name: string

    val typeToken = step() // skip type
    val typeName = matchAssertAnyIdent("after \"type\" type identifier expected")
    // type Person^ name: string age: int

    // if type decl separated
    val apostropheOrIdentWithColon = check(TokenType.Apostrophe) ||
            (check(TokenType.Identifier, 1) && check(TokenType.Colon, 2))
    if (check(TokenType.EndOfLine) && apostropheOrIdentWithColon) {
        step()
    }


    val typeFields = typeFields()

    val result = TypeDeclaration(
        typeName = typeName.lexeme,
        fields = typeFields,
        token = typeToken
    )
    return result
}

sealed class Sas<T>(val x: T)
class X<T>(x: T) : Sas<T>(x)
class Y<T>(x: T) : Sas<T>(x)


private fun Parser.typeFields(): MutableList<TypeFieldAST> {
    val fields = mutableListOf<TypeFieldAST>()

    if (check(TokenType.EndOfLine))
        return mutableListOf()

    do {
        val isGeneric = match(TokenType.Apostrophe)
        val name = step()
        val type: TypeAST? = if (!isGeneric) {
            val isThereFields = match(TokenType.Colon)
            if (!isThereFields && !check(TokenType.EndOfLine)) {
                name.compileError("Syntax error, expected : fields or new line, but found \"$name\"")
            }
            parseType()
        } else {
            null
        }

        // type declaration can be separated on many lines
        match(TokenType.EndOfLine)

        match(TokenType.EndOfFile)

        fields.add(
            TypeFieldAST(
                name = name.lexeme,
                type = type,
                token = name,
            )
        )
    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1) || check(TokenType.Apostrophe))
    return fields
}

fun Parser.unionDeclaration(): UnionDeclaration {
    val unionTok = matchAssert(TokenType.Union, "")
    val unionName = matchAssertAnyIdent("name of the union expected")
    val localFields = typeFields()

    matchAssert(TokenType.Assign, "Equal expected")
    match(TokenType.EndOfLine)

    fun unionFields(root: UnionDeclaration): List<UnionBranch> {
        val unionBranches = mutableListOf<UnionBranch>()

        do {
            skipNewLinesAndComments()

            // | Rectangle => width: int height: int
            val pipeTok = matchAssert(TokenType.Pipe, "pipe expected on each union branch declaration")
            val branchName = matchAssertAnyIdent("Name of the union branch expected")

            matchAssert(TokenType.Then, "=> expected")

            val fields = typeFields()

            unionBranches.add(
                UnionBranch(
                    typeName = branchName.lexeme,
                    fields = fields,
                    token = pipeTok,
                    root = root
                )
            )
//            match(TokenType.EndOfLine)
//            match(TokenType.EndOfFile)

        } while (check(TokenType.Pipe))

        return unionBranches
    }


    val root = UnionDeclaration(
        typeName = unionName.lexeme,
        branches = mutableListOf(),
        token = unionTok,
        fields = localFields
    )
    val unionBranches = unionFields(root)
    root.branches = unionBranches


    return root
}

fun Parser.typeAliasDeclaration(): AliasDeclaration {
    val tok = matchAssert(TokenType.Alias)
    val x = identifierMayBeTyped()
    val equal = matchAssert(TokenType.Assign)
    val y = identifierMayBeTyped()
    val result = AliasDeclaration(
        x.name,
        y.name,
        tok
    )

    return result

}
