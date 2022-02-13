package liklibs.db

import java.sql.Date
import java.sql.Time
import java.sql.Timestamp

interface DBData{
    val detectInjectionRegex: Regex
        get() = Regex("['`]")

    val jdbcName: String
    val userRequire: Boolean

    fun <T> parseValue(value: T): String
    fun <T> parseResult(value: T): Any?
}

object PostgresData: DBData{
    override val jdbcName = "postgresql"
    override val userRequire = true

    override fun <T> parseValue(value: T): String = when (value) {
        is String -> "'${value.replace(Regex("['`]"), "")}'"
        is Iterable<*> -> value.joinToString(prefix = "{", postfix = "}") { parseValue(it) }
        is Timestamp -> "TIMESTAMP '$value'"
        is Date -> "DATE '$value'"
        is Time -> "TIME '$value'"
        else -> value.toString().replace(Regex("['`]"), "")
    }

    override fun <T> parseResult(value: T): Any? = when (value) {
        null -> value
        is Timestamp -> value.toSQL()
        is Date -> value.toSQL()
        is Time -> value.toSQL()
        else -> value
    }
}

object SQLiteData: DBData{
    override val jdbcName = "sqlite"
    override val userRequire = false

    override fun <T> parseValue(value: T): String = when (value) {
        is String -> "'${value.replace(detectInjectionRegex, "")}'"
        is Iterable<*> -> TODO()
        is Timestamp -> "TIMESTAMP '$value'"
        is Date -> "DATE '$value'"
        is Time -> "TIME '$value'"
        else -> value.toString().replace(detectInjectionRegex, "")
    }

    override fun <T> parseResult(value: T): Any? = when {
        value == null -> null
        value is String && value.length > 2 && value.first() == '{' && value.last() == '}' -> parseJson(value)
        value is Timestamp -> value.toSQL()
        value is Date -> value.toSQL()
        value is Time -> value.toSQL()
        else -> value
    }

    private fun parseJson(str: String){
        TODO()
    }
}