package frontend.parser.parsing

import frontend.parser.types.ast.Pragma
import main.frontend.meta.Token
import main.frontend.meta.TokenType
import main.frontend.meta.compileError
import main.frontend.parser.parsing.parseTypeAST
import main.frontend.parser.parsing.simpleReceiver
import main.frontend.parser.types.ast.*
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL

fun Parser.typeDeclaration(pragmas: MutableList<Pragma>): TypeDeclaration {
    // type Person name: string generic age: int
    // OR
    // type Person
    //   name: string

    val typeToken = step() // skip type
    val typeName = matchAssertAnyIdent("name of the type expected")
    // type Person^ name: string age: int

    val genericTypeParam = if (match(TokenType.DoubleColon)) {
        matchAssertAnyIdent("inside type declaration after `Type::` generic param expected")
    } else null

    skipNewLinesAndComments()

    val typeFields2 = typeFieldsAndMessageDecl(typeName)
    val typeFields = typeFields2.a

    tree.addAll(typeFields2.b)


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
    val localFields = if (check(TokenType.Assign)) emptyList() else typeFields()
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
        val name = matchAssertAnyIdent("name of the enum's field expected, but found ${peek().lexeme}")
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

    if (!(check(TokenType.Identifier) && check(TokenType.Colon, 1))) {
        skipNewLinesAndComments()
        return mutableListOf()
    }

    do {
        val isGeneric = match(TokenType.Apostrophe)
        val name = matchAssertAnyIdent("name of the field expected, but found ${peek().lexeme}")
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
            parseTypeAST()
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


class TypeFieldsAndMessageDecl(val a: MutableList<TypeFieldAST>, val b: MutableList<MessageDeclaration>)
enum class KindOfTypeDecl {
    MutField, Field, Unary, Binary, Keyword, Nope
}

fun Parser.typeFieldsAndMessageDecl(typeName: Token): TypeFieldsAndMessageDecl {
    val fields = mutableListOf<TypeFieldAST>()
    val messageDeclarations = mutableListOf<MessageDeclaration>()

    if (checkEndOfLineOrFile() || check(TokenType.If)) {
        skipNewLinesAndComments()
        return TypeFieldsAndMessageDecl(mutableListOf(), mutableListOf())
    }

    val checker: () -> KindOfTypeDecl = {
        // 0 qq: mut Q -> Field
        // 1 qq: Q -> Field
        // 4 qq::    -> Kw
        // 5 qq: q:: -> Kw
        // 2 aa -> = -> Unary
        // 3 + -> Binary
        when {
            checkMany(TokenType.Identifier, TokenType.Colon, TokenType.Mut) -> {
                // 0
                if (checkIdentifier(3) || check(TokenType.OpenBracket, 3))
                    KindOfTypeDecl.MutField
                else
                    peek(2).compileError("parsing error, field declaration expected(Identifier after :)")

            }
            checkMany(TokenType.Identifier, TokenType.Colon) -> {
                // 1
                if (checkIdentifier(2) ||
                    check(TokenType.OpenBracket, 2)
                    || check(TokenType.OpenBrace, 2) // collection type List
                    || check(TokenType.OpenBraceHash, 2) // collection type Map
                    || check(TokenType.OpenParenHash, 2)) // collection type Set
                    KindOfTypeDecl.Field
                else
                    peek(2).compileError("parsing error, field declaration expected(Identifier after :)")
            }

            check(TokenType.On) -> {
                if (check(TokenType.Identifier, 1)) {
                    // 5
                    if (checkMany(TokenType.Colon, TokenType.Identifier, TokenType.DoubleColon, distance = 2)) {
                        KindOfTypeDecl.Keyword
                    }
                    // 4
                    else if (check(TokenType.DoubleColon, 2)) {
                        KindOfTypeDecl.Keyword
                    }
                    // 2
                    else if (check(TokenType.Assign, 2) || check(TokenType.ReturnArrow, 2))
                        KindOfTypeDecl.Unary
                    else
                        peek(2).compileError("Parsing error of kw or unary message declaration inside type declaration, but found ${peek(2)}")

                } // 3
                else if (check(TokenType.BinarySymbol, 1))
                    KindOfTypeDecl.Binary
                else
                    peek(1).compileError("parsing error of message declaration inside type declaration")

            }

            else -> KindOfTypeDecl.Nope
//                    peek(2).compileError("parsing error of type declaration")
        }
    }

    val fakeTypeAst = TypeAST.UserType(typeName.lexeme, token = typeName)

    var q = checker()
    skipNewLinesAndComments()
//    skipOneEndOfLineOrComments()
    while (q != KindOfTypeDecl.Nope) {
        when (q) {
            KindOfTypeDecl.Field -> {
                val name = step()
                step() // colon
                val type = parseTypeAST()
                fields.add(
                    TypeFieldAST(
                        name = name.lexeme,
                        typeAST = type,
                        token = name,
                        type = null
                    )
                )
            }
            KindOfTypeDecl.MutField -> {
                // sas: mut String
                val name = step()
                step() // colon
                val type = parseTypeAST()
                fields.add(
                    TypeFieldAST(
                        name = name.lexeme,
                        typeAST = type,
                        token = name,
                        type = null
                    )
                )
            }

            KindOfTypeDecl.Unary -> {
                match(TokenType.On)
                val u = unaryDeclaration(fakeTypeAst)
                messageDeclarations.add(u)
            }

            KindOfTypeDecl.Binary -> {
                match(TokenType.On)
                val b = binaryDeclaration(fakeTypeAst)
                messageDeclarations.add(b)
            }

            KindOfTypeDecl.Keyword -> {
                match(TokenType.On)
                val k = keywordDeclaration(fakeTypeAst)
                messageDeclarations.add(k)
            }

            KindOfTypeDecl.Nope -> peek().compileError("parsing error, KindOfTypeDecl=Nope")
        }
        skipNewLinesAndComments()

        q = checker()

    }

    return TypeFieldsAndMessageDecl(fields, messageDeclarations)
}

fun Parser.errordomainDeclaration(pragmas: MutableList<Pragma>): ErrorDomainDeclaration {
    val errordomainTok = step()
    val union = unionDeclaration(pragmas, errordomainTok)
    return ErrorDomainDeclaration(union)
}

fun Parser.unionDeclaration(pragmas: MutableList<Pragma>, firstTokAlreadyParsed: Token? = null): UnionRootDeclaration {
    val unionTok = firstTokAlreadyParsed ?: step()
    val unionName = dotSeparatedIdentifiers() ?: unionTok.compileError("name of the union expected")
    val localFields = if (check(TokenType.Assign)) emptyList() else typeFields()
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
            val inlineBranch = match(TokenType.Return)
            val branchName = identifierMayBeTyped()//matchAssertAnyIdent("Name of the union branch expected")

            // union Sas =
            // | Sus
            //   x: Int
            skipNewLinesAndComments()

            val fields = typeFields()

            // save many names
            unionBranches.add(
                UnionBranchDeclaration(
                    typeName = branchName.name,
                    fields = fields,
                    token = pipeTok,
                    root = root,
                    isRoot = inlineBranch
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

    val typeAST = parseTypeAST()

    return TypeAliasDeclaration(
        realTypeAST = typeAST,
        typeName = name.lexeme,
        token = tok,
        pragmas = pragmas
    )

}
