package liklibs.db.utlis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import liklibs.db.*
import org.intellij.lang.annotations.Language
import org.postgresql.util.PSQLException
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

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

    fun <T> insertQuery(table: String, map: Map<String, T>): String {
        val fieldsQuery = parseList(map.keys, false)
        val valuesQuery = parseList(map.values)
        val updateQuery = map.keys.joinToString { "$it = EXCLUDED.$it" }

        //language=PostgreSQL
        return "INSERT INTO $table $fieldsQuery VALUES $valuesQuery ON CONFLICT (_id) DO UPDATE SET $updateQuery RETURNING _id;"
    }

    fun <T> insertQuery(table: String, keys: List<String>, vararg valuesList: List<T>): String {
        val fieldsQuery = parseList(keys, false)
        val valuesQuery = valuesList.joinToString(transform = ::parseList)
        val updateQuery = keys.joinToString { "$it = EXCLUDED.$it" }

        //language=PostgreSQL
        return "INSERT INTO $table $fieldsQuery VALUES $valuesQuery ON CONFLICT (_id) DO UPDATE SET $updateQuery RETURNING _id;"
    }

    fun <T> updateQuery(table: String, map: Map<String, T>, id: Int): String {
        val fieldsQuery = map.entries.joinToString {
            "SET ${it.key} = ${parseValue(it.value)}"
        }

        //language=PostgreSQL
        return "UPDATE $table $fieldsQuery WHERE _id = $id"
    }

    fun selectQuery(table: String, fields: String = "*", filter: String? = null) =
        "SELECT $fields FROM $table ${if (filter != null) "WHERE $filter" else ""}"

    fun <T> insert(table: String, map: Map<String, T>): Int? {
        val res = executeQuery(insertQuery(table, map))

        if (res == null || !res.next()) return null

        return res.getInt("_id")
    }

    fun <T> insert(table: String, keys: List<String>, vararg valuesList: List<T>): List<Int?> {
        val res = executeQuery(insertQuery(table, keys, *valuesList)) ?: return emptyList()

        val ids = mutableListOf<Int?>()
        while (res.next()) {
            ids.add(res.getInt("_id"))
        }

        return ids
    }

    fun <T> update(table: String, map: Map<String, T>, id: Int) = execute(updateQuery(table, map, id))

    fun select(table: String, fields: String = "*", filter: String? = null) =
        executeQuery(selectQuery(table, fields, filter))

    @Language("PostgreSQL")
    fun deleteQuery(table: String, filter: String) = "DELETE FROM $table WHERE $filter;"

    fun delete(table: String, filter: String) = execute(deleteQuery(table, filter))

    internal open fun <T> parseValue(value: T): String = when (value) {
        is String -> "'${value.replace(Regex("['`]"), "")}'"
        is Iterable<*> -> value.joinToString(prefix = "{", postfix = "}") { parseValue(it) }
        is Timestamp -> "TIMESTAMP '$value'"
        is Date -> "DATE '$value'"
        is Time -> "TIME '$value'"
        else -> value.toString().replace(Regex("['`]"), "")
    }

    internal open fun <T> parseResult(value: T): Any? = when (value) {
        null -> value
        is java.sql.Timestamp -> value.toSQL()
        is java.sql.Date -> value.toSQL()
        is java.sql.Time -> value.toSQL()
        else -> value
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