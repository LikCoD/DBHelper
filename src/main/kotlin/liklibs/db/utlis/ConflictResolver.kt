package liklibs.db.utlis

class ConflictResolver<T>(
    var resolver: (ConflictResolver<T>, List<Conflict<T>>) -> Unit,
    var onResolve: (ConflictResolver<T>, List<T>) -> Unit
) {

    fun conflict(conflicts: List<Conflict<T>>) {
        resolver(this, conflicts)
    }

    fun resolve(resolve: List<T>) {
        onResolve(this, resolve)
    }

    data class Conflict<T>(var local: T, var server: T)
}