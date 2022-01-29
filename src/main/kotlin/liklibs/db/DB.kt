package liklibs.db

import kotlinx.serialization.ExperimentalSerializationApi
import liklibs.db.utlis.DBUtils
import java.sql.ResultSet
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

@ExperimentalSerializationApi
open class DB(dbName: String, credentialsFileName: String? = null) : DBUtils(dbName, credentialsFileName) {

    fun <T : Any> selectToClass(c: KClass<T>): List<T?> {
        var query = c.findAnnotation<DBInfo>()?.selectQuery
        if (query == null || query == "") query = "SELECT * FROM ${c.findAnnotation<DBInfo>()?.tableName}"

        val result = executeQuery(query) ?: return emptyList()

        return result.parseToArray(c)
    }

    fun <T : Any> insertFromClass(c: T) {
        val table = c::class.findAnnotation<DBInfo>()?.tableName ?: return

        var idProperty: KMutableProperty<*>? = null

        val fields = c::class.declaredMemberProperties.mapNotNull {
            val fieldName = it.findAnnotation<DBField>()?.name ?: it.name
            if (fieldName == "_id" && it is KMutableProperty<*>) idProperty = it

            if (it.findAnnotation<NotInsertable>() != null) return@mapNotNull null

            fieldName to it.getter.call(c)
        }.toMap()

        val id = insert(table, fields)
        idProperty?.setter?.call(c, id)
    }

    fun <T : Any> insertFromClass(objs: Collection<T>, parseId: Boolean = false) {
        if (objs.isEmpty()) return

        val table = objs.first()::class.findAnnotation<DBInfo>()?.tableName ?: return

        val keys = mutableListOf<String>()
        val values = MutableList(objs.size) {
            mutableListOf<Any?>()
        }

        var idProperty: KMutableProperty<*>? = null

        objs.first()::class.declaredMemberProperties.forEach {
            val fieldName = it.findAnnotation<DBField>()?.name ?: it.name
            if (fieldName == "_id" && it is KMutableProperty<*>) idProperty = it

            if (it.findAnnotation<NotInsertable>() != null && !parseId) return@forEach

            keys.add(fieldName)

            objs.forEachIndexed { i, obj ->
                values[i].add(it.getter.call(obj))
            }
        }


        val ids = insert(table, keys, *values.toTypedArray())
        objs.forEachIndexed { i, it ->
            idProperty?.setter?.call(it, ids[i])
        }
    }

    fun <T : Any> deleteFromClass(c: T) {
        val tableName = c::class.findAnnotation<DBInfo>()?.tableName ?: return

        val id = c::class.declaredMemberProperties
            .find { (it.findAnnotation<DBField>()?.name ?: it.name) == "_id" }
            ?.getter?.call(c) ?: return

        delete(tableName, "_id = ${parseValue(id)}")
    }

    fun <T : Any> deleteFromClass(objs: Collection<T>) {
        if (objs.isEmpty()) return

        val tableName = objs.first()::class.findAnnotation<DBInfo>()?.tableName ?: return

        val ids = mutableListOf<Any>()

        objs.forEach { c ->
            val id = c::class.declaredMemberProperties
                .find { (it.findAnnotation<DBField>()?.name ?: it.name) == "_id" }
                ?.getter?.call(c) ?: return@forEach

            ids.add(id)
        }

        delete(tableName, "_id IN ${ids.joinToString(prefix = "(", postfix = ")")}")
    }

    fun <T : Any> updateFromClass(c: T) {
        val tableName = c::class.findAnnotation<DBInfo>()?.tableName ?: return

        val id: Int? = null

        val fields = c::class.declaredMemberProperties.mapNotNull {
            val fieldName = it.findAnnotation<DBField>()?.name ?: it.name
            if (fieldName == "_id") it.getter.call(c)

            if (it.findAnnotation<NotInsertable>() != null) return@mapNotNull null

            fieldName to it.getter.call(c)
        }.toMap()

        id ?: return
        update(tableName, fields, id)
    }

    private fun <T : Any> ResultSet.parseToArray(c: KClass<T>): List<T?> {
        val list = mutableListOf<T?>()

        while (next()) list.add(parseToClass(c))

        return list
    }

    private fun <T : Any> ResultSet.parseToClass(c: KClass<T>): T? {
        try {
            val constructor = c.primaryConstructor ?: return null

            val fields = c.declaredMemberProperties
                .associate {
                    it.name to parseResult(getObject(it.findAnnotation<DBField>()?.name ?: it.name))
                }.toMutableMap()

            val constructorFields =
                constructor.parameters.associateWith { fields[it.name].also { _ -> fields.remove(it.name) } }

            val obj = constructor.callBy(constructorFields)

            c.declaredMemberProperties.forEach {
                if (fields[it.name] == null || it !is KMutableProperty<*>) return@forEach

                it.setter.call(obj, fields[it.name])
            }

            return obj
        } catch (ex: Exception) {
            println(ex.message)
        }

        return null
    }

    inline fun <reified T : Any> selectToClass(): List<T?> = selectToClass(T::class)

    private inline fun <reified T : Any> ResultSet.parseToArray(): List<T?> = parseToArray(T::class)

    private inline fun <reified T : Any> ResultSet.parseToClass(): T? = parseToClass(T::class)
}