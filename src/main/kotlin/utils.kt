import com.github.h0tk3y.betterParse.grammar.parseToEnd

fun getInitialEnvironment(): Environment = hashMapOf(
    "+" to Plus,
    "-" to Minus,
    "*" to Mul,
    "/" to Div,
    "head" to Head,
    "tail" to Tail,
    "list" to LList,
    "eval" to Eval,
    "join" to Join,
    "def" to Def,
    "=" to Put,
    "\\" to Lambda,
    ">" to Greater,
    ">=" to GreaterEq,
    "<" to Smaller,
    "<=" to SmallerEq,
    "==" to Equal,
    "!=" to NotEqual,
    "if" to If,
    "print" to Print,
    "error" to Error,
    "load" to Load,
).toEnvironment()

fun loadCode(code: String, lispGrammar: LispGrammar, env: Environment) {
    val ast = lispGrammar.parseToEnd(code) as SExpr
    for (s in ast.values) {
        try {
            eval(s, env)
        } catch (e: Exception) {
            throw Exception("Error in expression '$s': ${e.message}")
        }
    }
}

fun loadPrelude(lispGrammar: LispGrammar, env: Environment) {
    val prelude = object {}.javaClass.getResource("prelude.lspy")!!.readText()
    try {
        loadCode(prelude, lispGrammar, env)
    } catch (e: Exception) {
        throw Exception("Could not load prelude: ${e.message}")
    }
}
