package liklibs.db

import liklibs.db.annotations.DBField
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

fun Any.members() = this::class.declaredMemberProperties

inline fun <reified A : Annotation> Any.findPropertyWithAnnotation() = members().find { it.hasAnnotation<A>() }

inline fun <reified A : Annotation> Any.annotation(): A? = this::class.findAnnotation()
inline fun <reified A : Annotation> Any.isAnnotation(): Boolean = this::class.hasAnnotation<A>()

fun KProperty1<*, *>.get(obj: Any) = getter.call(obj)

fun KProperty1<*, *>.set(obj: Any, value: Any?): Boolean {
    if (this !is KMutableProperty<*>) return false

    println(setter.call(obj, value))
    return true
}

inline fun <reified A : Annotation> Any.getPropertyWithAnnotation(obj: Any) =
    findPropertyWithAnnotation<A>()?.get(obj)

inline fun <reified A : Annotation> Any.setPropertyWithAnnotation(obj: Any, value: Any?) =
    findPropertyWithAnnotation<A>()?.set(obj, value)

fun KProperty1<*, *>.getDBFieldName() = findAnnotation<DBField>()?.name ?: name

fun Any.findPropertyWithName(name: String) = members().find { it.name == name }

fun <T> Collection<T>.onFounded(filter: (T) -> Boolean, onEach: (T) -> Unit) = forEach { if (filter(it)) onEach(it) }

inline fun <reified A : Annotation> Any.onAnnotationFind(noinline onEach: (KProperty1<out Any, *>) -> Unit) {
    members().onFounded({ it.isAnnotation<A>() }, onEach)
}