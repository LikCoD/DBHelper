package liklibs.db.utlis

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import liklibs.db.*
import liklibs.db.annotations.*
import okio.EOFException
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class TableUtils<T : Any>(
    private val c: KClass<T>,
    private val dbInfo: DBInfo = c.java.declaringClass.kotlin.findAnnotation() ?: throw IllegalArgumentException(),
    var offlineStoragePath: String = dbInfo.offlineStoragePath,
) : DB(dbInfo.dbName, dbInfo.credentialsFilePath) {

    @OptIn(ExperimentalStdlibApi::class)
    private val jsonAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<T>>(Types.newParameterizedType(List::class.java, c.java))

    private val infoAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(TableInfo::class.java)

    private var info: TableInfo = TableInfo()

    private fun getLocalId(): Int = info.index++.apply { saveInfo() }

    fun sync(): List<T> {
        val oldList = fromJSON()
        if (!isAvailable) {
            info.index = if (oldList.isEmpty()) 1
            else oldList.maxOf { it::class.getPropertyWithAnnotation<Primary>(it) as Int }
            return oldList
        }

        val tableName = c.findAnnotation<DBTable>()?.tableName ?: return emptyList()
        if (info.deleteIds.isNotEmpty())
            execute("DELETE FROM $tableName WHERE _id IN (${info.deleteIds.joinToString()})")

        info.insertsIds.forEach { id ->
            val element = oldList.find { it::class.getPropertyWithAnnotation<Primary>(it) == id } ?: return@forEach

            insertFromClass(element)
        }

        info.insertsIds.clear()
        info.deleteIds.clear()

        saveInfo()

        insertFromClass(oldList, true)

        if (!info.wasOffline) return oldList

        val syncedList = selectToClass(c).filterNotNull()
        toJSON(syncedList)

        return syncedList
    }

    fun insert(list: List<T>, obj: T) {
        if (!isAvailable) {
            val id = getLocalId()
            obj::class.setPropertyWithAnnotation<Primary>(obj, id)

            info.insertsIds.add(id)
            saveInfo()
        } else insertFromClass(obj)

        toJSON(list)
    }

    fun insert(list: List<T>, objs: Collection<T>) {
        if (!isAvailable) {
            val ids = objs.map { t ->
                getLocalId().apply { t::class.setPropertyWithAnnotation<Primary>(t, this) }
            }

            info.insertsIds.addAll(ids)
            saveInfo()
        } else insertFromClass(objs)

        toJSON(list)
    }

    fun delete(list: List<T>, obj: T) {
        if (!isAvailable) {
            val id = obj::class.getPropertyWithAnnotation<Primary>(obj) as Int

            if (!info.insertsIds.remove(id)) info.deleteIds.add(id)

            saveInfo()
        } else deleteFromClass(obj)

        toJSON(list)
    }

    fun delete(list: List<T>, objs: Collection<T>) {
        if (!isAvailable) {
            objs.forEach { obj ->
                val id = obj::class.getPropertyWithAnnotation<Primary>(obj) as Int

                if (!info.insertsIds.remove(id)) info.deleteIds.add(id)
            }

            saveInfo()
        } else deleteFromClass(objs)

        toJSON(list)
    }

    private fun getFile(postfix: String = ""): File {
        val path = "$offlineStoragePath${c.simpleName}$postfix.json"
        File(path.substringBeforeLast("\\")).mkdirs()

        return File(path).apply { if (!exists()) createNewFile() }
    }


    internal fun fromJSON(postfix: String = ""): List<T> = try {
        jsonAdapter.fromJson(getFile(postfix).readText())!!
    } catch (ex: Exception) {
        if (ex !is EOFException) ex.printStackTrace()

        emptyList()
    }

    internal fun toJSON(obj: List<T>, postfix: String = "") = try {
        getFile(postfix).writeText(jsonAdapter.toJson(obj))
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    private fun loadInfo() {
        try {
            info = infoAdapter.fromJson(getFile("_info").readText())!!
        } catch (ex: Exception) {
            if (ex !is EOFException) ex.printStackTrace()
        }
    }

    private fun saveInfo() = try {
        getFile("_info").writeText(infoAdapter.toJson(info))
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    init {
        loadInfo()
    }
}