package liklibs.db.delegates

import liklibs.db.*
import liklibs.db.annotations.DBTable
import liklibs.db.annotations.Dependency
import liklibs.db.annotations.Primary
import liklibs.db.utlis.DBUtils
import org.intellij.lang.annotations.Language
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

object DBProperty {
    class Property<V>(private var value: V) : ReadWriteProperty<Any?, V> {

        override fun getValue(thisRef: Any?, property: KProperty<*>): V = value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            val oldValue = this.value
            this.value = value

            if (!property.hasAnnotation<Primary>()) changeValueInDB(thisRef, property, value)

            changeDependencies(property, value, oldValue)
        }


        private fun changeValueInDB(thisRef: Any?, property: KProperty<*>, value: V) {
            val id = thisRef?.getPropertyWithAnnotation<Primary>()
                ?: throw IllegalStateException("No primary property in dependency class")
            val tableName =
                thisRef.annotation<DBTable>()?.tableName ?: throw IllegalStateException("Provide table name")

            val thisList = lists[thisRef::class.simpleName] ?: throw IllegalStateException("No list created")

            if (thisList.utils.isAvailable) {
                @Language("PostgreSQL")
                val query =
                    "UPDATE $tableName SET ${property.getDBFieldName()} = ${DBUtils.parseValue(value)} WHERE _id = $id"
                thisList.utils.execute(query)
            }
            thisList.save()
        }

        private fun changeDependencies(property: KProperty<*>, value: V, oldValue: V) {
            val dependency = property.findAnnotation<Dependency>() ?: return
            val list = lists[dependency.listName] ?: throw IllegalArgumentException("No list with this name")
            list.forEach {
                val dependencyProperty =
                    it.findPropertyWithName(dependency.field) ?: throw IllegalArgumentException("No such field")

                if ((dependencyProperty.get(it) ?: return@forEach) == oldValue) dependencyProperty.set(it, value)

                if (!list.utils.isAvailable) return@forEach

                val id = it.getPropertyWithAnnotation<Primary>()
                    ?: throw IllegalStateException("No primary property in dependency class")
                val tableName = it.annotation<DBTable>()?.tableName
                    ?: throw IllegalStateException("No table name in dependency class")

                list.utils.execute("UPDATE $tableName SET ${dependency.field} = ${DBUtils.parseValue(value)} WHERE _id = $id")
            }

            list.save()
        }
    }

    fun <T> dbProperty(i: T): Property<T> = Property(i)
}


