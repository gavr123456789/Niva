# Usage
`JObject toJsonStr`  
`String toJson`  
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
jsonText = """
{
  "name": "Niva",
  "isSimple": true,
  "version": 0.1,
  "tags": ["language", "smalltalk"]
}
"""

nivaProject = jsonText toJson
```
