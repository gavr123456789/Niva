@file:Suppress("unused")

import frontend.resolver.Resolver
import frontend.resolver.resolve
import main.codogen.lua.codegenLua
import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File
import main.test.getAstTest

fun generateLua(source: String): String {
    val ast = getAstTest(source)
    val resolver = Resolver(
        projectName = "common",
        statements = ast.toMutableList(),
        currentResolvingFileName = File("")
    )
    resolver.resolve(resolver.statements, mutableMapOf())
    return codegenLua(resolver.statements)
}

fun generateLuaWithoutResolve(source: String): String {
    val ast = getAstTest(source)
    val resolver = Resolver(
        projectName = "common",
        statements = ast.toMutableList(),
        currentResolvingFileName = File("")
    )
    return codegenLua(resolver.statements)
}

class LuaCodogenTest {
    @Test
    fun testBasicLiterals() {
        assertEquals("""
            local x = 42
            local s = "hello"
            local b = true
        """.trimIndent(),
            generateLua("""
                x = 42
                s = "hello"
                b = true
            """.trimIndent())
        )
    }

    @Test
    fun testControlFlow() {
        assertEquals(
            """
                local x = 10
                if x > 0 then
                  return x
                else
                  return 0 - x
                end"""
            .trimIndent(),
            generateLua("""
                x = 10
                (x > 0) ifTrue: [x] ifFalse: [0 - x]""".trimIndent())
        )
    }

    @Test
    fun testTypeDeclaration() {
        assertEquals(
            """
                local Point = {}
                Point.__index = Point
                
                function Point:new(x, y)
                  local instance = setmetatable({}, self)
                  instance.x = x
                  instance.y = y
                  return instance
                end
            """.trimIndent(),
            generateLuaWithoutResolve("""
                type Point x: Int y: Int""".trimIndent())
        )
    }

    @Test
    fun testBinary() {
        assertEquals(
            """
                1 + 2 + 3 + 4
            """.trimIndent(),
            generateLuaWithoutResolve("""
                1 + 2 + 3 + 4
            """.trimIndent())
        )
    }

    @Test
    fun testMethodDeclaration() {
        assertEquals(
            """local Point = {}
Point.__index = Point

function Point:new(x, y)
  local instance = setmetatable({}, self)
  instance.x = x
  instance.y = y
  return instance
end

function Point:distance(other)
  local dx = self.x - other.x
  local dy = self.y - other.y
  return dx * dx + dy * dy
end""".trimIndent(),
            generateLuaWithoutResolve("""
                type Point x: Int y: Int
                extend Point [
                    on distance: other::Point = [
                        dx = x - other x
                        dy = y - other y
                        ^ dx * dx + dy * dy
                    ]
                ]""".trimIndent())
        )
    }

    @Test
    fun testUnionType() {
        assertEquals(
            """local Shape = {}
Shape.__index = Shape

function Shape:new_Circle(radius)
  local instance = setmetatable({}, self)
  instance._type = 'Circle'
  instance.radius = radius
  return instance
end

function Shape:new_Rectangle(width, height)
  local instance = setmetatable({}, self)
  instance._type = 'Rectangle'
  instance.width = width
  instance.height = height
  return instance
end""".trimIndent(),
            generateLuaWithoutResolve("""
                union Shape = Circle radius: Float | Rectangle width: Float height: Float""".trimIndent())
        )
    }
}
