package liklibs.db.annotations

import org.intellij.lang.annotations.Language

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class DBTable(
    val tableName: String = "",
    @Language("PostgreSQL") val selectQuery: String = ""
)
