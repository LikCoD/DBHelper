package liklibs.db

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import liklibs.db.utlis.TableUtils
import kotlin.reflect.KClass

@ExperimentalSerializationApi
class SQList<E : Any>(
    dbCredentialsFileName: String,
    kClass: KClass<E>,
    serialization: KSerializer<List<E>>,
    private val list: MutableList<E> = mutableListOf()
) : MutableList<E> by list {
    val utils: TableUtils<E>

    init {
        utils = TableUtils(dbCredentialsFileName, kClass, serialization)

        list.addAll(utils.sync())
    }

    override fun add(element: E): Boolean {
        utils.insert(list, element)

        return list.add(element)
    }

    override fun add(index: Int, element: E) {
        utils.insert(list, element)

        return list.add(index, element)
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        utils.insert(list, elements)

        return list.addAll(index, elements)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        utils.insert(list, elements)

        return list.addAll(elements)
    }

    override fun clear() {
        utils.delete(list, list)

        return list.clear()
    }

    override fun remove(element: E): Boolean {
        utils.delete(list, element)

        return list.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        utils.delete(list, elements)

        return list.removeAll(elements)
    }

    override fun removeAt(index: Int): E {
        utils.delete(list, list[index])

        return list.removeAt(index)
    }

    override fun set(index: Int, element: E): E {
        return list.set(index, element)
    }
}

@ExperimentalSerializationApi
inline fun <reified T : Any> sqList(dbCredentialsFileName: String) =
    SQList(dbCredentialsFileName, T::class, serializer())