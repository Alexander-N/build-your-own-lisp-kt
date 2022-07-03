import com.github.h0tk3y.betterParse.grammar.parseToEnd

fun main() {
    println("Lispy Version 0.0.0.0.1")
    val env = getInitialEnvironment()
    loadPrelude(lispGrammar, env)

    var input: String
    while (true) {
        print("lispy> ")
        input = readln()
        try {
            val ast = lispGrammar.parseToEnd(input)
            val result = eval(ast, env)
            println(result)
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}

