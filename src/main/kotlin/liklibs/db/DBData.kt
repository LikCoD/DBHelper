package liklibs.db

import com.google.gson.Gson

interface DBData {
    val detectInjectionRegex: Regex
        get() = Regex("['`]")

    val jdbcName: String
    val userRequire: Boolean
    val dbName: String

    fun <T> parseValue(value: T): String
    fun <T> parseResult(value: T): Any?
}

object PostgresData : DBData {
    override val jdbcName = "postgresql"
    override val userRequire = true
    override val dbName = "Postgres"

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
    override val dbName = "SQLite"

    private const val trueString = "__true__"
    private const val falseString = "__false__"

    override fun <T> parseValue(value: T): String = when (value) {
        is Boolean -> "'${if (value) trueString else falseString}'"
        is String -> "'${value.replace(detectInjectionRegex, "")}'"
        is Iterable<*> -> toJson(value)
        is Timestamp -> "'__ts__${value}__'"
        is Date -> "'__d__${value}__'"
        is Time -> "'__t__${value}__'"
        else -> value.toString().replace(detectInjectionRegex, "")
    }

    override fun <T> parseResult(value: T): Any? = when {
        value == null -> null
        value is String && value.length >= 2 && value.first() == '[' && value.last() == ']' -> fromJson(value)
        value is String && value == trueString -> true
        value is String && value == falseString -> false
        value is String && value.startsWith("__ts") -> Timestamp().fromString(value.drop(6).dropLast(2))
        value is String && value.startsWith("__d") -> Date().fromString(value.drop(5).dropLast(2))
        value is String && value.startsWith("__t") -> Time().fromString(value.drop(5).dropLast(2))
        value is java.sql.Timestamp -> value.toSQL()
        value is java.sql.Date -> value.toSQL()
        value is java.sql.Time -> value.toSQL()
        else -> value
    }

    private fun fromJson(str: String): Any = try {
        if (str.length == 2) emptyList<Int>()
        else Gson().fromJson(str, List::class.java)
    } catch (ex: Exception) {
        str
    }

    private fun <T> toJson(value: T): String = "'${Gson().toJson(value)}'"
}