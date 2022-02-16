package liklibs.db

import liklibs.db.annotations.CreateQuery
import liklibs.db.annotations.Dependency
import liklibs.db.annotations.NotInsertable
import liklibs.db.annotations.Primary
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.typeOf
import kotlin.reflect.KType

object TableQuery {
    inline fun <reified T : Any> createSQLite() = createSQLite(T::class)

    fun <T : Any> createSQLite(c: KClass<T>): String {
        c.findAnnotation<CreateQuery>()?.let {
            return File(it.filePath).readText()
        }

        val types = mutableListOf("_id integer primary key")

        val dependencies = mutableListOf<String>()

        c.declaredMemberProperties.forEach {
            if (it.hasAnnotation<NotInsertable>() || it.hasAnnotation<Primary>()) return@forEach

            val filed = it.dbFieldName()
            with(it.returnType) {
                when {
                    check<Int>() -> types.add("$filed integer")
                    check<String>() || check<Date>() || check<Time>() || check<Timestamp>() -> types.add("$filed text")
                    check<Double>() -> types.add("$filed double")
                    check<Float>() -> types.add("$filed float")
                    else -> return@forEach
                }
            }

            val dependency = it.findAnnotation<Dependency>() ?: return@forEach

            //language=SQL
            dependencies.add("FOREIGN KEY ($filed) REFERENCES ${dependency.table} (${dependency.filed}) ON UPDATE CASCADE")
        }

        return (types + dependencies).joinToString()
    }

    private inline fun <reified T> KType.check() = this == typeOf<T>() || this == typeOf<T?>()
}