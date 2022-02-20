package liklibs.db.delegates

import liklibs.db.lists
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField

class DBDependency<V>(value: V, private vararg val propertyList: KProperty<*>) : DBProperty<V>(value) {

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        if (this.value == value) return

        super.setValue(thisRef, property, value)

        propertyList.forEach propertiesFor@{ dependencyProperty ->
            val parent = dependencyProperty.javaField!!.declaringClass.kotlin
            val list = lists[parent.simpleName] ?: return@propertiesFor
            list.update()
        }
    }
}


