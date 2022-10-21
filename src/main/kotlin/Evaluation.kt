fun eval(lval: LispVal, env: Environment): LispVal =
    when (lval) {
        is SExpr -> evalSexpr(lval, env)
        is Symbol -> env.getOrElse(lval.value) { throw Exception("Error: Unbound symbol '$lval'") }
        else -> lval
    }

fun evalSexpr(sexpr: SExpr, env: Environment): LispVal {
    if (sexpr.values.isEmpty()) {
        return sexpr
    }
    val v = sexpr.values.map { eval(it, env) }
    if (v.size == 1) {
        return v.first()
    }
    val func = v.first()
    val arguments = v.drop(1)
    require(func is Function) { "First element '$func' of S-expression '$sexpr' is not a function" }
    return func(arguments, env)
}
