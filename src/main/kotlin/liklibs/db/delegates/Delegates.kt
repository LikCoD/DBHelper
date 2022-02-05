package liklibs.db.delegates

import kotlin.reflect.KProperty

fun <T> dbProperty(i: T) = DBProperty(i)

fun <T> dbDependency(i: T, vararg propertyList: KProperty<*>) = DBDependency(i, *propertyList)