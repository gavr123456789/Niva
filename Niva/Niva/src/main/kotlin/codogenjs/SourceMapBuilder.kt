package main.codogenjs

import java.io.File


class SourceMapBuilder(private val generatedFileName: String) {
    private val sources = mutableListOf<String>()
    private val sourceToIndex = mutableMapOf<String, Int>()
    private val mappings = mutableListOf<Mapping>()
    
    // current position in generated js file
    var currentGeneratedLine = 0
        private set
    var currentGeneratedColumn = 0
        private set
    
    data class Mapping(
        val generatedLine: Int,
        val generatedColumn: Int,
        val sourceIndex: Int,
        val sourceLine: Int,
        val sourceColumn: Int
    )
    

//    fun addMapping(
//        generatedLine: Int,
//        generatedColumn: Int,
//        sourceFile: File,
//        sourceLine: Int,
//        sourceColumn: Int = 0
//    ) {
//        val sourceIndex = getOrAddSource(sourceFile.name)
//        mappings.add(
//            Mapping(
//                generatedLine = generatedLine,
//                generatedColumn = generatedColumn,
//                sourceIndex = sourceIndex,
//                sourceLine = sourceLine,
//                sourceColumn = sourceColumn
//            )
//        )
//    }
    
    fun advancePosition(text: String) {
        for (ch in text) {
            if (ch == '\n') {
                currentGeneratedLine++
                currentGeneratedColumn = 0
            } else {
                currentGeneratedColumn++
            }
        }
    }
    
    private fun getOrAddSource(sourceName: String): Int {
        return sourceToIndex.getOrPut(sourceName) {
            val index = sources.size
            sources.add(sourceName)
            index
        }
    }
    
    fun toJson(): String {
        val mappingsString = encodeMappings()
        
        val sourcesJson = sources.joinToString(",") { "\"$it\"" }
        
        return """
{
  "version": 3,
  "file": "$generatedFileName",
  "sourceRoot": "",
  "sources": [$sourcesJson],
  "names": [],
  "mappings": "$mappingsString"
}
        """.trimIndent()
    }

    /**
     * Encodes mappings in the Base64 VLQ format.
     *
     * Each segment contains:
     * 1. The column in the generated file (relative to the previous one on the same line)
     * 2. The index of the source file (relative to the previous one)
     * 3. The line in the source file (relative to the previous one)
     * 4. The column in the source file (relative to the previous one)
     */
    private fun encodeMappings(): String {
        if (mappings.isEmpty()) return ""
        
        // Sort by generated line, then by generated column
        val sorted = mappings.sortedWith(compareBy({ it.generatedLine }, { it.generatedColumn }))
        
        val result = StringBuilder()
        var prevGeneratedLine = 0
        var prevGeneratedColumn = 0
        var prevSourceIndex = 0
        var prevSourceLine = 0
        var prevSourceColumn = 0
        
        for (mapping in sorted) {
            // Add ; for new lines
            while (prevGeneratedLine < mapping.generatedLine) {
                result.append(';')
                prevGeneratedLine++
                prevGeneratedColumn = 0
            }
            
            // Add , for new segments on the same line
            if (result.isNotEmpty() && result.last() != ';') {
                result.append(',')
            }
            
            // Segment with 5 values:
            // 1. generated column (relative to the previous one on the same line)
            result.append(encodeVLQ(mapping.generatedColumn - prevGeneratedColumn))
            prevGeneratedColumn = mapping.generatedColumn
            
            // 2. source file index (relative to the previous one)
            result.append(encodeVLQ(mapping.sourceIndex - prevSourceIndex))
            prevSourceIndex = mapping.sourceIndex
            
            // 3. source line (relative to the previous one)
            result.append(encodeVLQ(mapping.sourceLine - prevSourceLine))
            prevSourceLine = mapping.sourceLine
            
            // 4. source column (relative to the previous one)
            result.append(encodeVLQ(mapping.sourceColumn - prevSourceColumn))
            prevSourceColumn = mapping.sourceColumn
        }
        
        return result.toString()
    }
    
    /**
     * Encodes an integer in Base64 VLQ.
     * Variable-Length Quantity with base 64.
     */
    private fun encodeVLQ(value: Int): String {
        val result = StringBuilder()
        
        // Convert to signed VLQ: least significant bit = sign
        var vlq = if (value < 0) ((-value) shl 1) or 1 else value shl 1
        
        do {
            var digit = vlq and 0x1F
            vlq = vlq ushr 5
            
            if (vlq > 0) {
                digit = digit or 0x20 // continuation bit
            }
            
            result.append(base64Char(digit))
        } while (vlq > 0)
        
        return result.toString()
    }
    
    private fun base64Char(value: Int): Char {
        val base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        return base64[value]
    }
}
