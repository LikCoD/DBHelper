import liklibs.db.DBField
import liklibs.db.DBInfo
import liklibs.db.NotInsertable
import java.sql.Date
import java.sql.Timestamp

@DBInfo(
    dbName = "defriuiuqmjmcl",
    tableName = "accounts"
)
data class Table(
    @NotInsertable @DBField("_id") var id: Int = 0,
    @DBField("val") val value: String,
    @DBField("_key") val key: String,
    val date: Date?
) {

    @DBField("datetime")
    var dateTime: Timestamp? = null
}