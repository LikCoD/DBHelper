package liklibs.db.utlis

data class ConflictResolver<T>(var local: T, var server: T)