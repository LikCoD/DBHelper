package liklibs.db

import liklibs.db.annotations.DBField
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

fun KClass<*>.members() = declaredMemberProperties

inline fun <reified A : Annotation> KClass<*>.findPropertyWithAnnotation() = members().find { it.hasAnnotation<A>() }

fun KProperty<*>.get(obj: Any) = getter.call(obj)

fun KProperty<*>.set(obj: Any, value: Any?): Boolean {
    if (this !is KMutableProperty<*>) return false

    setter.call(obj, value)

    return true
}

inline fun <reified A : Annotation> KClass<*>.getPropertyWithAnnotation() =
    findPropertyWithAnnotation<A>()?.get(this)

inline fun <reified A : Annotation> KClass<*>.setPropertyWithAnnotation( value: Any?) =
    findPropertyWithAnnotation<A>()?.set(this, value)

fun KProperty<*>.dbFieldName() = findAnnotation<DBField>()?.name ?: name
