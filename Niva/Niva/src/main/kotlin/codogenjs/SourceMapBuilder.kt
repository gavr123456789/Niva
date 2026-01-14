package main.codogenjs

import java.io.File

/**
 * Строит Source Map V3 для JS-кодогена Niva.
 * 
 * Source Map формат:
 * {
 *   "version": 3,
 *   "file": "output.js",
 *   "sourceRoot": "",
 *   "sources": ["input.niva"],
 *   "names": [],
 *   "mappings": "AAAA,CAAC,CAAC..."
 * }
 * 
 * mappings — это строка Base64 VLQ, описывающая соответствия между позициями в JS и исходниках.
 */
class SourceMapBuilder(private val generatedFileName: String) {
    private val sources = mutableListOf<String>()
    private val sourceToIndex = mutableMapOf<String, Int>()
    private val mappings = mutableListOf<Mapping>()
    
    // Текущая позиция в генерируемом JS файле
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
    
    /**
     * Добавляет mapping между позицией в сгенерированном JS и исходном Niva файле.
     */
    fun addMapping(
        generatedLine: Int,
        generatedColumn: Int,
        sourceFile: File,
        sourceLine: Int,
        sourceColumn: Int = 0
    ) {
        val sourceIndex = getOrAddSource(sourceFile.name)
        mappings.add(
            Mapping(
                generatedLine = generatedLine,
                generatedColumn = generatedColumn,
                sourceIndex = sourceIndex,
                sourceLine = sourceLine,
                sourceColumn = sourceColumn
            )
        )
    }
    
    /**
     * Обновляет текущую позицию в генерируемом файле после добавления текста.
     */
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
    
    /**
     * Генерирует JSON source map в формате V3.
     */
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
     * Кодирует mappings в Base64 VLQ формат.
     * 
     * Каждый сегмент содержит:
     * 1. Колонка в generated файле (относительно предыдущей на той же строке)
     * 2. Индекс source файла (относительно предыдущего)
     * 3. Строка в source файле (относительно предыдущей)
     * 4. Колонка в source файле (относительно предыдущей)
     */
    private fun encodeMappings(): String {
        if (mappings.isEmpty()) return ""
        
        // Сортируем по generated line, потом по generated column
        val sorted = mappings.sortedWith(compareBy({ it.generatedLine }, { it.generatedColumn }))
        
        val result = StringBuilder()
        var prevGeneratedLine = 0
        var prevGeneratedColumn = 0
        var prevSourceIndex = 0
        var prevSourceLine = 0
        var prevSourceColumn = 0
        
        for (mapping in sorted) {
            // Добавляем ; для новых строк
            while (prevGeneratedLine < mapping.generatedLine) {
                result.append(';')
                prevGeneratedLine++
                prevGeneratedColumn = 0
            }
            
            // Разделитель между сегментами на одной строке
            if (result.isNotEmpty() && result.last() != ';') {
                result.append(',')
            }
            
            // Сегмент из 5 значений:
            // 1. generated column (относительно предыдущего на этой строке)
            result.append(encodeVLQ(mapping.generatedColumn - prevGeneratedColumn))
            prevGeneratedColumn = mapping.generatedColumn
            
            // 2. source file index (относительно предыдущего)
            result.append(encodeVLQ(mapping.sourceIndex - prevSourceIndex))
            prevSourceIndex = mapping.sourceIndex
            
            // 3. source line (относительно предыдущего)
            result.append(encodeVLQ(mapping.sourceLine - prevSourceLine))
            prevSourceLine = mapping.sourceLine
            
            // 4. source column (относительно предыдущего)
            result.append(encodeVLQ(mapping.sourceColumn - prevSourceColumn))
            prevSourceColumn = mapping.sourceColumn
        }
        
        return result.toString()
    }
    
    /**
     * Кодирует целое число в Base64 VLQ.
     * Variable-Length Quantity с основанием 64.
     */
    private fun encodeVLQ(value: Int): String {
        val result = StringBuilder()
        
        // Преобразуем в signed VLQ: младший бит = знак
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
