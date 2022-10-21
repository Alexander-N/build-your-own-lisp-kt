import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.ParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData

val lispGrammar = LispGrammar()

class GrammarTests : DescribeSpec({
    describe("Lisp Grammar") {
        it("should parse valid inputs") {
            withData(
                "+ 1 2 6",
                "+ 6 (* 2 9)",
                "/ (* 10 2) (+ 4 2)",
                "- 2          (+ 1 1)",
                "+ 1.7 1.566",
                "(+ 1 1)",
                "/ 2",
                "(+ 1 ( / 4 (* 4 0.5)))",
                "()",
                "(3 4)",
                "3 4",
                "3",
                "list 1 2 3 4",
                "{head (list 1 2 3 4)}",
                "eval {head (list 1 2 3 4)}",
                "tail {tail tail tail}",
                "eval (tail {tail tail {5 6 7}})",
                "eval (head {(+ 1 2) (+ 10 20)})",
                "hello",
                "1dog",
                """
                    "\""
                """.trimIndent(),
            ) { input: String ->
                lispGrammar.parseToEnd(input)
            }
        }
        it("should reject invalid inputs") {
            withData(
                "{hello",
                "1dog)",
                "- 2 (+ 1 3",
                "+ 1 .4",
            ) { input: String ->
                shouldThrow<ParseException> {
                    lispGrammar.parseToEnd(input)
                }
            }
        }
    }
})
