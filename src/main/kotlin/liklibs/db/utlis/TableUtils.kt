package liklibs.db.utlis

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import liklibs.db.DB
import liklibs.db.DBInfo
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@ExperimentalSerializationApi
class TableUtils<T : Any>(
    credentialsFileName: String,
    private val c: KClass<T>
) : DB(c.findAnnotation<DBInfo>()?.dbName ?: throw IllegalArgumentException(), credentialsFileName) {

    private val tableName = c.findAnnotation<DBInfo>()?.tableName ?: throw IllegalArgumentException()

    fun sync(): List<T> {
        if (!isAvailable) return fromJSON()

        val list = fromJSON()
        if (list.isNotEmpty()) insertFromClass(list)

        val syncedList = selectToClass(c).filterNotNull()
        toJSON(syncedList)

        return syncedList
    }

    fun insert(list: List<T>, obj: T){
        toJSON(list)

        if (isAvailable) insertFromClass(obj)
    }

    fun insert(list: List<T>, objs: Collection<T>){
        toJSON(list)

        if (isAvailable) insertFromClass(objs)
    }

    fun delete(list: List<T>, obj: T){
        toJSON(list)

        if (isAvailable) deleteFromClass(obj)
    }

    fun delete(list: List<T>, objs: Collection<T>){
        toJSON(list)

        if (isAvailable) deleteFromClass(objs)
    }

    fun drop(){
        execute("DROP TABLE $tableName")
    }

    private fun fromJSON(): List<T> {
        val file = File("db_${c::simpleName}.json")

        if (!file.exists()) return emptyList()

        return try {
            Json.decodeFromStream(file.inputStream())
        } catch (ex: Exception) {
            emptyList()
        }
    }

    private fun toJSON(obj: List<T>) {
        try {
            Json.encodeToStream(obj, File("db_${c::simpleName}.json").outputStream())
        } catch (_: Exception) {
        }
    }
}