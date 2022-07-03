import java.io.File
import java.math.BigDecimal
import java.math.MathContext

private fun getQexpr(values: List<LispVal>): QExpr {
    require(values.size == 1) { "Arguments can only be one element but was: $values" }
    val qexpr = values.first()
    require(qexpr is QExpr) { "Argument has to be an QExpr but was: $qexpr" }
    return qexpr
}

object Plus : Function {
    override fun toString() = "+"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        return arguments.toNumbers().reduce { x, y -> Num(x.value + y.value) }
    }
}

object Minus : Function {
    override fun toString() = "-"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        return arguments
            .toNumbers()
            .let {
                if (it.size == 1) listOf(zero) + it
                else it
            }.reduce { x, y -> Num(x.value - y.value) }
    }
}

object Mul : Function {
    override fun toString() = "*"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        return arguments.toNumbers().reduce { x, y -> Num(x.value * y.value) }
    }
}

object Div : Function {
    override fun toString() = "/"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        return arguments.toNumbers().reduce { x, y ->
            Num(x.value.divide(y.value, MathContext.DECIMAL128))
        }
    }
}

object Head : Function {
    override fun toString() = "head"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): QExpr {
        val qexpr = getQexpr(arguments)
        return QExpr(qexpr.values.take(1))
    }
}

object Tail : Function {
    override fun toString() = "tail"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): QExpr {
        val qexpr = getQexpr(arguments)
        return QExpr(qexpr.values.drop(1))
    }
}

object LList : Function {
    override fun toString() = "list"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): QExpr = QExpr(arguments)
}

object Eval : Function {
    override fun toString() = "eval"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal {
        val qexpr = getQexpr(arguments)
        return eval(SExpr(qexpr.values), env)
    }
}

object Join : Function {
    override fun toString() = "join"
    override fun invoke(arguments: List<LispVal>, env: Environment): QExpr {
        val qexprs = try {
            arguments.map { it as QExpr }
        } catch (e: ClassCastException) {
            throw Exception("Arguments to 'join' can only be Q-Expressions, but was $arguments")
        }
        return QExpr(qexprs.flatMap { it.values })
    }
}

object Put : Function {
    override fun toString() = "="
    override operator fun invoke(arguments: List<LispVal>, env: Environment): SExpr {
        require(arguments.size >= 2) { "`$this` needs at least two arguments" }
        val qexpr = arguments.first()
        require(qexpr is QExpr) { "First argument to `$this` has to be an QExpr but was: $qexpr" }
        val symbols = try {
            qexpr.values.map { it as Symbol }
        } catch (e: ClassCastException) {
            throw Exception("`$this` cannot define non-symbol")
        }
        val valuesToAssign = arguments.drop(1)
        require(symbols.size == valuesToAssign.size) {
            "Number of symbol and values needs to be the same"
        }
        symbols.zip(valuesToAssign) { s, v ->
            env.put(s.toString(), v)
        }
        return SExpr(listOf())
    }
}

object Def : Function {
    override fun toString() = "def"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): SExpr {
        var currentEnv = env
        while (currentEnv.parent != null) {
            currentEnv = currentEnv.parent!!
        }
        return Put(arguments, currentEnv)
    }
}

object Lambda : Function {
    override fun toString() = "\\"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal {
        require(arguments.size == 2) { "`$this` takes exactly two arguments but was given $arguments" }

        val (parameters, body) = arguments
        require(parameters is QExpr) { "First argument to `$this` has to be an QExpr but was: '$parameters'" }
        require(body is QExpr) { "Second argument to `$this` has to be an QExpr but was: '$body" }

        val environment = Environment(parent = env)
        return LambdaFunction(parameters, body, environment)
    }
}

object Greater : Function {
    override fun toString() = ">"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        require(arguments.size == 2) { "`$this` takes exactly two arguments but was given $arguments" }
        val (n1, n2) = arguments.toNumbers()
        return (n1.value > n2.value).toNum()
    }
}

object GreaterEq : Function {
    override fun toString() = ">="
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        require(arguments.size == 2) { "`$this` takes exactly two arguments but was given $arguments" }
        val (n1, n2) = arguments.toNumbers()
        return (n1.value >= n2.value).toNum()
    }
}

object Smaller : Function {
    override fun toString() = "<"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        require(arguments.size == 2) { "`$this` takes exactly two arguments but was given $arguments" }
        val (n1, n2) = arguments.toNumbers()
        return (n1.value < n2.value).toNum()
    }
}

object SmallerEq : Function {
    override fun toString() = "<="
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        require(arguments.size == 2) { "`$this` takes exactly two arguments but was given $arguments" }
        val (n1, n2) = arguments.toNumbers()
        return (n1.value <= n2.value).toNum()
    }
}

object Equal : Function {
    override fun toString() = "=="
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        require(arguments.size == 2) { "`$this` takes exactly two arguments but was given $arguments" }
        val (v1, v2) = arguments
        return (v1 == v2).toNum()
    }
}

object NotEqual : Function {
    override fun toString() = "=="
    override operator fun invoke(arguments: List<LispVal>, env: Environment): Num {
        require(arguments.size == 2) { "`$this` takes exactly two arguments but was given $arguments" }
        val (v1, v2) = arguments
        return (v1 != v2).toNum()
    }
}

object If : Function {
    override fun toString() = "if"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal {
        require(arguments.size == 3) { "`$this` takes exactly three arguments but was given $arguments" }
        val (condition, trueBranch, falseBranch) = arguments
        require(condition is Num) { "First argument to `$this` has to be an Num but was: '$condition'" }
        require(trueBranch is QExpr) { "Second argument to `$this` has to be an QExpr but was: '$trueBranch'" }
        require(falseBranch is QExpr) { "Third argument to `$this` has to be an QExpr but was: '$falseBranch'" }

        return when (condition.value) {
            BigDecimal(0) -> Eval(listOf(falseBranch), env)
            else -> Eval(listOf(trueBranch), env)
        }
    }
}

object Print : Function {
    override fun toString() = "print"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal {
        println(arguments.joinToString())
        return SExpr(listOf())
    }
}

object Error : Function {
    override fun toString() = "error"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal {
        require(arguments.size == 1) { "`$this` takes exactly one argument but was given $arguments" }
        val msg = arguments.first()
        require(msg is LString) { "Argument to `$this` has to be an LString but was: '$msg'" }
        throw Exception(msg.toString())
    }
}

object Load : Function {
    override fun toString() = "load"
    override operator fun invoke(arguments: List<LispVal>, env: Environment): LispVal {
        require(arguments.size == 1) { "`$this` takes exactly one argument but was given $arguments" }
        val path = arguments.first()
        require(path is LString) { "Argument to `$this` has to be an LString but was: '$path'" }
        val code = File(path.toString().trim('"')).readText()
        loadCode(code, lispGrammar, env)
        return SExpr(listOf())
    }
}
