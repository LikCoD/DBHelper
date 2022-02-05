package liklibs.db.delegates

import liklibs.db.*
import liklibs.db.annotations.DBField
import liklibs.db.annotations.DBTable
import liklibs.db.annotations.Primary
import liklibs.db.utlis.DBUtils
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaField

object DBDependency {
    class Property<V>(value: V, private val propertyList: List<KProperty1<*, *>>) : DBProperty.Property<V>(value) {

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            val oldValue = this.value
            super.setValue(thisRef, property, value)

            changeDependencies(value, oldValue)
        }

        private fun changeDependencies(value: V, oldValue: V) {
            propertyList.forEach propertiesFor@ { dependencyProperty ->
                val parent = dependencyProperty.javaField!!.declaringClass.kotlin
                val list = lists[parent.simpleName] ?: throw IllegalArgumentException("No list with this name")
                list.forEach {
                    if ((dependencyProperty.get(it) ?: return@forEach) == oldValue) dependencyProperty.set(it, value)

                    if (!list.utils.isAvailable) return@forEach

                    val id = it.getPropertyWithAnnotation<Primary>()
                        ?: throw IllegalStateException("No primary property in dependency class")
                    val tableName = it.annotation<DBTable>()?.tableName
                        ?: throw IllegalStateException("No table name in dependency class")

                    val field = dependencyProperty.findAnnotation<DBField>()?.name ?: dependencyProperty.name

                    list.utils.execute("UPDATE $tableName SET $field = ${DBUtils.parseValue(value)} WHERE _id = $id")
                }

                list.save()
            }
        }
    }

    fun <T> dbDependency(i: T, propertyList: List<KProperty1<*, *>>): Property<T> = Property(i, propertyList)
}

