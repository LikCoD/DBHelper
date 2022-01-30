package liklibs.db.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class DBInfo(
    val dbName: String,
    val credentialsFilePath: String,
    val offlineStoragePath: String = "db\\"
)
