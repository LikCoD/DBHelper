import kotlinx.serialization.ExperimentalSerializationApi

@ExperimentalSerializationApi
fun main(args: Array<String>) {
    val mainList = sqList<Table>("db_credentials.json")
    println(mainList.toList())

    mainList.removeAt(2)
}

