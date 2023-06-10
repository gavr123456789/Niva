package frontend.parser.parsing

import frontend.meta.TokenType
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

private fun Parser.typeFields(): MutableList<TypeField> {
    val typeFields = mutableListOf<TypeField>()

    do {
        val isGeneric = match(TokenType.Apostrophe)
        val name = step()
        val type: Type? = if (!isGeneric) {
            matchAssert(TokenType.Colon, "colon before type name expected")
            parseType()
        } else {
            null
        }

        // type declaration can be separated on many lines
        match(TokenType.EndOfFile)
        match(TokenType.EndOfLine)

        typeFields.add(TypeField(name = name.lexeme, type = type, token = name))
    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1) || check(TokenType.Apostrophe))
    return typeFields
}

fun Parser.unionDeclaration(): UnionDeclaration {
    val unionTok = matchAssert(TokenType.Union, "")
    val unionName = matchAssertAnyIdent("name of the union expected")
    val localFields = typeFields()

    matchAssert(TokenType.Equal, "Equal expected")
    match(TokenType.EndOfLine)

    fun unionFields(): List<UnionBranch> {
        val unionBranches = mutableListOf<UnionBranch>()

        do {
            // | Rectangle => width: int height: int
            val pipeTok = matchAssert(TokenType.Pipe, "pipe expected on each union branch declaration")
            val branchName = matchAssertAnyIdent("Name of the union branch expected")

            matchAssert(TokenType.Then, "=> expected")

            val fields = typeFields()

            unionBranches.add(
                UnionBranch(
                    typeName = branchName.lexeme,
                    fields = fields,
                    token = pipeTok
                )
            )
            match(TokenType.EndOfLine)
            match(TokenType.EndOfFile)

        } while (check(TokenType.Pipe))

        return unionBranches
    }

    val unionBranches = unionFields()


    val result = UnionDeclaration(
        typeName = unionName.lexeme,
        branches = unionBranches,
        token = unionTok,
        fields = localFields
    )

    return result
}
