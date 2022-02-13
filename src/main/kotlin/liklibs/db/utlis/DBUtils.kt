package liklibs.db.utlis

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import liklibs.db.DBCredentials
import liklibs.db.DBData
import org.intellij.lang.annotations.Language
import java.io.File
import java.sql.*

abstract class DBUtils(private val dbName: String, val dbData: DBData, credentialsFileName: String? = null) {
    lateinit var connection: Connection
    lateinit var statement: Statement

    var isAvailable = false
    var exception: String? = null

    val log = mutableListOf<String>()

    internal open fun printError(query: String, ex: Exception) {
        println("${ex.message?.replace("\n", " ")}\nQUERY: $query".trim())
    }

    fun execute(@Language("SQL") query: String) = try {
        log.add(query)
        statement.execute(query)
    } catch (ex: Exception) {
        printError(query, ex)
        false
    }

    fun executeQuery(@Language("SQL") query: String): ResultSet? = try {
        log.add(query)
        statement.executeQuery(query)
    } catch (ex: Exception) {
        printError(query, ex)
        null
    }

    fun executeUpdate(@Language("SQL") query: String) = try {
        log.add(query)
        statement.executeUpdate(query)
    } catch (ex: Exception) {
        printError(query, ex)
        null
    }

    internal open fun <T> parseList(list: Iterable<T>, parseValue: Boolean = true) =
        list.joinToString(prefix = "(", postfix = ")") { if (parseValue) dbData.parseValue(it) else it.toString() }

    fun <T> insertQuery(table: String, map: Map<String, T>): String {
        val fieldsQuery = parseList(map.keys, false)
        val valuesQuery = parseList(map.values)
        val updateQuery = map.keys.joinToString { "$it = EXCLUDED.$it" }

        //language=SQL
        return "INSERT INTO $table $fieldsQuery VALUES $valuesQuery ON CONFLICT (_id) DO UPDATE SET $updateQuery"
    }

    fun <T> insertQuery(table: String, keys: List<String>, vararg valuesList: List<T>): String {
        val fieldsQuery = parseList(keys, false)
        val valuesQuery = valuesList.joinToString(transform = ::parseList)
        val updateQuery = keys.joinToString { "$it = EXCLUDED.$it" }

        //language=SQL
        return "INSERT INTO $table $fieldsQuery VALUES $valuesQuery ON CONFLICT (_id) DO UPDATE SET $updateQuery"
    }

    fun <T> updateQuery(table: String, map: Map<String, T>, id: Int): String {
        val fieldsQuery = map.entries.joinToString {
            "SET ${it.key} = ${dbData.parseValue(it.value)}"
        }

        //language=SQL
        return "UPDATE $table $fieldsQuery WHERE _id = $id"
    }

    fun selectQuery(table: String, fields: String = "*", filter: String? = null) =
        "SELECT $fields FROM $table ${if (filter != null) "WHERE $filter" else ""}"

    fun <T> insert(table: String, map: Map<String, T>): Int? {
        executeUpdate(insertQuery(table, map))

        val res = statement.executeQuery("SELECT _id FROM $table ORDER BY _id DESC LIMIT 1")

        if (res == null || !res.next()) return null

        return res.getInt("_id")
    }

    fun <T> insert(table: String, keys: List<String>, vararg valuesList: List<T>): List<Int?> {
        executeQuery(insertQuery(table, keys, *valuesList)) ?: return emptyList()

        val result = statement.executeQuery("SELECT _id FROM $table ORDER BY _id DESC LIMIT ${valuesList.size}")

        val ids = mutableListOf<Int>()

        while (result.next()) ids.add(0, result.getInt("_id"))

        return ids
    }

    fun <T> update(table: String, map: Map<String, T>, id: Int) = execute(updateQuery(table, map, id))

    fun select(table: String, fields: String = "*", filter: String? = null) =
        executeQuery(selectQuery(table, fields, filter))

    @Language("SQL")
    fun deleteQuery(table: String, filter: String) = "DELETE FROM $table WHERE $filter;"

    fun delete(table: String, filter: String) = execute(deleteQuery(table, filter))

    init {
        if (credentialsFileName != null) {
            try {
                Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(DBCredentials::class.java)
                    .fromJson(File(credentialsFileName).readText())?.let {

                        init(it.host, it.user, it.password)
                    }
            } catch (ex: Exception) {
                exception = ex.message
            }
        }

        if (!dbData.userRequire) init("db", divider = "")
    }

    fun init(url: String, user: String? = null, password: String? = null, divider: String = "//"): Boolean {
        try {
            connection = DriverManager.getConnection("jdbc:${dbData.jdbcName}:$divider$url/$dbName", user, password)
            statement = connection.createStatement()

            isAvailable = true
            return true
        } catch (ex: SQLException) {
            exception = ex.message
            println(ex.message)
        }

        return false
    }
}