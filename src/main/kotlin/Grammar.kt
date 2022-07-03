import com.github.h0tk3y.betterParse.combinators.and
import com.github.h0tk3y.betterParse.combinators.map
import com.github.h0tk3y.betterParse.combinators.or
import com.github.h0tk3y.betterParse.combinators.skip
import com.github.h0tk3y.betterParse.combinators.zeroOrMore
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser

// number  : /-?[0-9]+/ ;                       \
// symbol  : /[a-zA-Z0-9_+\\-*\\/\\\\=<>!&]+/ ; \
// string  : /\"(\\\\.|[^\"])*\"/ ;             \
// comment : /;[^\\r\\n]*/ ;                    \
// sexpr   : '(' <expr>* ')' ;                  \
// qexpr   : '{' <expr>* '}' ;                  \
// expr    : <number>  | <symbol> | <string>    \
//         | <comment> | <sexpr>  | <qexpr>;    \
// lispy   : /^/ <expr>* /$/ ;                  \
class LispGrammar : Grammar<LispVal>() {
    private val lpar by literalToken("(")
    private val rpar by literalToken(")")
    private val clpar by literalToken("{")
    private val crpar by literalToken("}")

    private val num by regexToken("""-?[0-9]+([.][0-9])?""")
    private val sym by regexToken("""[a-zA-Z0-9_+\-*/\\=<>!&]+""")
    private val str by regexToken(""""(\.|[^"])*"""")
    private val comment by regexToken(""";[^\r\n]*""", ignore = true)
    private val whitespace by regexToken("""\s+""", ignore = true)

    private val number by num.map { Num(it.text.toBigDecimal()) }
    private val symbol by sym.map { Symbol(it.text) }
    private val string by str.map { LString(it.text) }
    private val expressions: Parser<List<LispVal>> = zeroOrMore(parser { expr })
    private val sexpr by (skip(lpar) and expressions and skip(rpar)).map { SExpr(it) }
    private val qexpr by (skip(clpar) and expressions and skip(crpar)).map { QExpr(it) }
    private val expr by number or symbol or sexpr or qexpr or string
    private val lispy by expressions.map { SExpr(it) }

    override val rootParser by lispy
}

val lispGrammar = LispGrammar()
