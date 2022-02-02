package liklibs.db.utlis

data class TableInfo(
    var index: Int = 0,
    val insertsIds: MutableList<Int> = mutableListOf(),
    val deleteIds: MutableList<Int> = mutableListOf(),
)
