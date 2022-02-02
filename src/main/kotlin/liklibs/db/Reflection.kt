package liklibs.db

import liklibs.db.annotations.DBField
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.isAccessible

fun Any.members() = this::class.declaredMemberProperties

inline fun <reified A : Annotation> Any.findPropertyWithAnnotation() = members().find { it.hasAnnotation<A>() }

inline fun <reified A : Annotation> Any.annotation(): A? = this::class.findAnnotation()
inline fun <reified A : Annotation> Any.isAnnotation(): Boolean = this::class.hasAnnotation<A>()

fun KProperty1<*, *>.get(obj: Any) = getter.call(obj)

fun KProperty1<*, *>.set(obj: Any, value: Any?): Boolean {
    if (this !is KMutableProperty<*>) return false

    setter.call(obj, value)

    return true
}

inline fun <reified A : Annotation> Any.getPropertyWithAnnotation() =
    findPropertyWithAnnotation<A>()?.get(this)

inline fun <reified A : Annotation> Any.setPropertyWithAnnotation( value: Any?) =
    findPropertyWithAnnotation<A>()?.set(this, value)

fun KProperty<*>.getDBFieldName() = findAnnotation<DBField>()?.name ?: name

fun Any.findPropertyWithName(name: String) = members().find { it.name == name }

fun <T> Collection<T>.onFounded(filter: (T) -> Boolean, onEach: (T) -> Unit) = forEach { if (filter(it)) onEach(it) }

inline fun <reified A : Annotation> Any.onAnnotationFind(noinline onEach: (KProperty1<out Any, *>, A) -> Unit) {
    members().onFounded({ it.isAnnotation<A>() }) {
        onEach(it, it.findAnnotation()!!)
    }
}

inline fun <T : Any, reified D : Any> KProperty1<*, *>.delegate(obj: T): D? {
    @Suppress("UNCHECKED_CAST")
    this as KProperty1<Any, *>

    isAccessible = true
    if (getDelegate(obj) !is D) return null


    return getDelegate(obj) as D
}

inline fun <reified D : Any, reified A : Annotation> Any.findDelegateByAnnotation(): D? =
    findPropertyWithAnnotation<A>()?.delegate(this)