package liklibs.db.delegates

import liklibs.db.*
import liklibs.db.annotations.DBTable
import liklibs.db.annotations.Primary
import liklibs.db.utlis.DBUtils
import org.intellij.lang.annotations.Language
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

open class DBProperty<V>(var value: V) : ReadWriteProperty<Any?, V> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): V = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        thisRef ?: throw IllegalArgumentException("Not in class")

        if (this.value == value) return
        this.value = value

        if (property.hasAnnotation<Primary>()) return

        val utils = lists[thisRef::class.simpleName]?.utils ?: throw IllegalStateException("No list created")

        val id = thisRef::class.getPropertyWithAnnotation<Primary>(thisRef)
        if (!utils.info.insertIds.contains(id) && id is Int && !utils.onlineDB.isAvailable){
            utils.info.editIds.add(id)
            utils.saveInfo()
        }

        updateDb(thisRef, property, value, SQLiteData, utils.offlineDB)
        if (utils.onlineDB.isAvailable) updateDb(thisRef, property, value, PostgresData, utils.onlineDB)
    }

    private fun updateDb(thisRef: Any, property: KProperty<*>, value: V, dbData: DBData, db: DBUtils) {
        val id = thisRef::class.getPropertyWithAnnotation<Primary>(thisRef)
            ?: throw IllegalStateException("No primary property in dependency class")
        val tableName = thisRef::class.findAnnotation<DBTable>()?.tableName
            ?: throw IllegalStateException("Provide table name")

        @Language("SQL")
        val query = "UPDATE $tableName SET ${property.dbFieldName()} = ${dbData.parseValue(value)} WHERE _id = $id"

        db.executeUpdate(query)
    }
}


