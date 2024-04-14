package frontend.parser.parsing

import frontend.parser.types.ast.Pragma
import main.utils.RESET
import main.utils.WHITE
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.parsing.parseType
import main.frontend.parser.parsing.simpleReceiver
import main.frontend.parser.types.ast.*
import main.utils.RED
import main.utils.YEL

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
    } while (checkMany(TokenType.Identifier, TokenType.Colon) || check(TokenType.Apostrophe))
    return fields
}


fun Parser.typeFields(): MutableList<TypeFieldAST> {
    val fields = mutableListOf<TypeFieldAST>()

    if (checkEndOfLineOrFile() || check(TokenType.If)) {
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
                typeAST = type,
                token = name,
                type = null
            )
        )
    } while (check(TokenType.Identifier) && check(TokenType.Colon, 1) || check(TokenType.Apostrophe))
    return fields
}

fun Parser.unionDeclaration(pragmas: MutableList<Pragma>): UnionRootDeclaration {
    val unionTok = matchAssert(TokenType.Union, "")
    val unionName = dotSeparatedIdentifiers() ?: peek().compileError("name of the union expected")
    val localFields = if (check(TokenType.Assign)) listOf() else typeFields()
    val isThereBrunches = match(TokenType.Assign) //|| checkAfterSkip(TokenType.Colon)

    if (!isThereBrunches && checkAfterSkip(TokenType.If)) {
        unionTok.compileError("Union $YEL$unionName$RESET with branches detected, but you forget $WHITE'='$RESET (${WHITE}union $YEL$unionName$RED = $WHITE...)")
    }
    fun unionFields(root: UnionRootDeclaration): List<UnionBranchDeclaration> {
        val unionBranches = mutableListOf<UnionBranchDeclaration>()
        var firstBranch = true

        do {
            skipNewLinesAndComments()

            val pipeTok =
                getPipeTok(firstBranch)//matchAssert(TokenType.Pipe, "pipe expected on each union branch declaration")


            // | Rectangle => width: int height: int
            val branchName = identifierMayBeTyped()//matchAssertAnyIdent("Name of the union branch expected")
            val fields = typeFields()

            // save many names
            unionBranches.add(
                UnionBranchDeclaration(
                    typeName = branchName.name,
                    fields = fields,
                    token = pipeTok,
                    root = root,
                    names = branchName.names
                )
            )

            firstBranch = false
            skipNewLinesAndComments()
        } while (check(TokenType.If))

        return unionBranches
    }

    val root = UnionRootDeclaration(
        typeName = unionName.name,
        branches = mutableListOf(),
        token = unionTok,
        fields = localFields,
        pragmas = pragmas,
        pkg = if (unionName.names.count() > 1) unionName.names.dropLast(1).joinToString(".") else null
    )
    if (isThereBrunches) {
        val unionBranches = unionFields(root)
        root.branches = unionBranches
    }

    return root
}

fun Parser.typeAliasDeclaration(pragmas: MutableList<Pragma>): TypeAliasDeclaration {
    val tok = matchAssert(TokenType.Type)
    val name = matchAssert(TokenType.Identifier)

    matchAssert(TokenType.Assign)

    val typeAST = parseType()

    return TypeAliasDeclaration(
        realTypeAST = typeAST,
        typeName = name.lexeme,
        token = tok,
        pragmas = pragmas
    )

}
