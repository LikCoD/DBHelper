package liklibs.db

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Date(
        var day: Int = -1,
        var month: Int = -1,
        var year: Int = -1,
) {
    override fun toString() = "$year-$month-$day"

    companion object {
        fun fromString(str: String): Date {
            val (year, month, day) = str.split("-").map { it.toInt() }

            return Date(day, month, year)
        }
    }
}

data class Time(
        var hour: Int = -1,
        var minute: Int = -1,
        var second: Int = -1,
) {
    override fun toString() = "$hour:$minute:$second"

    companion object {
        fun fromString(str: String): Time {
            val (hour, minute, second) = str.split(":").map { it.toInt() }

            return Time(hour, minute, second)
        }
    }
}

data class Timestamp(
        var day: Int = -1,
        var month: Int = -1,
        var year: Int = -1,
        var hour: Int = -1,
        var minute: Int = -1,
        var second: Int = -1,
) {
    override fun toString() = "$year-$month-$day $hour:$minute:$second"

    companion object {
        fun fromString(str: String): Timestamp {
            val (year, month, day, hour, minute, second) = str.split("-", " ", ":").map { it.toInt() }

            return Timestamp(day, month, year, hour, minute, second)
        }

        private operator fun <E> List<E>.component6(): E = get(5)
    }
}


fun LocalDate.toSQL() = Date(dayOfMonth, monthValue, year)

fun LocalTime.toSQL() = Time(hour, minute, second)

fun LocalDateTime.toSQL() = Timestamp(dayOfMonth, monthValue, year, hour, minute, second)
fun LocalDateTime.toSQLDate() = toLocalDate().toSQL()
fun LocalDateTime.toSQLTime() = toLocalTime().toSQL()


fun java.sql.Date.toSQL() = toLocalDate().toSQL()
fun java.sql.Time.toSQL() = toLocalTime().toSQL()

fun java.sql.Timestamp.toSQL() = toLocalDateTime().toSQL()
fun java.sql.Timestamp.toSQLDate() = toLocalDateTime().toSQLDate()
fun java.sql.Timestamp.toSQLTime() = toLocalDateTime().toSQLTime()


fun Date.toLocalDate() = LocalDate.of(year, month, day)!!

fun Time.toLocalTime() = LocalTime.of(hour, minute, second)!!

fun Timestamp.toLocalDateTime() = LocalDateTime.of(year, month, day, hour, minute, second)!!
fun Timestamp.toLocalDate() = LocalDate.of(year, month, day)!!
fun Timestamp.toLocalTime() = LocalTime.of(hour, minute, second)!!

