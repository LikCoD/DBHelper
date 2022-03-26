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
    var resolver: (ConflictResolver<T>, List<ConflictResolver.Conflict<T>>) -> Unit = { r, l -> r.resolve(l.map { it.local }) },
    private val dbInfo: DBInfo = c.java.declaringClass.kotlin.findAnnotation() ?: throw IllegalArgumentException(),
    onlineDBData: DBData = PostgresData,
    offlineDBData: DBData = SQLiteData,
) {
    val onlineDB = dbs.getOrPut("${onlineDBData.dbName}_${dbInfo.dbName}") {
        DB(dbInfo.dbName,
            onlineDBData,
            dbInfo.credentialsFilePath)
    }
    val offlineDB = dbs.getOrPut("${offlineDBData.dbName}_${dbInfo.dbName}") {
        DB("${dbInfo.dbName}.sqlite", SQLiteData, null).apply {
            execute("PRAGMA foreign_keys = TRUE")
            execute("CREATE TABLE IF NOT EXISTS tablesinfo (_id integer primary key, tablename varchar, insertids text, deleteids text, editids text, wasoffline varchar)")
        }
    }

    var info: TableInfo

    init {
        val tableName = c.findAnnotation<DBTable>()?.tableName ?: throw IllegalArgumentException()

        info = offlineDB.executeQuery("SELECT * FROM tablesinfo WHERE tablename = '$tableName'")
            ?.parseToClass(offlineDBData) ?: TableInfo(tableName)
    }

    fun sync(list: MutableList<T>) {
        val tableName = c.findAnnotation<DBTable>()?.tableName ?: return

        val createQuery = TableQuery.createSQLite(c)
        offlineDB.execute("CREATE TABLE IF NOT EXISTS $tableName ($createQuery);")

        println("[INFO] Create $tableName query - $createQuery")

        val oldList = offlineDB.selectToClass(c, selectQuery).filterNotNull().toMutableList()
        if (!onlineDB.isAvailable) {
            list.addAll(oldList)
            return
        }

        if (info.deleteIds.isNotEmpty())
            onlineDB.execute("DELETE FROM $tableName WHERE _id IN (${info.deleteIds.joinToString()})")

        info.insertIds.forEach { id ->
            val element = oldList.find { it::class.getPropertyWithAnnotation<Primary>(it) == id } ?: return@forEach

            onlineDB.insertFromClass(element)

            val newId = element::class.getPropertyWithAnnotation<Primary>(element)

            offlineDB.executeUpdate("UPDATE $tableName SET _id = $newId WHERE _id = $id")

            oldList.remove(element)
        }

        val resolveList = oldList.mapNotNull {
            val id = it::class.getPropertyWithAnnotation<Primary>(it)
            if (id !is Int || !info.editIds.map { i -> i }.contains(id)) return@mapNotNull null

            val server =
                onlineDB.selectToClass(c, "SELECT * FROM $tableName WHERE _id = $id").firstOrNull()
                    ?: return@mapNotNull null

            ConflictResolver.Conflict(it, server)
        }

        info.insertIds.clear()
        info.deleteIds.clear()
        info.editIds.clear()

        saveInfo()

        ConflictResolver(resolver) { _, l ->
            onlineDB.insertFromClass(l, true)

            val elements = onlineDB.selectToClass(c, selectQuery).filterNotNull()
            offlineDB.insertFromClass(elements, true)

            list.addAll(elements)
        }.conflict(resolveList)
    }

    fun insert(obj: T) {
        if (onlineDB.isAvailable) {
            onlineDB.insertFromClass(obj)
            offlineDB.insertFromClass(obj, true)
        } else {
            val id = offlineDB.insertFromClass(obj) ?: -1

            info.insertIds.add(id)
            saveInfo()
        }
    }

    fun insert(objs: Collection<T>) {
        if (onlineDB.isAvailable) {
            onlineDB.insertFromClass(objs)
            offlineDB.insertFromClass(objs, true)
        } else {
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

    fun saveInfo() = offlineDB.insertFromClass(info, info.id != -1)
}
