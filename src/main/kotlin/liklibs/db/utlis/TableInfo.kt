package liklibs.db.utlis

import liklibs.db.annotations.DBTable
import liklibs.db.annotations.Primary

@DBTable("tablesinfo")
data class TableInfo(
    val tableName: String,
    val insertIds: MutableList<Int> = mutableListOf(),
    val deleteIds: MutableList<Int> = mutableListOf(),
    val wasOffline: Boolean = false,
    @Primary val id: Int = -1   
)