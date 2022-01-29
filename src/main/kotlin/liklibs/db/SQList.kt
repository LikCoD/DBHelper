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
    private val list: MutableList<E> = mutableListOf(),
) : MutableList<E> by list {
    val utils: TableUtils<E>

    init {
        utils = TableUtils(dbCredentialsFileName, kClass, serialization)

        list.addAll(utils.sync())
    }

    override fun add(element: E): Boolean =
        list.add(element).also {
            utils.insert(list, element)
        }

    override fun add(index: Int, element: E) =
        list.add(index, element).also {
            utils.insert(list, element)
        }

    override fun addAll(index: Int, elements: Collection<E>): Boolean =
        list.addAll(index, elements).also {
            utils.insert(list, elements)
        }

    override fun addAll(elements: Collection<E>): Boolean =
        list.addAll(elements).also {
            utils.insert(list, elements)
        }

    override fun clear() =
        list.clear().also {
            utils.delete(list, list)
        }

    override fun remove(element: E): Boolean =
        list.remove(element).also {
            utils.delete(list, element)
        }

    override fun removeAll(elements: Collection<E>): Boolean =
        list.removeAll(elements).also {
            utils.delete(list, elements)
        }

    override fun removeAt(index: Int): E =
        list.removeAt(index).also {
            utils.delete(list, list[index])
        }

    override fun set(index: Int, element: E): E {
        val oldElement = list[index]

        return list.set(index, element).also {
            utils.update(list, element, oldElement)
        }
    }
}

@ExperimentalSerializationApi
inline fun <reified T : Any> sqList(dbCredentialsFileName: String) =
    SQList(dbCredentialsFileName, T::class, serializer())