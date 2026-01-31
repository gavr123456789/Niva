# JSON lib is out
check it here https://github.com/gavr123456789/bazar/tree/main/LittleLibs/JSON_parser

### Generate json
```Scala
jsonNum = JNumber value: 5.0
jsonStr = JString value: "t'e'x't"
jObject = JObject map: #{"a\ng'e" jsonNum}
list = JArray list: {jsonNum jsonNum jsonStr jObject}

list toJsonStr echo // [ 5.0, 5.0, "t\'e\'x\'t", { "a\ng\'e": 5.0 } ]

list atIndex: 3, atKey: "a\ng'e", getNum echo // 5.0

```
### Parse json from string
```Scala
nivaProject = """
{
  "name": "Niva",
  "isSimple": true,
  "version": 0.1,
  "tags": ["language", "smalltalk"]
}
"""

nivaProject = jsonText toJson
```


# crud-http example updated
https://github.com/gavr123456789/bazar/tree/main/Programs/http-crud

After 6 mount there was many changes in the compiler, for example Boolean -> Bool rename  

Obviously from the name, it hosts a simple http-server, parses json bodies and uses java.sql to store it in SQLite. real app!  

```Scala
// main.niva
runServer = [
    PersonsDB createTable
    routes = Router routes: {route routeSavePersonToDb routeGetAllPersons}
    routes asServer: (SunHttp port: 9000), start
    
    "hosting on http://localhost:9000" log
    "current routes:\n$routes" log
] do

testDB = [
    PersonsDB createTable
    PersonsDB savePerson: (Person name: "Alice" age: 25)
    PersonsDB savePerson: (Person name: "Bob" age: 25)
    PersonsDB readTable log
]
```
Some routes
```Scala
type Person 
    name: String 
    age: Int 

// simple ping
path = "/hi" bind: Method.GET
// bind path to handler
route = path to: [
    Response status: Status.OK,
             body: "Hello, from niva server!"
]

// save person to db
pathSavePersonToDb = "/savePerson" bind: Method.POST
routeSavePersonToDb = pathSavePersonToDb to: [
    person = Json::Person decode: it body strPayload 
    person log
    PersonsDB savePerson: person
    Response status: Status.OK, body: "added $person"
]

```
SQL
```Scala
...
  on savePerson::Person -> Unit! = [
    databaseUrl = "jdbc:sqlite:people.db"
    insertQuery = "INSERT INTO persons (name, age) VALUES (?, ?)"
    
    connection = DriverManager getConnection: databaseUrl, orPANIC
    insertStatement = connection prepareStatement: insertQuery
    insertStatement addPerson: savePerson
    
    connection close 
  ]
...
PreparedStatement addPerson: p::Person -> Unit! = [
  .setString: 1 value: p name
  .setInt: 2 value: p age
  .executeUpdate
  "SQL: added $p" log
]
```

I really like to just create new methods for foreign types, like PreparedStatement addPerson and ResultSet getPersons, these are types from java.sql

kiinda like in Ruby but typed  
14 August 2025  

***
