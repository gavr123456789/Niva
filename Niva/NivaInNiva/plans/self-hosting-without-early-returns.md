# Подготовка NivaInNiva к self-hosting через Clojure

## Цель


Устранить нелокальные `^` из реализации компилятора NivaInNiva, сохранив поддержку `ReturnStatement` в самом языке Niva для пользовательских программ.

текущий бекенд это котлин, поэтому NivaInNiva сейчас компилируется, но сама NivaInNiva имеет главный бекенд - Clojure, который не поддерживает ретурн вообще,
поэтому цель - сделать глобальный рефакторинг всех ретурнов чтобы IrReturn в кодовой базе всегда встречался на последнем месте Body как последний експрешон



## Основные изменения

### 1. Разделить типы использования `^`

Составить инвентарь и обрабатывать отдельно:

- финальный `^ expression` на верхнем уровне метода — убрать `^`, оставить expression последним выражением;
- guard-return:

```niva
condition ifTrue: [^ value]
code
```

  переписать в match по `true`:

```niva
| true
| condition => value
|=> [
  code
]
```

- несколько guard-return подряд объединять в один match:

```niva
| true
| condition1 => value1
| condition2 => value2
|=> [
  fallback
]
```

- возврат из `unpack:`, `forEach:`, `map:` и других callback-блоков переписывать в явное ветвление до вызова callback либо в локальный результат, чтобы callback не выполнял возврат из внешнего метода;
- возвраты из циклов переписывать через результат условия/матчинга или через отдельное состояние, если цикл должен завершиться и после него продолжить выполнение.

например каждый unpack может быть заменен на матчинг против null, в ветке елс(|=>) значение против которого матчим станет не нуллабл
возможно это потребует создания дополнительных промежуточных переменных потому что нельзя сделать такое с экспрешоном

```
| nullabl expr
| null => ...
|=> we dont have a variable here, so no non-nullable
```

```
x = nullable expr
| x
| null => ...
|=> x is non-nullable here
```

### 2. Переписать реализацию compiler/front/IR

Приоритетные области:

- `compiler/compiler.niva`
- `compiler/incremental.niva`
- `argParse/cliArgs.niva`
- `front/parser/*.niva`
- `front/resolver/*.niva`
- `ir/*.niva`
- `back/clojureBackend/*.niva`

Особое внимание:

- `front/parser/parse.niva` — ранние возвраты из попыток парсинга и `unpack`;
- `front/resolver/typeDB.niva` — возвраты из `unpack`, обходов типов и поиска методов;
- `front/resolver/resolveExpr2.niva` — guard-return при разрешении AST;
- `ir/fromTypedAst.niva` — возвраты из преобразований AST в IR;
- `back/clojureBackend/cljEmit.niva` — убрать предположение, что внутренний `IrReturn` допустим.

### 3. Сохранить пользовательскую семантику `^`

Не удалять:

- `ReturnStatement` из AST/typed AST/IR;
- поддержку `^` в пользовательском исходном коде;
- тестовые строки, внутри которых проверяется парсинг пользовательского `^`;
- union-конструкторы вида `| ^Expr`;
- `^` в embedded Kotlin/Clojure-коде и type hints.

Изменяется только исходный код самого self-hosting-компилятора.

### 4. Усилить Clojure backend

Вместо позднего падения:

```niva
TO DO: "Clojure does not support ^"
```

добавить явную проверку инварианта:

- `IrReturn` допускается только последним statement блока;
- любой внутренний `IrReturn` выдаёт диагностическое сообщение с source token;
- добавить тест на отсутствие нелокальных возвратов в IR self-hosting-модулей.

### 5. Добавить проверку покрытия

Создать проверку, которая:

- сканирует `.niva`-исходники компилятора;
- игнорирует строки, comments, embedded strings и union-конструкторы;
- находит нелокальные `ReturnStatement`;
- запускается перед self-hosting сборкой.

Это предотвратит появление новых `^` внутри callback- или условных блоков.

## Тестирование

Проверить:

- parser tests — guard-return и match по `true`;
- resolver tests — корректные типы всех веток;
- IR tests — отсутствие внутренних `IrReturn` в compiler modules;
- Clojure backend tests — успешная генерация self-hosting-модулей;
- Clojure runtime tests — корректное выполнение всех бывших early-return путей;
- полный `niva test`;
- self-hosting pipeline: собрать NivaInNiva через Clojure backend и запустить полученный компилятор на небольшом Niva-файле.

## Критерий готовности

Self-hosting считается готовым, если:

1. исходники компилятора не содержат нелокальных возвратов;
2. Clojure backend не выбрасывает `Clojure does not support ^`;
3. Kotlin bootstrap-компилятор может скомпилировать NivaInNiva в Clojure;
4. полученный Clojure-компилятор способен повторно скомпилировать простой Niva-проект;
5. все существующие тесты проходят без изменения семантики пользовательского `^`.
