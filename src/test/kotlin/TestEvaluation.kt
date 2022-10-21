import com.github.h0tk3y.betterParse.grammar.parseToEnd
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.MathContext.DECIMAL128

class TestEvaluation : DescribeSpec({
    describe("eval") {
        val lispGrammar = LispGrammar()
        it("should return the correct results for arithmetics") {
            val twentyDiv6 = BigDecimal(20).divide(BigDecimal(6), DECIMAL128)
            val env = getInitialEnvironment()
            withData(
                "+ 1 2 6" to 9,
                "- 4 1 1" to 2,
                "+ 6 (* 2 9)" to 24,
                "/ (* 10 2) (+ 4 2)" to twentyDiv6,
                "+ 1 ( / 4 (* 4 0.5))" to 3,
                "(+ 1 ( / 4 (* 4 0.5)))" to 3,
                "(3)" to 3,
                "- 1" to -1,
                "+ 1" to 1,
                "* 1" to 1,
                "/ 1" to 1,
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env) shouldBe Num(expected.toString().toBigDecimal())
            }
        }
        it("should throw an exception when grammar is valid but evaluation is not possible") {
            val env = getInitialEnvironment()
            withData(
                "(3 3)",
                "/ 1 0",
                "+ 1 2 list",
                "join 1 2",
                "join {1} 2",
                "hello",
            ) { input ->
                val ast = lispGrammar.parseToEnd(input)
                shouldThrow<Exception> {
                    eval(ast, env)
                }
            }
        }
        it("should evaluate symbols and numbers as themselves") {
            val env = getInitialEnvironment()
            withData(
                "1" to "1",
                "+" to "+",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should evaluate Q-Expressions correctly") {
            val env = getInitialEnvironment()
            withData(
                "{1 2 3 4}" to "{1 2 3 4}",
                "{+ 4 5}" to "{+ 4 5}",
                "{1 2 (+ 5 6) 4}" to "{1 2 (+ 5 6) 4}",
                "{{2 3 4} {1}}" to "{{2 3 4} {1}}",
                "list 1 2 3 4" to "{1 2 3 4}",
                "{head (list 1 2 3 4)}" to "{head (list 1 2 3 4)}",
                "eval {head (list 1 2 3 4)}" to "{1}",
                "tail {tail tail tail}" to "{tail tail}",
                "eval (tail {tail tail {5 6 7}})" to "{6 7}",
                "eval (head {(+ 1 2) (+ 10 20)})" to "3",
                "join {1} {2}" to "{1 2}",
                "join {1 2} {3 4 5} {6 7}" to "{1 2 3 4 5 6 7}",
                "eval (head {5 10 11 15})" to "5",
                "(eval (head {+ - + - * /})) 10 20" to "30",
                "eval (head {tail - + - * /})" to "tail",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should be possible to define variables") {
            val env = getInitialEnvironment()
            withData(
                "def {x} 100" to "()",
                "def {y} 200" to "()",
                "x" to "100",
                "y" to "200",
                "+ x y" to "300",
                "def {a b} 5 6" to "()",
                "+ a b" to "11",
                "def {arglist} {a b x y}" to "()",
                "arglist" to "{a b x y}",
                "def arglist 1 2 3 4" to "()",
                "list a b x y" to "{1 2 3 4}",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should be possible to use lambda functions") {
            val env = getInitialEnvironment()
            withData(
                "(\\ {x y} {+ x y}) 10 20" to "30",
                "def {add-together} (\\ {x y} {+ x y})" to "()",
                "add-together 10 20" to "30",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("`def` defines variables in the global environment") {
            val env = getInitialEnvironment()
            withData(
                "(\\ {_} {def {x} 100}) {}" to "()",
                "x" to "100",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("`=` defines variables in the local environment") {
            val env = getInitialEnvironment()
            val (input, expected) = "(\\ {_} {= {x} 100}) {}" to "()"
            var ast = lispGrammar.parseToEnd(input)
            eval(ast, env).toString() shouldBe expected
            ast = lispGrammar.parseToEnd("x")
            shouldThrow<Exception> {
                eval(ast, env)
            }
        }
        it("should be possible to add a simpler syntax to define functions") {
            val env = getInitialEnvironment()
            withData(
                "def {fun} (\\ {args body} {def (head args) (\\ (tail args) body)})" to "()",
                "fun {add-together x y} {+ x y}" to "()",
                "add-together 2 3" to "5"
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("partial evaluation should work") {
            val env = getInitialEnvironment()
            withData(
                "def {add-mul} (\\ {x y} {+ x (* x y)})" to "()",
                "add-mul 10 20" to "210",
                "add-mul 10" to "(\\ {y} {+ x (* x y)})",
                "def {add-mul-ten} (add-mul 10)" to "()",
                "add-mul-ten 50" to "510",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should support a variable number of arguments") {
            val env = getInitialEnvironment()
            withData(
                "def {var-args} (\\ {& xs} {xs})" to "()",
                "var-args 1" to "{1}",
                "var-args 1 2" to "{1 2}",
                "var-args 1 2 3" to "{1 2 3}",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("currying and uncurrying should work") {
            val env = getInitialEnvironment()
            withData(
                "def {fun} (\\ {args body} {def (head args) (\\ (tail args) body)})" to "()",
                "fun {unpack f xs} {eval (join (list f) xs)}" to "()",
                "fun {pack f & xs} {f xs}" to "()",
                "def {uncurry} pack" to "()",
                "def {curry} unpack" to "()",
                "curry + {5 6 7}" to "18",
                "uncurry head 5 6 7" to "{5}",
                "def {add-uncurried} +" to "()",
                "def {add-curried} (curry +)" to "()",
                "add-curried {5 6 7}" to "18",
                "add-uncurried 5 6 7" to "18",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should have comparisons and conditionals") {
            val env = getInitialEnvironment()
            withData(
                "> 10 5" to "1",
                "< 10 5" to "0",
                "<= 88 5" to "0",
                ">= 88 5" to "1",
                "== 5 6" to "0",
                "== 5 {}" to "0",
                "== 1 1" to "1",
                "!= {} 56" to "1",
                "== {1 2 3 {5 6}} {1   2  3   {5 6}}" to "1",
                "def {x y} 100 200" to "()",
                "if (== x y) {+ x y} {- x y}" to "-100",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should be possible to define `len` recursively") {
            val env = getInitialEnvironment()
            withData(
                "def {fun} (\\ {args body} {def (head args) (\\ (tail args) body)})" to "()",
                """fun {len l} {
                    if (== l {})
                    {0}
                    {+ 1 (len (tail l))}
                }""" to "()",
                "len {}" to "0",
                "len {1 2 3 4}" to "4",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should be possible to define `reverse` recursively") {
            val env = getInitialEnvironment()
            withData(
                "def {fun} (\\ {args body} {def (head args) (\\ (tail args) body)})" to "()",
                """fun {reverse l} {
                      if (== l {})
                        {{}}
                        {join (reverse (tail l)) (head l)}
                    }""" to "()",
                "reverse {}" to "{}",
                "reverse {1 2 3 4}" to "{4 3 2 1}",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should support strings") {
            val env = getInitialEnvironment()
            withData(
                """ "hello" """ to """"hello"""",
                """ "hello\n" """ to """"hello\n"""",
                """ head {"hello" "world"} """ to """{"hello"}""",
                """ eval (head {"hello" "world"}) """ to """"hello""""
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should ignore comments") {
            val env = getInitialEnvironment()
            withData(
                ";3" to "()",
                "; + 2 3" to "()",
                "; Just ignore me" to "()",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should have a printfunction") {
            val env = getInitialEnvironment()
            withData(
                "print \"Hello World!\"" to "()",
                "print \"many args\" {1 2 3}" to "()",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("should throw an exception when calling the error function") {
            val env = getInitialEnvironment()
            val input = "error \"This is an error\""
            val ast = lispGrammar.parseToEnd(input)
            shouldThrow<Exception> {
                eval(ast, env)
            }
        }
        it("should have a working standard library from the prelude") {
            val env = getInitialEnvironment()
            loadPrelude(lispGrammar, env)
            withData(
                "nil" to "{}",
                "true" to "1",
                "false" to "0",
                "(flip def) 1 {x}" to "()",
                "x" to "1",
                "def {define-one} ((flip def) 1)" to "()",
                "define-one {y}" to "()",
                "y" to "1",
                "ghost + 2 2" to "4",
                "(unpack *) {2 2}" to "4",
                "- ((unpack *) {2 2})" to "-4",
                "comp - (unpack *)" to "(\\ {x} {f (g x)})",
                "def {mul-neg} (comp - (unpack *))" to "()",
                "mul-neg {2 8}" to "-16",
                "map - {5 6 7 8 2 22 44}" to "{-5 -6 -7 -8 -2 -22 -44}",
                "map (\\ {x} {+ x 10}) {5 2 11}" to "{15 12 21}",
                "print {\"hello\" \"world\"}" to "()",
                "map print {\"hello\" \"world\"}" to "{() ()}",
                "filter (\\ {x} {> x 2}) {5 2 11 -7 8 1}" to "{5 11 8}",
                "sum {2 2 -2}" to "2",
                "product {2 2 -2}" to "-8",
                "not true" to "0",
                "and true false" to "0",
                "or true false" to "1",
                "min 100 -2 4" to "-2",
                "max 100 -2 4" to "100",
                "fst {1 2 3 4}" to "1",
                "snd {1 2 3 4}" to "2",
                "trd {1 2 3 4}" to "3",
                "len {1 2 3 4}" to "4",
                "nth 1 {1 2 3 4}" to "2",
                "init {1 2 3 4}" to "{1 2 3}",
                "last {1 2 3 4}" to "4",
                "take 2 {1 2 3 4}" to "{1 2}",
                "drop 2 {1 2 3 4}" to "{3 4}",
                "split 2 {1 2 3 4}" to "{{1 2} {3 4}}",
                "== (tail {}) nil" to "1",
                "fun {equal-three x} {(== x 3)}" to "()",
                "equal-three 2" to "0",
                "equal-three 3" to "1",
                "map equal-three {1 2 3 4}" to "{0 0 1 0}",
                "filter equal-three {1 2 3 4 3}" to "{3 3}",
                "take-while equal-three {3 2 3 4}" to "{3}",
                "drop-while equal-three {3 2 3 4}" to "{2 3 4}",
                "elem 3 {1 2 3 4}" to "1",
                "elem 5 {1 2 3 4}" to "0",
                "zip {1 2 3} {4 5 6}" to "{{1 4} {2 5} {3 6}}",
                "unzip {{1 4} {2 5} {3 6}}" to "{{1 2 3} {4 5 6}}",
                """select
                    {false  "no"}
                    {true  "match 1"}
                    {true "match 2"}
                    {false  "no"}
                """ to "\"match 1\"",
                """fun {month-day-suffix i} {
                      select
                        {(== i 1)  "st"}
                        {(== i 2)  "nd"}
                        {(== i 3)  "rd"}
                        {otherwise "th"}
                }""" to "()",
                "month-day-suffix 1" to "\"st\"",
                "month-day-suffix 3" to "\"rd\"",
                "month-day-suffix 30" to "\"th\"",
                """fun {day-name x} {
                      case x
                        {0 "Monday"}
                        {1 "Tuesday"}
                        {2 "Wednesday"}
                        {3 "Thursday"}
                        {4 "Friday"}
                        {5 "Saturday"}
                        {6 "Sunday"}
                    }""" to "()",
                "day-name 3" to "\"Thursday\"",
                "fib 10" to "55",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("let in conjunction with do does not leak variables out of their scope") {
            val env = getInitialEnvironment()
            loadPrelude(lispGrammar, env)
            withData(
                "let {do (= {x} 100) (x)}" to "100",
                "x" to "Error: Unbound symbol 'x'"
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                if (expected.startsWith("Error:")) {
                    val exception = shouldThrow<Exception> {
                        eval(ast, env)
                    }
                    exception.message shouldBe expected
                } else {
                    eval(ast, env).toString() shouldBe expected
                }
            }
        }
        it("uses dynamic scoping") {
            val env = getInitialEnvironment()
            loadPrelude(lispGrammar, env)
            withData(
                "def {x} 100" to "()",
                "x" to "100",
                "fun {get-x _} {x}" to "()",
                "get-x {}" to "100",
                "let {do (= {x} 20) (x)}" to "20",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                eval(ast, env).toString() shouldBe expected
            }
        }
        it("can load a file with code") {
            val codeFile = tempfile()
            codeFile.writeText("(fun {add x y} {+ x y})")

            val env = getInitialEnvironment()
            loadPrelude(lispGrammar, env)
            withData(
                "add 2 3" to "Error: Unbound symbol 'add'",
                """load "${codeFile.path}"""" to "()",
                "add 2 3" to "5",
            ) { (input, expected) ->
                val ast = lispGrammar.parseToEnd(input)
                if (expected.startsWith("Error:")) {
                    val exception = shouldThrow<Exception> {
                        eval(ast, env)
                    }
                    exception.message shouldBe expected
                } else {
                    eval(ast, env).toString() shouldBe expected
                }
            }
        }
    }
})
