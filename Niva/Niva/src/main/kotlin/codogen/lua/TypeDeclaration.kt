package main.codogen.lua

import main.frontend.parser.types.ast.*

fun SomeTypeDeclaration.generateLuaTypeDeclaration(): String = buildString {
    // In Lua, types are represented as tables with metatables
    // Create the type table
    append("local $typeName = {}\n")
    append("${typeName}.__index = $typeName\n\n")

    // Create constructor
    append("function ${typeName}:new(")
    // Add constructor parameters based on fields
    append(fields.joinToString(", ") { it.name })
    append(")\n")
    append("  local instance = setmetatable({}, self)\n")

    // Initialize fields
    fields.forEach { field ->
        append("  instance.${field.name} = ${field.name}\n")
    }

    append("  return instance\n")
    append("end\n")
}

fun TypeAliasDeclaration.generateLuaTypeAlias(): String = buildString {
    // In Lua, type aliases are just references to the original type
    append("local $typeName = ")
    append(realTypeAST.toString())
    append(" -- Type alias\n")
}

fun UnionRootDeclaration.generateLuaUnionDeclaration(): String = buildString {
    // In Lua, we'll implement union types as tables with a type discriminator
    // Create the base type table
    append("local $typeName = {}\n")
    append("${typeName}.__index = $typeName\n\n")

    // Generate constructors for each branch
    branches.forEach { branch ->
        append("function ${typeName}:new_${branch.typeName}(")
        append(branch.fields.joinToString(", ") { it.name })
        append(")\n")
        append("  local instance = setmetatable({}, self)\n")
        append("  instance._type = '${branch.typeName}'\n")

        // Initialize fields
        branch.fields.forEach { field ->
            append("  instance.${field.name} = ${field.name}\n")
        }

        append("  return instance\n")
        append("end\n\n")
    }
}

