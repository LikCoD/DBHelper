package liklibs.db.utlis

data class TableInfo(
    val insertsIds: MutableList<Int> = mutableListOf(),
    val deleteIds: MutableList<Int> = mutableListOf(),
    var wasOffline: Boolean = true
)
