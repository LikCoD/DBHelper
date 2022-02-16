package liklibs.db.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
@MustBeDocumented
annotation class Dependency(
        val table: String,
        val filed: String,
)
