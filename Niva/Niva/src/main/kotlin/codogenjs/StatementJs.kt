package main.codogenjs

import main.frontend.meta.createFakeToken
import main.frontend.parser.types.ast.*

@Suppress("UnusedReceiverParameter")
fun GeneratorJs.generateJsStatement(statement: Statement, indent: Int): String = buildString {
    append(
        when (statement) {
            is Expression -> {
                val expr = statement.generateJsExpression()
                if (statement is MessageSend) expr + ";" else expr
            }
            is VarDeclaration -> "let ${statement.name.ifJsKeywordPrefix()} = ${statement.value.generateJsExpression()}"

            is MessageDeclaration -> statement.generateJsMessageDeclaration(false)
            is ExtendDeclaration -> statement.messageDeclarations.joinToString("\n") { it.generateJsMessageDeclaration(false) }
            is ManyConstructorDecl -> statement.messageDeclarations.joinToString("\n") { it.generateJsMessageDeclaration(true) }

            is TypeDeclaration ->
                statement.generateJsTypeDeclaration()
            is TypeAliasDeclaration -> "// typealias ${statement.typeName} = ${statement.realTypeAST.name}"

            is ReturnStatement -> {
                val expr = statement.expression
                if (expr != null) {
                    "return (" + expr.generateJsExpression() + ")"
                } else {
                    "return"
                }
            }
            is Assign -> "${statement.name.ifJsKeywordPrefix()} = ${statement.value.generateJsExpression()}"
            is DestructingAssign -> "// destructuring is not implemented in JS codegen yet"

            is UnionRootDeclaration ->
                statement.generateJsUnionRootDeclaration()
            is UnionBranchDeclaration ->
                statement.generateJsUnionBranchDeclaration()

            is EnumDeclarationRoot -> "// enum ${statement.typeName} not emitted for JS yet"
            is EnumBranch -> "// enum branch not emitted for JS yet"

            is TypeAST.InternalType -> "/* internal type stub */"
            is TypeAST.Lambda -> "/* lambda type stub */"
            is TypeAST.UserType -> "/* user type stub */"

            is NeedInfo -> createFakeToken().let { "// compiler bug placeholder" }

            is ErrorDomainDeclaration -> "// error domain is not emitted for JS yet"
        }.addIndentationForEachStringJs(indent)
    )
}

private fun StringBuilder.appendJsClassWithConstructor(typeName: String, fields: List<TypeFieldAST>) {
    // Все объявляемые типы в JS всегда экспортируются
    append("export class ", typeName, " {\n")

    // constructor
    append("    constructor(")
    append(fields.joinToString(", ") { it.name })
    append(") {\n")

    // field assignments
    fields.forEach { field ->
        append("        this.", field.name, " = ", field.name, ";\n")
    }

    append("    }\n")
    append("}\n")
}

fun TypeDeclaration.generateJsTypeDeclaration(): String = buildString {
    appendJsClassWithConstructor(typeName, fields)
}

fun UnionRootDeclaration.generateJsUnionRootDeclaration(): String = buildString {
    // Если этот union уже был сгенерирован как ветка с isRoot = true другого union,
    // не генерируем базовый класс повторно, но продолжаем генерировать его ветки
    val skipBaseClass = typeName in JsCodegenContext.generatedAsIsRootBranches
    
    if (!skipBaseClass) {
        appendJsClassWithConstructor(typeName, fields)
    }

    // Для JS-кодогенерации сразу же эмитим и все ветки union-типа
    if (branches.isNotEmpty()) {
        if (!skipBaseClass) {
            append("\n")
        }
        branches
            .forEachIndexed { index, branch ->
                append(branch.generateJsUnionBranchDeclaration())
                if (index != branches.lastIndex) {
                    append("\n")
                }
            }
    }
}

fun UnionBranchDeclaration.generateJsUnionBranchDeclaration(): String = buildString {
    // Если эта ветка является включением другого union (isRoot = true),
    // отмечаем, что этот тип уже был сгенерирован как ветка
    if (isRoot) {
        JsCodegenContext.generatedAsIsRootBranches.add(typeName)
    }
    
    // Базовый union-тип
    val rootDecl = root
    val rootTypeName = rootDecl.typeName

    // Параметры конструктора: сначала поля ветки, потом поля root
    val branchFieldNames = fields.map { it.name }
    val rootFieldNames = rootDecl.fields.map { it.name }

   	// Каждая ветка union-типа тоже экспортируется как отдельный класс
   	append("export class ", typeName, " extends ", rootTypeName, " {\n")
    append("    constructor(")
    append((branchFieldNames + rootFieldNames).joinToString(", "))
    append(") {\n")

    // Вызов super только с полями root (не дублируем инициализацию этих полей в наследнике)
    append("        super(")
    append(rootFieldNames.joinToString(", "))
    append(");\n")

    // Инициализируем только собственные поля ветки
    fields.forEach { field ->
        append("        this.", field.name, " = ", field.name, ";\n")
    }

    append("    }\n")
    append("}\n")
}
