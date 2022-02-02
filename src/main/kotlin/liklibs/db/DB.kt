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

open class DB(dbName: String, credentialsFileName: String? = null) : DBUtils(dbName, credentialsFileName) {

    fun <T : Any> selectToClass(c: KClass<T>): List<T?> {
        val table = c.findAnnotation<DBTable>() ?: return emptyList()
        val query = if (table.selectQuery == "") "SELECT * FROM ${table.tableName}" else table.selectQuery

        return executeQuery(query)?.parseToArray(c) ?: return emptyList()
    }

    fun <T : Any> insertFromClass(c: T): String? {
        val tableName = c.annotation<DBTable>()?.tableName ?: return null

        var idProperty: KProperty1<*, *>? = null

        val fields = c.members().mapNotNull {
            if (it.hasAnnotation<Primary>()) {
                idProperty = it
                return@mapNotNull null
            }
            if (it.hasAnnotation<NotInsertable>()) return@mapNotNull null

            val fieldName = it.findAnnotation<DBField>()?.name ?: it.name

            fieldName to it.get(c)
        }.toMap()

        return if (isAvailable) {
            val id = insert(tableName, fields)
            idProperty?.set(c, id)
            null
        } else insertQuery(tableName, fields)
    }

    fun <T : Any> insertFromClass(objs: Collection<T>, parseId: Boolean = false): String? {
        if (objs.isEmpty()) return null

        val table = objs.first().annotation<DBTable>()?.tableName ?: return null

        val keys = mutableListOf<String>()
        val values = MutableList(objs.size) {
            mutableListOf<Any?>()
        }

        var idProperty: KProperty1<*, *>? = null

        objs.first().members().forEach {
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

        return if (isAvailable) {
            val ids = insert(table, keys, *values.toTypedArray())
            objs.forEachIndexed { i, it ->
                if (ids.size <= i) return@forEachIndexed
                idProperty?.set(it, ids[i])
            }
            null
        } else insertQuery(table, keys, *values.toTypedArray())
    }

    fun <T : Any> deleteFromClass(c: T): String? {
        val tableName = c.annotation<DBTable>()?.tableName ?: return null
        val id = c.getPropertyWithAnnotation<Primary>(c) ?: return null

        return if (isAvailable) {
            delete(tableName, "_id = ${parseValue(id)}")
            null
        }else deleteQuery(tableName, "_id = ${parseValue(id)}")
    }

    fun <T : Any> deleteFromClass(objs: Collection<T>): String? {
        if (objs.isEmpty()) return null

        val tableName = objs.first().annotation<DBTable>()?.tableName ?: return null

        val ids = mutableListOf<Any>()

        objs.forEach { c ->
            val id = c.getPropertyWithAnnotation<Primary>(c) ?: return@forEach

            ids.add(id)
        }

        return if (isAvailable) {
            delete(tableName, "_id IN ${ids.joinToString(prefix = "(", postfix = ")")}")
            null
        }else deleteQuery(tableName, "_id IN ${ids.joinToString(prefix = "(", postfix = ")")}")
    }

    fun <T : Any> updateFromClass(c: T): String? {
        val tableName = c.annotation<DBTable>()?.tableName ?: return null

        var id: Int? = null

        val fields = c.members().mapNotNull {
            if (it.hasAnnotation<Primary>()) {
                id = it.get(c) as Int?
                return@mapNotNull null
            }

            if (it.hasAnnotation<NotInsertable>()) return@mapNotNull null

            it.getDBFieldName() to it.get(c)
        }.toMap()

        return if (isAvailable) {
            update(tableName, fields, id ?: return null)
            null
        } else updateQuery(tableName, fields, id ?: return null)
    }

    fun <T : Any> ResultSet.parseToArray(c: KClass<T>): List<T?> {
        val list = mutableListOf<T?>()

        while (next()) list.add(parseToClass(c))

        return list
    }

    fun <T : Any> ResultSet.parseToClass(c: KClass<T>): T? {
        try {
            val constructor = c.primaryConstructor ?: return null

            val fields = c.declaredMemberProperties.associate {
                val field = if (it.hasAnnotation<Primary>()) "_id" else it.getDBFieldName()

                it.name to parseResult(getObject(field))
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

    inline fun <reified T : Any> selectToClass(): List<T?> = selectToClass(T::class)

    inline fun <reified T : Any> ResultSet.parseToArray(): List<T?> = parseToArray(T::class)

    inline fun <reified T : Any> ResultSet.parseToClass(): T? = parseToClass(T::class)
}