package liklibs.db

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
data class Date(var day: Int, var month: Int, var year: Int){
    override fun toString() = "$year-$month-$day"
}

@Serializable
data class Time(var hour: Int, var minute: Int, var second: Int){
    override fun toString() = "$hour:$minute:$second"
}

@Serializable
data class Timestamp(var day: Int, var month: Int, var year: Int, var hour: Int, var minute: Int, var second: Int){
    override fun toString() = "$year-$month-$day $hour:$minute:$second"
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

