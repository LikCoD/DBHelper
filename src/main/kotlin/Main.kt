import liklibs.db.annotations.DBInfo
import liklibs.db.annotations.DBTable
import liklibs.db.annotations.Dependency
import liklibs.db.annotations.Primary
import liklibs.db.delegates.DBDependency
import liklibs.db.delegates.DBProperty
import liklibs.db.lists
import liklibs.db.sqList

@DBInfo("djasdjsavsamd", "db_credentials_example.json", "")
sealed class Main {
    @DBTable("t1")
    class Table1(value: String){
        var value by DBDependency.dbDependency(value, listOf(Table2::value))

        @Primary
        var id by DBProperty.dbProperty(0)

        override fun toString(): String = "(value: $value)"
    }

    @DBTable("t2")
    class Table2(value: String){
        var value by DBProperty.dbProperty(value)

        @Primary
        var id by DBProperty.dbProperty(0)

        override fun toString(): String = "(value: $value)"
    }
}

fun main(){
    val t1 = sqList<Main.Table1>()
    val t2 = sqList<Main.Table2>()

    lists.forEach { (t, u) ->
        println("$t: ${u.toList()}")
    }

    t1[0].value = "value...."

    lists.forEach { (t, u) ->
        println("$t: ${u.toList()}")
    }
}