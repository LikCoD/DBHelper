package liklibs.db.utlis

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import liklibs.db.*
import liklibs.db.annotations.DBInfo
import liklibs.db.annotations.DBTable
import liklibs.db.annotations.Primary
import okio.EOFException
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation

class TableUtils<T : Any>(
    private val c: KClass<T>,
    private val dbInfo: DBInfo = c.java.declaringClass.kotlin.findAnnotation() ?: throw IllegalArgumentException(),
    var offlineStoragePath: String = dbInfo.offlineStoragePath,
) {
    val onlineDB = dbs.getOrPut("PostgresData") { DB(dbInfo.dbName, PostgresData, dbInfo.credentialsFilePath) }
    val offlineDB = dbs.getOrPut("SQLiteData") { DB("${dbInfo.dbName}.sqlite", SQLiteData, null) }

    private val infoAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(TableInfo::class.java)

    private var info: TableInfo = TableInfo()

    fun sync(): List<T> {
        val tableName = c.findAnnotation<DBTable>()?.tableName ?: return emptyList()

        offlineDB.execute("PRAGMA foreign_keys = TRUE")

        val createQuery = File("db\\$tableName.query").readText()
        offlineDB.execute("CREATE TABLE IF NOT EXISTS $tableName ($createQuery);")

        val oldList = offlineDB.selectToClass(c).filterNotNull().toMutableList()
        if (!onlineDB.isAvailable) return oldList

        if (info.deleteIds.isNotEmpty())
            onlineDB.execute("DELETE FROM $tableName WHERE _id IN (${info.deleteIds.joinToString()})")

        info.insertsIds.forEach { id ->
            val element = oldList.find { it::class.getPropertyWithAnnotation<Primary>(it) == id } ?: return@forEach

            onlineDB.insertFromClass(element)

            val newId = element::class.getPropertyWithAnnotation<Primary>(element)

            offlineDB.executeUpdate("UPDATE $tableName SET _id = $newId WHERE _id = $id")

            oldList.remove(element)
        }

        info.insertsIds.clear()
        info.deleteIds.clear()

        saveInfo()

        onlineDB.insertFromClass(oldList, true)

        if (!info.wasOffline) return oldList

        val syncedList = onlineDB.selectToClass(c).filterNotNull()
        offlineDB.insertFromClass(syncedList, true)

        return syncedList
    }

    fun insert(obj: T) {
        val id = offlineDB.insertFromClass(obj) ?: -1

        if (!onlineDB.isAvailable) {
            info.insertsIds.add(id)
            saveInfo()
        } else onlineDB.insertFromClass(obj)
    }

    fun insert(objs: Collection<T>) {
        val ids = offlineDB.insertFromClass(objs).filterNotNull()

        if (!onlineDB.isAvailable) {
            info.insertsIds.addAll(ids)
            saveInfo()
        } else onlineDB.insertFromClass(objs)

    }

    fun delete(obj: T) {
        if (!onlineDB.isAvailable) {
            val id = obj::class.getPropertyWithAnnotation<Primary>(obj) as Int

            if (!info.insertsIds.remove(id)) info.deleteIds.add(id)

            saveInfo()
        } else onlineDB.deleteFromClass(obj)

        offlineDB.deleteFromClass(obj)
    }

    fun delete(objs: Collection<T>) {
        if (!onlineDB.isAvailable) {
            objs.forEach { obj ->
                val id = obj::class.getPropertyWithAnnotation<Primary>(obj) as Int

                if (!info.insertsIds.remove(id)) info.deleteIds.add(id)
            }

            saveInfo()
        } else onlineDB.deleteFromClass(objs)

        offlineDB.deleteFromClass(objs)
    }

    private fun getFile(postfix: String = ""): File {
        val path = "$offlineStoragePath${c.simpleName}$postfix.json"
        File(path.substringBeforeLast("\\")).mkdirs()

        return File(path).apply { if (!exists()) createNewFile() }
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
