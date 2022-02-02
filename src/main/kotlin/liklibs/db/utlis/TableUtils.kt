package liklibs.db.utlis

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import liklibs.db.*
import liklibs.db.annotations.*
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

        return if (max < 0) 1 else max + 1
    }

    fun sync(): List<T> {
        val oldList = fromJSON()
        if (!isAvailable) return oldList

        getFile("sql").readLines().forEach {
            if (it.startsWith("DELETE")) {
                execute(it)
                return@forEach
            }

            val oldIds = it.substringAfter("--").split(",").mapNotNull { i -> i.toIntOrNull() }
            val ids = mutableListOf<Int>()

            val res = executeQuery(it.substringBefore("; --")) ?: return@forEach
            while (res.next()) ids.add(res.getInt("_id"))

            oldList.forEach { t ->
                val idProperty = t.findPropertyWithAnnotation<Primary>()
                oldIds.forEachIndexed { i, oldId ->
                    if (idProperty?.get(t) != oldId) return@forEachIndexed

                    idProperty.set(t, ids[i])

                    changeDependenciesProperties(t, newValue = ids[i])
                }
            }
        }
        getFile("sql").writeText("")

        insertFromClass(oldList, true)

        val syncedList = selectToClass(c).filterNotNull()
        toJSON(syncedList)

        return syncedList
    }

    fun insert(list: List<T>, obj: T) {
        val query = insertFromClass(obj)

        if (!isAvailable) {
            val id = getLocalId(list)
            obj.setPropertyWithAnnotation<Primary>(obj, id)
            getFile("sql").appendText("$query --$id\n")
        }

        toJSON(list)
    }

    fun insert(list: List<T>, objs: Collection<T>) {
        val query = insertFromClass(objs)
        if (!isAvailable) {
            val ids = objs.joinToString(",") { t ->
                getLocalId(list).apply { t.setPropertyWithAnnotation<Primary>(t, this) }.toString()
            }
            getFile("sql").appendText("$query --$ids\n")
        }

        toJSON(list)
    }

    private fun findInsertedObj(obj: T): T? = fromJSON("_insert").find { t ->
        val property = obj.findPropertyWithAnnotation<Primary>() ?: return@find false

        val id = property.get(obj) ?: return@find false
        val insertId = property.get(t) ?: return@find false

        id == insertId
    }

    private fun deleteWithId(id: Int?): Boolean {
        id ?: return false

        val lines = getFile("sql").readLines().toMutableList()

        for ((i, it) in lines.withIndex()) {
            if (!it.startsWith("INSERT")) continue

            val ids = it.substringAfter("--").split(",").toMutableList()
            if (ids.isEmpty()) continue

            if (ids.size == 1) {
                if (ids.first().toIntOrNull() == id) {
                    lines.removeAt(i)
                    getFile("sql").writeText(lines.joinToString("\n", postfix = "\n"))
                    return true
                }
                continue
            }

            iFor@ for ((j, s) in ids.withIndex()) {
                if (s.toIntOrNull() != id) continue@iFor

                val prefix = it.substring(0, it.indexOf("VALUES") + 7)
                val postfix = it.substring(it.indexOf(" ON CONFLICT")).substringBefore(" --")

                val parts =
                    it.substringAfter(prefix).substringBefore(postfix).drop(1).dropLast(1).split("), (").toMutableList()
                parts.removeAt(j)

                ids.removeAt(j)

                lines[i] = prefix + parts.joinToString { "($it)" } + postfix + " --" + ids.joinToString(",")

                getFile("sql").writeText(lines.joinToString("\n", postfix = "\n"))

                return true
            }
        }

        return false
    }

    fun delete(list: List<T>, obj: T) {
        val query = deleteFromClass(obj)
        if (!isAvailable) {
            val id = query?.substringAfter("_id = ")?.dropLast(1)?.toIntOrNull()
            if (!deleteWithId(id)) getFile("sql").appendText("$query\n")
        }

        toJSON(list)
    }

    fun delete(list: List<T>, objs: Collection<T>) {
        var query = deleteFromClass(objs)
        if (!isAvailable) {
            val ids = query?.substringAfter("_id IN (")?.dropLast(2)?.split(", ")?.toMutableList()?.filter {
                !deleteWithId(it.toIntOrNull())
            }

            if (ids != null && ids.isNotEmpty()) {
                query = query?.substringBefore("IN (")
                query += if (ids.size == 1) "= ${ids.first()}"
                else "IN (${ids.joinToString()})"

                getFile("sql").appendText("$query\n")
            }
        }

        toJSON(list)
    }

    fun update(list: List<T>, obj: T, oldObj: T) {
        val id = oldObj.getPropertyWithAnnotation<Primary>(oldObj)

        val idProperty = obj.setPropertyWithAnnotation<Primary>(obj, id)
        if (idProperty != true) return

        if (isAvailable) updateFromClass(obj)

        toJSON(list)

        val insertedObj = findInsertedObj(obj)

        if (insertedObj != null) toJSON(fromJSON("_insert") - insertedObj + obj, "_insert")

        changeDependenciesProperties(oldObj, obj)
    }

    private fun getFile(extension: String = "", postfix: String = ""): File {
        val path = "$offlineStoragePath${c.simpleName}$postfix.$extension"
        File(path.substringBeforeLast("\\")).mkdirs()

        return File(path).apply { if (!exists()) createNewFile() }
    }


    internal fun fromJSON(postfix: String = ""): List<T> = try {
        jsonAdapter.fromJson(getFile(postfix).readText())!!
    } catch (ex: Exception) {
        emptyList()
    }

    internal fun toJSON(obj: List<T>, postfix: String = "") = try {
        getFile(postfix).writeText(jsonAdapter.toJson(obj))
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    private fun changeDependenciesProperties(oldObj: T, obj: T? = null, newValue: Any? = null) {
        if (obj == null && newValue == null) return

        oldObj.onAnnotationFind<Dependency> { property, dependency ->
            lists[dependency.listName]?.forEach { el ->
                val dependencyProperty = el.findPropertyWithName(dependency.field)
                val oldValue = dependencyProperty?.get(el) ?: return@forEach
                if (oldValue != property.get(oldObj)) return@forEach

                dependencyProperty.set(el, newValue ?: property.get(obj!!))

                val id = el.getPropertyWithAnnotation<Primary>(el) ?: return@forEach
                val tableName = el.annotation<DBTable>()?.tableName ?: return@forEach

                execute("UPDATE $tableName SET ${dependency.field} = $newValue WHERE _id = $id")
            }
        }
    }
}