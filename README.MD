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
    implementation("com.github.LikCoD:DBHelper:v0.4")
}
```

build.gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.LikCoD:DBHelper:0.4'
}
```

****

This library provide some tools that help us to communicate with postgreSQL server and sqlite client

Also, there is a sqList that sync with db or if user doesn't have internet connection files will save to sqlite db

To use date, time, timestamp - use this classes

```kotlin
liklibs.db.Date() // For dates
liklibs.db.Time() // For times
liklibs.db.Timestamp() // For timestamps
```
****

Also create .query file in db folder that provide sqlite creation table

path to file projectDir(or executable file dir)/db/table_name.query

```sql
_id   integer primary key,
value varchar,
anotherid integer,

FOREIGN KEY (anotherid) REFERENCES db_table (_id) ON UPDATE CASCADE
FOREIGN KEY (value) REFERENCES db_table (anothervalue) ON UPDATE CASCADE
```

****

#### **Do not edit property with @Primary annotation**
#### **Call create list function in order of dependencies**

****

Another way to access created sqList:
```kotlin
lists["TableName"]
```

or created db:
```kotlin
dbs["dbType (PostgreSQL or SQLite)"]
```

****

Examples:

**1. Create simple data class**

```kotlin
import liklibs.db.Date
import liklibs.db.Time
import liklibs.db.Timestamp
import liklibs.db.annotations.*
import liklibs.db.delegates.dbDependency
import liklibs.db.delegates.dbProperty

@DBInfo("dbName", "db_credentials.json")
sealed class DB{

  @DBTable("primary")
  class Table1(prop1: String, prop2: Int, prop3: Int) {
    var prop1 by dbProperty(prop1)
    var prop2 by dbDependency(prop2, Table2::prop4)
    @NotInsertable var prop3 by dbProperty(prop3)
    @Primary var id by dbProperty(-1)
  }

  @DBTable("secondary")
  data class Table2(prop4: Date, prop5: Timestamp, prop6: Time, prop7: Int) {
    var prop4 by dbProperty(prop4)
    var prop5 by dbProperty(prop5)
    var prop6 by dbProperty(prop6)
    @DBField("prop7") var prop by dbProperty(prop7)
    @Primary var id by dbProperty(0)
  }
}
```

In this case there is sealed class with classes inside:

- `@DBInfo` - db name and credentials file, create it with following structure
```json
{
  "host": "",
  "user": "",
  "password": ""
}
```
- `dbProperty` - use to sync values and dependencies when you change variable
- `@NotInsertable` - ignore this property
- `@DBField` - use this annotation when name of property and field are different
- `@Primary` - use for id field
- `@DBTable` - table name, and query to set (if you want to use default query don't use this annotation field)
- `dbDependency` - use this delegate to show dependency one field from another

****

**2. Create main func**

```kotlin
import kotlinx.serialization.ExperimentalSerializationApi
import liklibs.db.sqList

@ExperimentalSerializationApi
fun main() {
    val list = sqList<Table>()
    println(list.toList())
}
```

There we use `sqList`

- It syncs with db (When you add sth or remove)
- When user doesn't have internet access it save files locally
- It loads data from file when you create it

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


If you want to execute query you can use functions builtin sqList

```kotlin
val executeResult1 = list.utils.(offline/online).executeUpdate("") //Returns Int
val executeResult1 = list.utils.(offline/online).executeQuery("") //Returns ResultSet?
val executeResult2 = list.utils.(offline/online).execute("") //Returns Boolean
```
