package liklibs.db.utlis

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import liklibs.db.DB
import liklibs.db.DBField
import liklibs.db.DBInfo

import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

@ExperimentalSerializationApi
class TableUtils<T : Any>(
    credentialsFileName: String,
    private val c: KClass<T>,
    private val serialization: KSerializer<List<T>>,
) : DB(c.findAnnotation<DBInfo>()?.dbName ?: throw IllegalArgumentException(), credentialsFileName) {

    fun sync(): List<T> {
        if (!isAvailable) return fromJSON()

        deleteFromClass(fromJSON("_delete"))
        toJSON(emptyList(), "_delete")

        val list = fromJSON()
        if (list.isNotEmpty()) insertFromClass(list, true)

        val syncedList = selectToClass(c).filterNotNull()
        toJSON(syncedList)

        return syncedList
    }

    fun insert(list: List<T>, obj: T) {
        toJSON(list)

        if (isAvailable) insertFromClass(obj)
    }

    fun insert(list: List<T>, objs: Collection<T>) {
        toJSON(list)

        if (isAvailable) insertFromClass(objs)
    }

    fun delete(list: List<T>, obj: T) {
        toJSON(list)

        if (isAvailable) deleteFromClass(obj)
        else toJSON(fromJSON("_delete") + obj)
    }

    fun delete(list: List<T>, objs: Collection<T>) {
        toJSON(list)

        if (isAvailable) deleteFromClass(objs)
        else toJSON(fromJSON("_delete") + objs)
    }

    fun update(list: List<T>, obj: T, oldObj: T){
        val id = oldObj::class.declaredMemberProperties
            .find { (it.findAnnotation<DBField>()?.name ?: it.name) == "_id" }
            ?.getter?.call(oldObj)?.toString()?.toIntOrNull() ?: return

        val idProperty = obj::class.declaredMemberProperties
            .find { (it.findAnnotation<DBField>()?.name ?: it.name) == "_id"}

        if (idProperty !is KMutableProperty<*>) return
        idProperty.setter.call(obj, id)

        toJSON(list)

        if (isAvailable) updateFromClass(obj)
    }

    private fun fromJSON(postfix: String = ""): List<T> {
        return try {
            val file = File("db_${c.simpleName}$postfix.json")
            if (!file.exists()) return emptyList()

            Json.decodeFromStream(serialization, file.inputStream())
        } catch (ex: Exception) {
            emptyList()
        }
    }

    private fun toJSON(obj: List<T>, postfix: String = "") {
        try {
            val file = File("db_${c.simpleName}$postfix.json")
            if (!file.exists()) file.createNewFile()

            Json.encodeToStream(serialization, obj, file.outputStream())
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}