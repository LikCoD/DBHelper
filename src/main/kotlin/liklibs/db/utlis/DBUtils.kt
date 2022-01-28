package liklibs.db.utlis

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import liklibs.db.DBCredentials
import org.intellij.lang.annotations.Language
import org.postgresql.util.PSQLException
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@ExperimentalSerializationApi
abstract class DBUtils(private val dbName: String, credentialsFileName: String? = null) {
    private lateinit var connection: Connection
    private lateinit var statement: Statement

    var isAvailable = false
    var exception: String? = null

    internal open fun printError(query: String, ex: Exception) {
        println("${ex.message?.replace("\n", " ")}\nQUERY: $query".trim())
    }

    fun execute(@Language("PostgreSQL") query: String) = try {
        statement.execute(query)
    } catch (ex: Exception) {
        printError(query, ex)
        false
    }

    fun executeQuery(@Language("PostgreSQL") query: String): ResultSet? = try {
        statement.executeQuery(query)
    } catch (ex: Exception) {
        printError(query, ex)
        null
    }

    internal open fun <T> parseList(list: Iterable<T>, parseValue: Boolean = true) =
        list.joinToString(prefix = "(", postfix = ")") { if (parseValue) parseValue(it) else it.toString() }

    fun <T> insert(table: String, map: Map<String, T>, onConflict: String = "NOTHING"): Int? {
        val fieldsQuery = parseList(map.keys, false)
        val valuesQuery = parseList(map.values)

        val res =
            executeQuery("INSERT INTO $table $fieldsQuery VALUES $valuesQuery ON CONFLICT DO $onConflict RETURNING _id")

        if (res == null || !res.next()) return null

        return res.getInt("_id")
    }

    fun <T> insert(
        table: String,
        keys: List<String>,
        vararg valuesList: List<T>,
        onConflict: String = "NOTHING",
    ): List<Int?> {
        val fieldsQuery = parseList(keys, false)
        val valuesQuery = valuesList.joinToString(transform = ::parseList)

        val res =
            executeQuery("INSERT INTO $table $fieldsQuery VALUES $valuesQuery ON CONFLICT DO $onConflict RETURNING _id")

        val ids = mutableListOf<Int?>()
        while (res?.next() ?: return emptyList()) {
            ids.add(res.getInt("_id"))
        }

        return ids
    }

    fun select(table: String, fields: String = "*", filter: String? = null) =
        executeQuery("SELECT $fields FROM $table ${if (filter != null) "WHERE $filter" else ""}")

    fun delete(table: String, filter: String) =
        execute("DELETE FROM $table WHERE $filter")

    internal open fun <T> parseValue(value: T): String = when (value) {
        is String -> "'${value.replace(Regex("['`]"), "")}'"
        is Iterable<*> -> value.joinToString(prefix = "{", postfix = "}") { parseValue(it) }
        is LocalDateTime -> "TIMESTAMP '${value.format(DateTimeFormatter.ISO_DATE_TIME)}'"
        is LocalDate -> "DATE '${value.format(DateTimeFormatter.ISO_DATE)}'"
        is LocalTime -> "TIME '${value.format(DateTimeFormatter.ISO_TIME)}'"
        else -> value.toString().replace(Regex("['`]"), "")
    }

    init {
        if (credentialsFileName != null) {
            try {
                Json.decodeFromStream<DBCredentials>(File(credentialsFileName).inputStream()).let {
                    init(it.host, it.user, it.password)
                }
            } catch (ex: Exception) {
                exception = ex.message
            }
        }
    }

    fun init(url: String, user: String, password: String): Boolean {
        try {
            connection = DriverManager.getConnection("jdbc:postgresql://$url/$dbName", user, password)
            statement = connection.createStatement()

            isAvailable = true
            return true
        } catch (ex: PSQLException) {
            exception = ex.message
            println(ex.message)
        }

        return false
    }
}