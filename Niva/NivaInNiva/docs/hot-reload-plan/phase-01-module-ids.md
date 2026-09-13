# Фаза 1. Стабильные module id и пути файлов

[← Индекс плана](clojure-repl.md)

Сейчас `NivaBuildSystem.findAllFilesFrom` кладёт файлы в map по
`nameWithoutExt`. Из-за этого теряются относительные директории и два файла с
одинаковым basename конфликтуют.

Нужно:

1. Определить project root как директорию entry-файла либо ближайший каталог с
   конфигурацией Niva, когда она появится.
2. Хранить для каждого source:
   - абсолютный путь для чтения и diagnostics;
   - нормализованный относительный путь;
   - стабильный module id.
3. Строить module id из относительного пути:

```text
main.niva            → main
model/user.niva      → model.user
model/user.bind.niva → внешний bind-модуль, не generated local namespace
```

4. Запрещать неоднозначные id после нормализации регистра, `-`, `_` и других
   символов.
5. Передавать реальные source paths в `SourceFile`, чтобы diagnostics и source
   maps указывали на исходный `.niva`, а не на восстановленное имя.

Результат фазы: resolver и `IrPackage.name` используют стабильные module id, а
не basename файла.
