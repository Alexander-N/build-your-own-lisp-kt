import java.math.BigDecimal

sealed interface LispVal

@JvmInline
value class Num(val value: BigDecimal) : LispVal {
    override fun toString(): String = value.toString()
}

@JvmInline
value class LString(private val value: String) : LispVal {
    override fun toString(): String = value
}

data class SExpr(val values: List<LispVal>) : LispVal {
    override fun toString(): String =
        values.joinToString(separator = " ", prefix = "(", postfix = ")")
}

data class QExpr(val values: List<LispVal>) : LispVal {
    override fun toString(): String =
        values.joinToString(separator = " ", prefix = "{", postfix = "}")
}

data class Symbol(val value: String) : LispVal {
    override fun toString(): String = value
}

sealed interface Function : LispVal {
    operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal
}

fun <E : LispVal> List<E>.toNumbers(): List<Num> {
    return try {
        this.map { it as Num }
    } catch (e: ClassCastException) {
        throw Exception("Arguments can only be numbers, but was $this")
    }
}

class Environment(
    val parent: Environment? = null
) : HashMap<String, LispVal>() {
    override fun get(key: String): LispVal? {
        val value = super.get(key)
        if (value != null) return value
        return parent?.get(key)
    }

    fun copy(parent: Environment? = this.parent): Environment {
        return this.toEnvironment(parent = parent)
    }
}

fun <V : LispVal> HashMap<String, V>.toEnvironment(parent: Environment? = null): Environment {
    val env = Environment(parent = parent)
    for ((key, value) in this) {
        env[key] = value
    }
    return env
}

class LambdaFunction(
    private val parameters: QExpr,
    private val body: QExpr,
    private val environment: Environment,
) : Function {
    override fun toString(): String = "(\\ $parameters $body)"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal {
        // Setting the environment of the caller as parent is dynamic scoping!
        val invocationEnv = environment.copy(parent = env)

        val remainingParams = parameters.values.toMutableList()
        val remainingArguments = arguments.toMutableList()
        while (remainingArguments.isNotEmpty()) {
            require(remainingParams.isNotEmpty()) {
                "Function got passed too many arguments. Got $arguments, Expected $parameters."
            }
            val symbol = remainingParams.removeFirst().toString()
            if (symbol == "&") {
                require(remainingParams.size == 1) {
                    "Function format invalid. Symbol '&' not followed by single symbol."
                }
                val varArgs = remainingParams.removeFirst().toString()
                invocationEnv[varArgs] = LList(remainingArguments, env)
                break
            }
            invocationEnv[symbol] = remainingArguments.removeFirst()
        }

        return if (remainingParams.isEmpty()) {
            // Evaluate function if all parameters have been bound
            Eval(listOf(body), invocationEnv)
        } else {
            // Otherwise, return partially evaluated function
            val params = QExpr(values = remainingParams)
            LambdaFunction(params, body, invocationEnv.copy())
        }
    }
}

val zero = Num(BigDecimal(0))
val one = Num(BigDecimal(1))
fun Boolean.toNum(): Num =
    if (this) one
    else zero
