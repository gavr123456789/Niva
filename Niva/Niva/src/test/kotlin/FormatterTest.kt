import main.formatter.formatNivaSource
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatterTest {
    @Test
    fun alignsAdjacentSingleKeywords() {
        val source = """
            type Person
              name: String
                      age: Int
        """.trimIndent()

        val expected = """
            type Person
              name: String
               age: Int
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun alignsMultipleKeywordsOnAdjacentLines() {
        val source = """
            1 from: 2 to: 3
            and: 4 orMaybe: 5
        """.trimIndent()

        val expected = """
            1 from: 2      to: 3
               and: 4 orMaybe: 5
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun emptyLineBreaksFormattingGroup() {
        val source = """
            1 from: 2 to: 3

            and: 4 orMaybe: 5
        """.trimIndent()

        assertEquals(source, formatNivaSource(source))
    }

    @Test
    fun splitsContinuationKeywordsWhenPreviousLineHasOneKeyword() {
        val source = """
            1 from: abcd
              to: ccc and: dddddddd
        """.trimIndent()

        val expected = """
            1 from: abcd
                to: ccc
               and: dddddddd
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun ignoresNestedKeywordsInsideParentheses() {
        val source = """
            1 from: abcd
              to: (1 from: 2 to: 3) and: dddddddd
        """.trimIndent()

        val expected = """
            1 from: abcd
                to: (1 from: 2 to: 3)
               and: dddddddd
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun alignsContinuationKeywordColonsToReceiverKeywordColon() {
        val source = """
            {1 2 3 4} map: [it * 2],
            filter: [it % 2 == 0], forEach: [it echo]
        """.trimIndent()

        val expected = """
            {1 2 3 4} map: [it * 2],
                   filter: [it % 2 == 0],
                  forEach: [it echo]
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun receiverKeywordLineBreaksFormattingGroupWithoutComma() {
        val source = """
            reason = ParameterReason name: (impl generics at: i) index: i span: at
            ctx newTyVarForReason: reason
        """.trimIndent()

        assertEquals(source, formatNivaSource(source))
    }

    @Test
    fun alignsAdjacentUnaryMessageToPreviousMessageStart() {
        val source = """
            1 > 2 then: [1]
              else: [3],
              echo
        """.trimIndent()

        val expected = """
            1 > 2 then: [1]
                  else: [3],
                  echo
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun alignsUnaryMessagesWithComma() {
        val source = """
            1 > 2 then: [1]
                  else: [3],
              echo,
              echo
        """.trimIndent()

        val expected = """
            1 > 2 then: [1]
                  else: [3],
                  echo,
                  echo
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun emptyLineBreaksUnaryFormattingGroup() {
        val source = """
            1 > 2 then: [1]
                  else: [3],

              echo
        """.trimIndent()

        assertEquals(source, formatNivaSource(source))
    }

    @Test
    fun alignsCommaUnaryMessagesToPreviousMessageStart() {
        val source = """
            1 inc,
            decc,
            inc,
            dec
        """.trimIndent()

        val expected = """
            1 inc,
              decc,
              inc,
              dec
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun caretBreaksFormattingGroup() {
        val source = """
            1 from: 2 ^
            and: 3
        """.trimIndent()

        assertEquals(source, formatNivaSource(source))
    }

    @Test
    fun alignsBarsBeforeArrows() {
        val source = """
            | this
              | Circle => []
            | Rect => []
        """.trimIndent()

        val expected = """
            | this
            | Circle => []
            | Rect   => []
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun alignsBarsWithResetLine() {
        val source = """
            ^|.
                | StructTy => []
                | ClassTy => []
        """.trimIndent()

        val expected = """
            ^|.
             | StructTy => []
             | ClassTy => []
        """.trimIndent()

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun alignsResetBranchesWithElseBar() {
        val source = listOf(
            "    ^|.",
            "    | StructTy => []",
            "  | SizedArray => []",
            "      |=> []",
        ).joinToString("\n")

        val expected = listOf(
            "    ^ |.",
            "      | StructTy   => []",
            "      | SizedArray => []",
            "      |=> []",
        ).joinToString("\n")

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun alignsBranchBarsToInlineBar() {
        val source = listOf(
            "opt = TraitOption",
            "  impl_id: v_id",
            "     args: (v_args mapIndexed: [i, it -> |it",
            "        |null => [",
            "            reason = ParameterReason name: (impl generics at: i) index: i span: at",
            "            ctx newTyVarForReason: reason",
            "        ]",
            "        |=> it",
            "    ])",
        ).joinToString("\n")

        val expected = listOf(
            "opt = TraitOption",
            "  impl_id: v_id",
            "     args: (v_args mapIndexed: [i, it -> |it",
            "                                         |null => [",
            "                                             reason = ParameterReason name: (impl generics at: i) index: i span: at",
            "                                             ctx newTyVarForReason: reason",
            "                                         ]",
            "                                         |=> it",
            "    ])",
        ).joinToString("\n")

        assertEquals(expected, formatNivaSource(source))
    }

    @Test
    fun literalsAreIgnoredAsUnaryMessages() {
        val source = """
            1 inc,
            true
            false,
            nil
        """.trimIndent()

        assertEquals(source, formatNivaSource(source))
    }

    @Test
    fun literalsDoNotBecomeUnaryAnchors() {
        val source = """
            true
              echo
        """.trimIndent()

        assertEquals(source, formatNivaSource(source))
    }
}
