package liklibs.db

import liklibs.db.annotations.*
import liklibs.db.utlis.DBUtils
import java.sql.ResultSet
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

open class DB(dbName: String, dbData: DBData, credentialsFileName: String? = null) :
    DBUtils(dbName, dbData, credentialsFileName) {

    fun <T : Any> selectToClass(c: KClass<T>, selectQuery: String?): List<T?> {
        val table = c.findAnnotation<DBTable>() ?: return emptyList()
        val query = selectQuery
            ?: (if (table.selectQuery == "") "SELECT * FROM ${table.tableName} ORDER BY _id" else table.selectQuery)

        return executeQuery(query)?.parseToArray(c, dbData) ?: return emptyList()
    }

    fun <T : Any> insertFromClass(c: T, parseId: Boolean = false): Int? {
        val tableName = c::class.findAnnotation<DBTable>()?.tableName ?: return null

        var idProperty: KProperty1<*, *>? = null

        val fields = c::class.members().mapNotNull {
            var fieldName = it.findAnnotation<DBField>()?.name ?: it.name

            if (it.hasAnnotation<Primary>()) {
                idProperty = it
                if (!parseId) return@mapNotNull null
                fieldName = "_id"
            }

            if (it.hasAnnotation<NotInsertable>()) return@mapNotNull null

            fieldName to it.get(c)
        }.toMap()

        val id = insert(tableName, fields)
        idProperty?.set(c, id)

        return id
    }

    fun <T : Any> insertFromClass(objs: Collection<T>, parseId: Boolean = false): List<Int?> {
        if (objs.isEmpty()) return emptyList()

        val table = objs.first()::class.findAnnotation<DBTable>()?.tableName ?: return emptyList()

        val keys = mutableListOf<String>()
        val values = MutableList(objs.size) {
            mutableListOf<Any?>()
        }

        var idProperty: KProperty1<*, *>? = null

        objs.first()::class.members().forEach {
            var fieldName = it.findAnnotation<DBField>()?.name ?: it.name
            if (it.hasAnnotation<Primary>()) {
                idProperty = it
                if (!parseId) return@forEach
                fieldName = "_id"
            }

            if (it.hasAnnotation<NotInsertable>()) return@forEach

            keys.add(fieldName)

            objs.forEachIndexed { i, obj ->
                values[i].add(it.get(obj))
            }
        }

        val ids = insert(table, keys, *values.toTypedArray())
        objs.forEachIndexed { i, it ->
            if (ids.size <= i) return@forEachIndexed
            idProperty?.set(it, ids[i])
        }

        return ids
    }

    fun <T : Any> deleteFromClass(c: T) {
        val tableName = c::class.findAnnotation<DBTable>()?.tableName ?: return
        val id = c::class.getPropertyWithAnnotation<Primary>(c) ?: return

        delete(tableName, "_id = ${dbData.parseValue(id)}")
    }

    fun <T : Any> deleteFromClass(objs: Collection<T>) {
        if (objs.isEmpty()) return

        val tableName = objs.first()::class.findAnnotation<DBTable>()?.tableName ?: return

        val ids = mutableListOf<Any>()

        objs.forEach { c ->
            val id = c::class.getPropertyWithAnnotation<Primary>(c) ?: return@forEach

            ids.add(id)
        }

        delete(tableName, "_id IN ${ids.joinToString(prefix = "(", postfix = ")")}")
    }

    inline fun <reified T : Any> selectToClass(selectQuery: String): List<T?> = selectToClass(T::class, selectQuery)

    companion object {
        fun <T : Any> ResultSet.parseToArray(c: KClass<T>, dbData: DBData): List<T?> {
            val list = mutableListOf<T?>()

            while (next()) list.add(parseToClass(c, dbData))

            return list
        }

        fun <T : Any> ResultSet.parseToClass(c: KClass<T>, dbData: DBData): T? {
            try {
                if (isClosed) return null

                val constructor = c.primaryConstructor ?: return null

                val fields = c.declaredMemberProperties.associate {
                    val field = if (it.hasAnnotation<Primary>()) "_id" else it.dbFieldName()

                    it.name to dbData.parseResult(getObject(field))
                }.toMutableMap()

                val constructorFields =
                    constructor.parameters.associateWith { fields[it.name].also { _ -> fields.remove(it.name) } }

                val obj = constructor.callBy(constructorFields)

                c.declaredMemberProperties.forEach {
                    if (fields[it.name] == null) return@forEach

                    it.set(obj, fields[it.name])
                }

                return obj
            } catch (ex: Exception) {
                println(ex.message)
            }

            return null
        }

        inline fun <reified T : Any> ResultSet.parseToArray(dbData: DBData): List<T?> = parseToArray(T::class, dbData)

        inline fun <reified T : Any> ResultSet.parseToClass(dbData: DBData): T? = parseToClass(T::class, dbData)
    }
}