package liklibs.db

import org.intellij.lang.annotations.Language

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class DBInfo(
    val dbName: String,
    val tableName: String = "",
    @Language("PostgreSQL") val selectQuery: String = ""
)
