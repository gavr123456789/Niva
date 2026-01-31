package utils

// it uses sortedMaps
const val devMode_JVM_dependant = """
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
       
import java.util.SortedMap
       
// Live Dev Mode

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
        val stackTrace = (Thread.currentThread().stackTrace.drop(2).joinToString(" <- ") { it.methodName.toString() + if (it.moduleName != null) "(" + it.moduleName + ")" else "" })
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

"""
//
//const val oldDevModeThatPrintsIntoComments = """
//    import java.io.BufferedWriter
//    import java.io.FileWriter
//    import java.io.IOException
//
//    import java.util.SortedMap // works on desktop target, but not on native!!!
//
//    fun <T> inlineRepl(x: T, pathToNivaFileAndLine: String, count: Int): T {
//        val q = x.toString()
//        // x/y/z.niva:6 5
//        val content = pathToNivaFileAndLine + "|||" + q + "***" + count
//
//        try {
//            val writer = BufferedWriter(FileWriter(INLINE_REPL, true))
//            writer.append(content)
//            writer.newLine()
//            writer.close()
//        } catch (e: IOException) {
//            println("File error" + e.message)
//        }
//
//        return x
//    }
//"""