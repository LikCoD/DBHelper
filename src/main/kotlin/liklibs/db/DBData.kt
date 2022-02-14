package liklibs.db

import com.google.gson.Gson

interface DBData {
    val detectInjectionRegex: Regex
        get() = Regex("['`]")

    val jdbcName: String
    val userRequire: Boolean

    fun <T> parseValue(value: T): String
    fun <T> parseResult(value: T): Any?
}

object PostgresData : DBData {
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
        is java.sql.Timestamp -> value.toSQL()
        is java.sql.Date -> value.toSQL()
        is java.sql.Time -> value.toSQL()
        else -> value
    }
}

object SQLiteData : DBData {
    override val jdbcName = "sqlite"
    override val userRequire = false

    override fun <T> parseValue(value: T): String = when (value) {
        is String -> "'${value.replace(detectInjectionRegex, "")}'"
        is Iterable<*> -> toJson(value)
        is Timestamp -> "TIMESTAMP '$value'"
        is Date -> "DATE '$value'"
        is Time -> "TIME '$value'"
        else -> value.toString().replace(detectInjectionRegex, "")
    }

    override fun <T> parseResult(value: T): Any? = when {
        value == null -> null
        value is String && value.length > 2 && value.first() == '{' && value.last() == '}' -> fromJson(value)
        value is java.sql.Timestamp -> value.toSQL()
        value is java.sql.Date -> value.toSQL()
        value is java.sql.Time -> value.toSQL()
        else -> value
    }

    fun fromJson(str: String): Any = try {
        Gson().fromJson(str, List::class.java)
    } catch (ex: Exception) {
        str
    }

    fun <T> toJson(value: T): String {
        return Gson().toJson(value)
    }
}