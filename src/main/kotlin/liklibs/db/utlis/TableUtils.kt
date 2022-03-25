package liklibs.db.utlis

import liklibs.db.*
import liklibs.db.DB.Companion.parseToClass
import liklibs.db.annotations.DBInfo
import liklibs.db.annotations.DBTable
import liklibs.db.annotations.Primary
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class TableUtils<T : Any>(
    private val c: KClass<T>,
    var selectQuery: String?,
    private val dbInfo: DBInfo = c.java.declaringClass.kotlin.findAnnotation() ?: throw IllegalArgumentException(),
    onlineDBData: DBData = PostgresData,
    offlineDBData: DBData = SQLiteData,
) {
    val onlineDB = dbs.getOrPut("${onlineDBData.dbName}_${dbInfo.dbName}") { DB(dbInfo.dbName, onlineDBData, dbInfo.credentialsFilePath) }
    val offlineDB = dbs.getOrPut("${offlineDBData.dbName}_${dbInfo.dbName}") {
        DB("${dbInfo.dbName}.sqlite", SQLiteData, null).apply {
            execute("PRAGMA foreign_keys = TRUE")
            execute("CREATE TABLE IF NOT EXISTS tablesinfo (_id integer primary key, tablename varchar, insertids text, deleteids text, wasoffline varchar)")
        }
    }

    private var info: TableInfo

    init {
        val tableName = c.findAnnotation<DBTable>()?.tableName ?: throw IllegalArgumentException()

        info = offlineDB.executeQuery("SELECT * FROM tablesinfo WHERE tablename = '$tableName'")
            ?.parseToClass(offlineDBData) ?: TableInfo(tableName)
    }

    fun sync(): List<T> {
        val tableName = c.findAnnotation<DBTable>()?.tableName ?: return emptyList()

        val createQuery = TableQuery.createSQLite(c)
        offlineDB.execute("CREATE TABLE IF NOT EXISTS $tableName ($createQuery);")

        println("[INFO] Create $tableName query - $createQuery")

        val oldList = offlineDB.selectToClass(c, selectQuery).filterNotNull().toMutableList()
        if (!onlineDB.isAvailable) return oldList

        if (info.deleteIds.isNotEmpty())
            onlineDB.execute("DELETE FROM $tableName WHERE _id IN (${info.deleteIds.joinToString()})")

        info.insertIds.forEach { id ->
            val element = oldList.find { it::class.getPropertyWithAnnotation<Primary>(it) == id } ?: return@forEach

            onlineDB.insertFromClass(element)

            val newId = element::class.getPropertyWithAnnotation<Primary>(element)

            offlineDB.executeUpdate("UPDATE $tableName SET _id = $newId WHERE _id = $id")

            oldList.remove(element)
        }

        info.insertIds.clear()
        info.deleteIds.clear()

        saveInfo()

        onlineDB.insertFromClass(oldList, true)

        //if (!info.wasOffline) return oldList

        val syncedList = onlineDB.selectToClass(c, selectQuery).filterNotNull()
        offlineDB.insertFromClass(syncedList, true)

        return syncedList
    }

    fun insert(obj: T) {
        if (onlineDB.isAvailable) {
            onlineDB.insertFromClass(obj)
            offlineDB.insertFromClass(obj, true)
        }else {
            val id = offlineDB.insertFromClass(obj) ?: -1

            info.insertIds.add(id)
            saveInfo()
        }
    }

    fun insert(objs: Collection<T>) {
        if (onlineDB.isAvailable) {
            onlineDB.insertFromClass(objs)
            offlineDB.insertFromClass(objs, true)
        }else {
            val ids = offlineDB.insertFromClass(objs).filterNotNull()

            info.insertIds.addAll(ids)
            saveInfo()
        }
    }

    fun delete(obj: T) {
        if (!onlineDB.isAvailable) {
            val id = obj::class.getPropertyWithAnnotation<Primary>(obj) as Int

            if (!info.insertIds.remove(id)) info.deleteIds.add(id)

            saveInfo()
        } else onlineDB.deleteFromClass(obj)

        offlineDB.deleteFromClass(obj)
    }

    fun delete(objs: Collection<T>) {
        if (!onlineDB.isAvailable) {
            objs.forEach { obj ->
                val id = obj::class.getPropertyWithAnnotation<Primary>(obj) as Int

                if (!info.insertIds.remove(id)) info.deleteIds.add(id)
            }

            saveInfo()
        } else onlineDB.deleteFromClass(objs)

        offlineDB.deleteFromClass(objs)
    }

    private fun saveInfo() = offlineDB.insertFromClass(info, info.id != -1)
}
