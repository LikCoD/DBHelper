package liklibs.db

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
@MustBeDocumented
annotation class DBField(val name: String)
