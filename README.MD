<a href="https://jitpack.io/#LikCoD/DBHelper"> <img src="https://jitpack.io/v/LikCoD/DBHelper.svg" alt="alt"> </a>

# DBHelper

****
How to use

build.gradle.kts

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.LikCoD:DBHelper:0.1.8")
}
```

build.gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.LikCoD:DBHelper:0.1.6'
}
```

****

This library provide some tools that help us to communicate with postgreSQL

Also, there is a sqList that sync with db or if user doesn't have internet connection save files locally

To use date, time, timestamp - use this classes, because standard libs (`java.sql`) not serializable

```kotlin
liklibs.db.Date() // For dates
liklibs.db.Time() // For times
liklibs.db.Timestamp() // For timestamps
```

Examples:
****

**1. Create simple data class**

```kotlin
import kotlinx.serialization.Serializable
import liklibs.db.Date
import liklibs.db.Time
import liklibs.db.Timestamp
import liklibs.db.annotations.*

@DBInfo("dbName")
sealed class DB{

  @DBTable("primary")
  @Serializable
  data class Table1(
    @DBField("prop1") var prop: String = "",
    var prop2: Int = -1,
    @NotInsertable var prop3: Int = -1,
    @Primary var id: Int = -1
  )

  @DBTable("secondary")
  @Serializable
  data class Table2(
    var prop4: Date = Date(),
    var prop5: Timestamp = Timestamp(),
    var prop6: Time = Time(),
    @Primary var id: Int = -1
  )
}
```

In this case there is data class with annotations:

- `@Serializable` - annotation that provide access to save/read JSON files
  add this lines to build.gradle.kts

```kotlin
plugins {
    kotlin("plugin.serialization") version "1.6.10"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
}
```

- `@DBInfo`
- `@NotInsertable` - ignore this property
- `@DBField` - use this annotation when name of property and field are different
- `@Primary` - use for id field

****

**2. Create main func**

```kotlin
import kotlinx.serialization.ExperimentalSerializationApi
import liklibs.db.sqList

@ExperimentalSerializationApi
fun main() {
    val list = sqList<Table>("db_credentials.json")
    println(list.toList())
}
```

There we use `sqList`

- It syncs with db (When you add sth or remove)
- When user doesn't have internet access it save files locally
- It loads data from file when you create it

Also, you need to create file db_credentials.json (or another name) with following structure

```json
{
  "host": "hostname",
  "user": "username",
  "password": "user password"
}
```

****

**3. Work with list as mutableList**

```kotlin
list.add(Table(value = "some_value", key = "some_key"))
```

- id will be automatically filled

```kotlin
list.removeAt(1)
```

- The second element will be deleted if internet connection doesn't present it will be deleted when user connect to
  internet (from list it will be deleted immediately)

****

- You can use all functions as mutableList has

****

If you have specific select request you can use `@Select` annotation

```kotlin
@DBTable(
    tableName = "tablename",
    selectQuery = "SELECT _id, key_ FROM tablename"
)
data class Table(
  @DBField("key_") val key: String,
  @Primary val id: Int
)
```

****

You can change offline storage path just add `offlineStoragePath` property

```kotlin
@DBInfo(
  dbName = "dbName",
  offlineStoragePath = "db\\" 
  // to add prefix to db you can use
  // offlineStoragePath = "db\\prefix" 
)
sealed class DB {
    /*...*/
}
```

To change it in runtime you can use

```kotlin
list.utils.offlineStoragePath = "..."
```

****

If you want to execute query you can use functions builtin sqList

```kotlin
val executeResult1 = list.utils.executeQuery("") //Returns ResultSet?
val executeResult2 = list.utils.execute("") //Returns Boolean
```
