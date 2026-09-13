# Инвентарь `^` в self-hosting-части

Этот файл фиксирует первый этап плана `self-hosting-without-early-returns.md`.
Инвентарь ограничен исходниками компилятора и backend-а; тесты, строки с
пользовательским Niva-кодом, union-конструкторы и embedded Clojure/Kotlin не
считаются кандидатами на удаление.

## Категории

| Категория | Что с ней делать | Примеры |
| --- | --- | --- |
| Финальный результат метода | Убрать `^`; выражение оставить последним выражением body | `compiler/compiler.niva:36`, `front/parser/parse.niva:300`, `ir/fromTypedAst.niva:780` |
| Guard-return | Переписать в match по `true`; несколько последовательных guard-ов объединить | `compiler/compiler.niva:103-109`, `argParse/cliArgs.niva:17-25`, `front/resolver/resolveExpr2.niva:179-210` |
| Возврат из callback | Сначала получить значение/состояние снаружи callback, либо заменить callback-обход на явное ветвление | `front/parser/parse.niva:521`, `front/resolver/typeDB.niva:245`, `front/resolver/typeDB.niva:941-953`, `ir/fromTypedAst.niva:112` |
| Возврат из цикла | Вынести результат цикла в локальное состояние или match и завершить body после цикла | `ir/fromTypedAst.niva:300`, `front/resolver/typeDB.niva:413-420` |
| Легитимный `^` | Не изменять: это синтаксис пользовательской программы или generated/embedded-код | `front/parser/astTypes/ast.niva:6`, `front/resolver/nivaTypes.niva:12`, `compiler/compilerConstants.niva:68`, тесты |

## Приоритетный охват

Число совпадений ниже — это грубый лексический инвентарь, а не число
нелокальных возвратов: в нём присутствуют финальные `^`, комментарии и
embedded-код. Колонка `guard` считает только очевидные формы
`ifTrue: [^ ...]`/`ifFalse: [^ ...]`, `callback` — очевидные `^` в `unpack`-
блоках.

| Файл | Все `^` | Guard | Callback | Основная работа |
| --- | ---: | ---: | ---: | --- |
| `compiler/compiler.niva` | 10 | 2 | 0 | guards в состоянии watch queue |
| `compiler/incremental.niva` | 25 | 6 | 0 | guards в commit/compile pipeline |
| `argParse/cliArgs.niva` | 11 | 5 | 0 | последовательные guards парсеров аргументов |
| `front/parser/parse.niva` | 63 | 2 | 1 | `tryParseDestructingAssign`, parser callbacks |
| `front/parser/parseAstType.niva` | 8 | 1 | 0 | ранняя ошибка parse-типа |
| `front/resolver/typeDB.niva` | 87 | 19 | 6 | поиск типов/методов, `unpack` и обходы |
| `front/resolver/resolveExpr2.niva` | 25 | 4 | 0 | guards разрешения выражений |
| `ir/fromTypedAst.niva` | 34 | 6 | 2 | bind/while преобразования AST в IR |
| `back/clojureBackend/cljEmit.niva` | 35 | 2 | 0 | инвариант `IrReturn` и диагностика |

## Исключения, которые нужно сохранить

- `ReturnStatement` в AST, typed AST и IR и поддержку пользовательского `^`.
- Union-конструкторы `| ^Expr`.
- `^` внутри строк с исходниками пользовательских программ в тестах.
- Clojure metadata/type hints (`^:private`, `^String`, `^Runnable` и т. п.).
- Лексер и parser-пути, где `^` является токеном входной программы, а не
  возвратом реализации.

## Порядок обработки после инвентаризации

1. Сначала финальные `^` в приоритетных файлах — механическая и безопасная
   часть.
2. Затем guards, группируя последовательные guards в один `match true`.
3. Затем callback- и loop-return, с отдельными промежуточными результатами.
4. После каждого слоя проверять, что пользовательские тесты на `^` остались
   неизменными.
