package languageServer

import frontend.resolver.Type
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import main.frontend.meta.createFakeToken2
import main.frontend.parser.types.ast.CollectionAst
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.LiteralExpression
import main.frontend.parser.types.ast.MapCollection
import main.frontend.parser.types.ast.MessageDeclaration
import java.io.File
import java.util.SortedMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator


fun Expression.generateAddDevDataFunCall(b: StringBuilder) {
    if (isInlineRepl) {
        val filePath = "\"${token.file.absolutePath}\""
        val exprName = "\"$this\""
//        val getStackTrace = "(Thread.currentThread().stackTrace.drop(1).joinToString(\" <- \") { \"\${it.methodName}\${if (it.moduleName != null) \"(\${it.moduleName})\" else \"\"}\" })"
        val tokenLine = token.line
        val tokenStart = token.relPos.start
        val tokenEnd = token.relPos.end
        b.append("), $filePath, $exprName, $tokenLine, $tokenStart, $tokenEnd)")
    }
}


fun devModeSetInlineRepl(expr: Expression, resolvingMessageDeclaration: MessageDeclaration?) {
    if (expr !is LiteralExpression && expr !is CollectionAst && expr !is MapCollection) {
        val resolvingMessageDeclaration = resolvingMessageDeclaration
        if (resolvingMessageDeclaration?.pragmas?.isNotEmpty() == true) {
            resolvingMessageDeclaration.pragmas.find { it.name == "debug" }?.also {
                expr.isInlineRepl = true
            }
        }
    }
}

//fun getStackTrace() {
//    val getSTString = "Thread.currentThread().stackTrace.drop(1).joinToString(\" -> \") { \"\${it.methodName}\${if (it.moduleName != null) \"(\${it.moduleName})\" else \"\"}\" }"
//    Thread.currentThread().stackTrace.drop(1).joinToString(" -> ") { "${it.methodName}${if (it.moduleName != null) "(${it.moduleName})" else ""}" }
//}

class DevLiveData (
    val name: String,
    val start: Int, var end: Int,
    val values: MutableList<String>,
    val stackTraces: MutableList<String>
) {
    fun toJson(): JsonObject {
        return buildJsonObject {
            put("name", name)
            put("start", start)
            put("end", end)
            put("values", JsonArray(values.map { JsonPrimitive(it) }))
            put("stackTraces", JsonArray(stackTraces.map { JsonPrimitive(it) }))
        }
    }

    companion object {
        fun fromJson(json: JsonObject): DevLiveData {
            val name = json["name"]?.jsonPrimitive?.content ?: ""
            val start = json["start"]?.jsonPrimitive?.int ?: 0
            val end = json["end"]?.jsonPrimitive?.int ?: 0
            val values = json["values"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList() ?: mutableListOf()
            val stackTraces = json["stackTraces"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList() ?: mutableListOf()

            return DevLiveData(name, start, end, values, stackTraces)
        }
    }
}

class DevModeStore(
    val data: MutableMap<String, SortedMap<Int, MutableList<DevLiveData>>> = mutableMapOf()
) {
    fun <T> add(
        x: T,
        filePath: String,
        name: String,
        line: Int,
        start: Int,
        end: Int,
    ): T {
        NivaDevModeDB.wasDevModeUsed = true
        val stackTrace = (Thread.currentThread().stackTrace.drop(1).dropLast(1).joinToString(" <- ") { it.methodName.toString() + if (it.moduleName != null) "(" + it.moduleName + ")" else "" })
        val value = x.toString()
        val lines = data.getOrPut(filePath) { sortedMapOf() }
        val list = lines.getOrPut(line) { mutableListOf() }

        val existing = list.find { it.name == name && it.start == start && it.end == end }
        if (existing != null) {
            existing.values.add(value)
            existing.stackTraces.add(stackTrace)
        } else {
            val devLiveData = DevLiveData(name, start, end, mutableListOf(value), mutableListOf(stackTrace))
            list.add(devLiveData)
        }
        return x
    }

    fun toJson(): JsonObject {
        return buildJsonObject {
            put("data", buildJsonObject {
                for ((key, sortedMap) in data) {
                    put(key, buildJsonObject {
                        for ((intKey, list) in sortedMap) {
                            put(intKey.toString(), JsonArray(list.map { it.toJson() }))
                        }
                    })
                }
            })
        }
    }

    companion object {
        fun fromJson(json: JsonObject): DevModeStore {
            val dataJson = json["data"]?.jsonObject ?: JsonObject(emptyMap())
            val data = mutableMapOf<String, SortedMap<Int, MutableList<DevLiveData>>>()

            for ((key, nested) in dataJson) {
                val innerMap = sortedMapOf<Int, MutableList<DevLiveData>>()
                val nestedObject = nested.jsonObject
                for ((intKeyStr, listJson) in nestedObject) {
                    val intKey = intKeyStr.toIntOrNull() ?: continue
                    val liveDataList = listJson.jsonArray.map {
                        DevLiveData.fromJson(it.jsonObject)
                    }.toMutableList()
                    innerMap[intKey] = liveDataList
                }
                data[key] = innerMap
            }

            return DevModeStore(data)
        }
    }
}


object NivaDevModeDB {
    val db = DevModeStore()
    var wasDevModeUsed = false
}



fun DevLiveData.toIdentifierExpr(file: File, lineNum: Int): IdentifierExpr {
    val text = buildString {
        append("```niva\n")
        stackTraces.zip(values).forEach { (trace, value) ->
            appendLine(trace)
            appendLine(value)
            appendLine("---")
        }
        if (stackTraces.isNotEmpty()) {
            setLength(length - 4) // remove the last "---\n"
        }
        append("\n```")
    }
    val token =  createFakeToken2(name, lineNum, start, end, file)
    return IdentifierExpr(
        name = name,
        names = listOf(name),
        token = token,
        isType = false,
        type = null
    ).also {
        it.type = Type.UnknownGenericType(text)
    }
}

fun readFromJson(fileName: String): DevModeStore {
    val file = File(fileName)
    val fileContent = file.readText()
    val json = Json.parseToJsonElement(fileContent).jsonObject
    return DevModeStore.fromJson(json)
}