package liklibs.db.utlis

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import liklibs.db.DB
import liklibs.db.annotations.DBField
import liklibs.db.annotations.DBInfo

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
    private val dbInfo: DBInfo = c.java.declaringClass.kotlin.findAnnotation() ?: throw IllegalArgumentException(),
    var offlineStoragePath: String = dbInfo.offlineStoragePath
) : DB(dbInfo.dbName, credentialsFileName) {

    private fun getLocalId(list: List<T>) = list.maxOf {
        it::class.declaredMemberProperties
            .find { p -> (p.findAnnotation<DBField>()?.name ?: p.name) == "_id" }
            ?.getter?.call(it).toString().toInt()
    } + 1

    private fun setId(list: List<T>, obj: T) {
        val idProperty =
            obj::class.declaredMemberProperties.find { (it.findAnnotation<DBField>()?.name ?: it.name) == "_id" }
        if (idProperty !is KMutableProperty<*>) return

        idProperty.setter.call(obj, getLocalId(list))
    }

    private fun setId(list: List<T>, objs: Collection<T>) = objs.forEach { setId(list, it) }

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
        if (isAvailable) insertFromClass(obj)
        else setId(list, obj)

        toJSON(list)
    }

    fun insert(list: List<T>, objs: Collection<T>) {
        if (isAvailable) insertFromClass(objs)
        else setId(list, objs)

        toJSON(list)
    }

    fun delete(list: List<T>, obj: T) {
        if (isAvailable) deleteFromClass(obj)
        else toJSON(fromJSON("_delete") + obj)

        toJSON(list)
    }

    fun delete(list: List<T>, objs: Collection<T>) {
        if (isAvailable) deleteFromClass(objs)
        else toJSON(fromJSON("_delete") + objs)

        toJSON(list)

    }

    fun update(list: List<T>, obj: T, oldObj: T) {
        val id = oldObj::class.declaredMemberProperties
            .find { (it.findAnnotation<DBField>()?.name ?: it.name) == "_id" }
            ?.getter?.call(oldObj)?.toString()?.toIntOrNull() ?: return

        val idProperty = obj::class.declaredMemberProperties
            .find { (it.findAnnotation<DBField>()?.name ?: it.name) == "_id" }

        if (idProperty !is KMutableProperty<*>) return
        idProperty.setter.call(obj, id)

        if (isAvailable) updateFromClass(obj)

        toJSON(list)
    }

    private fun fromJSON(postfix: String = ""): List<T> {
        return try {
            val file = File("$offlineStoragePath${c.simpleName}$postfix.json")
            if (!file.exists()) return emptyList()

            Json.decodeFromStream(serialization, file.inputStream())
        } catch (ex: Exception) {
            emptyList()
        }
    }

    private fun toJSON(obj: List<T>, postfix: String = "") {
        try {
            val path = "$offlineStoragePath${c.simpleName}$postfix.json"
            File(path.substringBeforeLast("\\")).mkdirs()

            val file = File(path)
            if (!file.exists()) file.createNewFile()

            Json.encodeToStream(serialization, obj, file.outputStream())
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}