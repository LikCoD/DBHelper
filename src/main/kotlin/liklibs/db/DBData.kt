package liklibs.db

import com.beust.klaxon.Klaxon
import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.postgresql.util.PGobject
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface DBData {
    val detectInjectionRegex: Regex
        get() = Regex("['`]")

    val jdbcName: String
    val userRequire: Boolean
    val dbName: String

    fun <T> parseValue(value: T): String
    fun <T> parseResult(value: T, resultClass: KType? = null): Any?

    fun jsonListParser(resultClass: KType, value: String): List<*>? =
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter<List<*>>(Types.newParameterizedType(
            List::class.java, (resultClass.arguments.first().type!!.classifier as KClass<*>).java)
        ).fromJson(value)
}

object PostgresData : DBData {
    override val jdbcName = "postgresql"
    override val userRequire = true
    override val dbName = "Postgres"

    override fun <T> parseValue(value: T): String = when (value) {
        null -> "null"
        is String -> "'${value.replace(Regex("['`]"), "")}'"
        is Iterable<*> -> value.joinToString(prefix = "'[", postfix = "]'") { parseValue(it) }
        is Timestamp -> "TIMESTAMP '$value'"
        is Date -> "DATE '$value'"
        is Time -> "TIME '$value'"
        is Float -> value.toString()
        is Double -> value.toString()
        is Int -> value.toString()
        is Boolean -> value.toString()
        else -> Klaxon().toJsonString(value).replace(detectInjectionRegex, "")
    }

    override fun <T> parseResult(value: T, resultClass: KType?): Any? = when (value) {
        null -> value
        is java.sql.Timestamp -> value.toSQL()
        is java.sql.Date -> value.toSQL()
        is java.sql.Time -> value.toSQL()
        is String -> value
        is PGobject -> {
            if (value.type != "json" || resultClass == null) null
            else jsonListParser(resultClass, value.value!!)
        }
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
        null -> "null"
        is Boolean -> "'${if (value) trueString else falseString}'"
        is String -> "'${value.replace(detectInjectionRegex, "")}'"
        is Iterable<*> -> toJson(value)
        is Timestamp -> "'__ts__${value}__'"
        is Date -> "'__d__${value}__'"
        is Time -> "'__t__${value}__'"
        is Float -> value.toString()
        is Double -> value.toString()
        is Int -> value.toString()
        else -> "'${Klaxon().toJsonString(value).replace(detectInjectionRegex, "")}'"
    }

    override fun <T> parseResult(value: T, resultClass: KType?): Any? = when {
        value == null || value == "null" -> null
        value is String && value.length >= 2 && value.first() == '[' && value.last() == ']' -> fromJson(value)
        value is String && value == trueString -> true
        value is String && value == falseString -> false
        value is String && value.startsWith("__ts") -> Timestamp.fromString(value.drop(6).dropLast(2))
        value is String && value.startsWith("__d") -> Date.fromString(value.drop(5).dropLast(2))
        value is String && value.startsWith("__t") -> Time.fromString(value.drop(5).dropLast(2))
        value is String && value.startsWith("{") && value.endsWith("}") -> jsonListParser(resultClass!!, value)
        value is java.sql.Timestamp -> value.toSQL()
        value is java.sql.Date -> value.toSQL()
        value is java.sql.Time -> value.toSQL()
        else -> value
    }

    private fun fromJson(str: String): Any = try {
        if (str.length == 2) mutableListOf<Any>()
        else Gson().fromJson(str, List::class.java)
    } catch (ex: Exception) {
        str
    }

    private fun <T> toJson(value: T): String = "'${Gson().toJson(value)}'"
}