package frontend.parser.parsing

import frontend.parser.types.ast.Pragma
import main.utils.RESET
import main.utils.WHITE
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.parsing.parseType
import main.frontend.parser.parsing.simpleReceiver
import main.frontend.parser.types.ast.*

fun Parser.typeDeclaration(pragmas: MutableList<Pragma>): TypeDeclaration {
    // type Person name: string generic age: int
    // OR
    // type Person
    //   name: string

    val typeToken = step() // skip type
    val typeName = matchAssertAnyIdent("after \"type\" type identifier expected")
    // type Person^ name: string age: int

    val genericTypeParam = if (match(TokenType.DoubleColon)) {
        matchAssertAnyIdent("inside type declaration after `Type::` generic param expected")
    } else null

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
        token = typeToken,
        pragmas = pragmas
    )
    if (genericTypeParam != null) {
        result.genericFields.add(genericTypeParam.lexeme)
    }
    return result
}

fun Parser.enumDeclaration(pragmas: MutableList<Pragma>): EnumDeclarationRoot {
    val enumTok = matchAssert(TokenType.Enum, "")
    val enumName = matchAssertAnyIdent("name of the enum expected")
    val localFields = if (check(TokenType.Assign)) listOf() else typeFields()
    val isThereBrunches = match(TokenType.Assign)


    fun enumBranches(root: EnumDeclarationRoot): List<EnumBranch> {
        val enumBranches = mutableListOf<EnumBranch>()
        var firstBranch = true
        do {
            // | Rectangle width: int height: int

            // enum Sas = Q | W | E

            val pipeTok = getPipeTok(firstBranch)

            val branchName = matchAssertAnyIdent("Name of the enum branch expected")
            val fields = enumFields()

            enumBranches.add(
                EnumBranch(
                    name = branchName.lexeme,
                    fieldsValues = fields,
                    token = pipeTok,
                    root = root
                )
            )

            firstBranch = false
            skipNewLinesAndComments()
        } while (check(TokenType.If))

        return enumBranches
    }


    val root = EnumDeclarationRoot(
        typeName = enumName.lexeme,
        branches = mutableListOf(),
        token = enumTok,
        fields = localFields,
        pragmas = pragmas
    )

    if (isThereBrunches) {
        val unionBranches = enumBranches(root)
        root.branches = unionBranches
    }

    return root
}

private fun Parser.getPipeTok(firstBranch: Boolean) =
    if (firstBranch) {
        if (match(TokenType.EndOfLine) || check(TokenType.If)) {
            skipNewLinesAndComments()
            matchAssert(TokenType.If, "pipe expected on each enum branch declaration")
        } else {
            val tok = peek()
            skipNewLinesAndComments()
            tok
        }
    } else {
        skipNewLinesAndComments()
        matchAssert(TokenType.If, "pipe expected on each enum branch declaration")
    }


fun Parser.enumFields(): MutableList<EnumFieldAST> {
    val fields = mutableListOf<EnumFieldAST>()
    if (checkEndOfLineOrFile() || check(TokenType.If)) {
        skipNewLinesAndComments()
        return mutableListOf()
    }
    do {
        val name = matchAssertAnyIdent("Identifier expected, but found ${peek().lexeme}")
        matchAssert(TokenType.Colon)
        val expr = simpleReceiver() //?: peek().compileError("Primary expected")
//        skipOneEndOfLineOrFile()

        fields.add(
            EnumFieldAST(
                name = name.lexeme,
                value = expr,
                token = name,
            )
        )
    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1) || check(TokenType.Apostrophe))
    return fields
}


fun Parser.typeFields(): MutableList<TypeFieldAST> {
    val fields = mutableListOf<TypeFieldAST>()

    if (checkEndOfLineOrFile()) {
        skipNewLinesAndComments()
        return mutableListOf()
    }

    do {
        val isGeneric = match(TokenType.Apostrophe)
        val name = matchAssertAnyIdent("Identifier expected, but found ${peek().lexeme}")
        val type: TypeAST? = if (!isGeneric) {
            val isThereFields = match(TokenType.Colon)
            val isThereEndOfLine = skipOneEndOfLineOrComment()
            if (!isThereFields && !isThereEndOfLine) {
                if (match(TokenType.DoubleColon)) {
                    name.compileError("For fields of type you need to use $WHITE`:`$RESET not $WHITE`::`")
                }
                name.compileError("Syntax error, expected : fields or new line, but found \"$name\"")
            }
            if (isThereEndOfLine) {
                skipNewLinesAndComments()
            }
            parseType()
        } else {
            null
        }
        // type declaration can be separated on many lines
        skipOneEndOfLineOrComment()

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

fun Parser.unionDeclaration(pragmas: MutableList<Pragma>): UnionDeclaration {
    val unionTok = matchAssert(TokenType.Union, "")
    val unionName = matchAssertAnyIdent("name of the union expected")
    val localFields = if (check(TokenType.Assign)) listOf() else typeFields()
    val isThereBrunches = match(TokenType.Assign)

    fun unionFields(root: UnionDeclaration): List<UnionBranch> {
        val unionBranches = mutableListOf<UnionBranch>()
        var firstBranch = true

        do {
            skipNewLinesAndComments()

            // | Rectangle => width: int height: int
            val pipeTok =
                getPipeTok(firstBranch)//matchAssert(TokenType.Pipe, "pipe expected on each union branch declaration")


            val branchName = matchAssertAnyIdent("Name of the union branch expected")
            val fields = typeFields()

            unionBranches.add(
                UnionBranch(
                    typeName = branchName.lexeme,
                    fields = fields,
                    token = pipeTok,
                    root = root
                )
            )

            firstBranch = false
            skipNewLinesAndComments()
        } while (check(TokenType.If))

        return unionBranches
    }

    val root = UnionDeclaration(
        typeName = unionName.lexeme,
        branches = mutableListOf(),
        token = unionTok,
        fields = localFields,
        pragmas = pragmas
    )
    if (isThereBrunches) {
        val unionBranches = unionFields(root)
        root.branches = unionBranches
    }

    return root
}

@Suppress("unused")
fun Parser.typeAliasDeclaration(): AliasDeclaration {
    val tok = matchAssert(TokenType.Alias)
    val x = identifierMayBeTyped()
    matchAssert(TokenType.Assign) // equal
    val y = identifierMayBeTyped()
    val result = AliasDeclaration(
        x.name,
        y.name,
        tok
    )

    return result

}
