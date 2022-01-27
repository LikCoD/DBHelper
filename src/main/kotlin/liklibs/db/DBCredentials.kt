package liklibs.db

import kotlinx.serialization.Serializable

@Serializable
data class DBCredentials(val host: String, val user: String, val password: String)

