package liklibs.db

import liklibs.db.annotations.DBField
import liklibs.db.annotations.Primary
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
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

inline fun <reified A : Annotation> KClass<*>.getPropertyWithAnnotation(obj: Any) =
    findPropertyWithAnnotation<A>()?.get(obj)

inline fun <reified A : Annotation> KClass<*>.setPropertyWithAnnotation(obj: Any, value: Any?) =
    findPropertyWithAnnotation<A>()?.set(obj, value)

fun KProperty<*>.dbFieldName(): String {
    if (hasAnnotation<Primary>()) return "_id"

    return findAnnotation<DBField>()?.name ?: name
}
