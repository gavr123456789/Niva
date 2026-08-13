# Фаза 2. Явный граф зависимостей

[← Индекс плана](clojure-repl.md)

Сейчас `NivaPkg.imports` частично наполняется resolver'ом при поиске типов и
методов. Это хорошая отправная точка, но для hot reload недостаточно полагаться
на порядок и побочные эффекты resolution.

Нужно добавить нормализующий IR-pass, который для каждого `IrPackage` собирает
все прямые local dependencies из:

- типов полей, аргументов и return type;
- receiver type и владельца вызываемой функции;
- `IrCall.declPkg`/`funcId`;
- `IrNewExpr.typeId` и type references в match;
- union branches, enum references и error types;
- generic instantiations;
- явно зарегистрированных local imports resolver'а.

Предпочтительная модель:

```text
IrDependency
  fromPkg: String
  toPkg: String
  kind: Type | Call | Constructor | Explicit
  token: Token
  symbol: String
```

В `IrPackage` можно хранить дедуплицированный список зависимостей, сохраняя
отдельную таблицу provenance для diagnostics. External Clojure imports и bind
imports не входят в local graph.

Инварианты:

- пакет не зависит от самого себя;
- каждый local target существует;
- каждый cross-package symbol имеет однозначного владельца;
- backend не угадывает владельца разбором строки, если его можно сохранить в
  IR явно.
