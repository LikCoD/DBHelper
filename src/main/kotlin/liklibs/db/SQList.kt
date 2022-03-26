package liklibs.db

import liklibs.db.utlis.ConflictResolver
import liklibs.db.utlis.TableUtils
import kotlin.reflect.KClass

class SQList<E : Any>(
    private val kClass: KClass<E>,
    selectQuery: String? = null,
    conflictResolver: (ConflictResolver<E>, List<ConflictResolver.Conflict<E>>) -> Unit = { r, l -> r.resolve(l.map { it.local }) },
    private val list: MutableList<E> = mutableListOf(),
) : MutableList<E> by list {
    val utils: TableUtils<E>

    init {
        lists[kClass.simpleName.toString()] = this

        utils = TableUtils(kClass, selectQuery, conflictResolver)

        utils.sync(list)
    }

    override fun add(element: E): Boolean =
        list.add(element).also {
            utils.insert(element)
        }

    override fun add(index: Int, element: E) =
        list.add(index, element).also {
            utils.insert(element)
        }

    override fun addAll(index: Int, elements: Collection<E>): Boolean =
        list.addAll(index, elements).also {
            utils.insert(elements)
        }

    override fun addAll(elements: Collection<E>): Boolean =
        list.addAll(elements).also {
            utils.insert(elements)
        }

    override fun clear() =
        list.clear().also {
            utils.delete(list)
        }

    override fun remove(element: E): Boolean =
        list.remove(element).also {
            utils.delete(element)
        }

    override fun removeAll(elements: Collection<E>): Boolean =
        list.removeAll(elements).also {
            utils.delete(elements)
        }

    override fun removeAt(index: Int): E =
        list.removeAt(index).also {
            utils.delete(it)
        }

    override fun set(index: Int, element: E): E {
        throw IllegalArgumentException("you cannot change full property")
    }

    fun update() {
        list.clear()
        list.addAll(utils.offlineDB.selectToClass(kClass, utils.selectQuery).filterNotNull().toMutableList())
    }
}

inline fun <reified T : Any> sqList(
    selectQuery: String? = null,
    noinline conflictResolver: (ConflictResolver<T>, List<ConflictResolver.Conflict<T>>) -> Unit = { r, l -> r.resolve(l.map { it.local }) },
) = SQList(T::class, selectQuery, conflictResolver)