# Фаза 3. Детектирование циклов

[← Индекс плана](clojure-repl.md)

Статус: завершена 2026-08-13. Реализация и проверки зафиксированы в
[progress.md](progress.md).

Перед code generation построить ориентированный граф:

```text
package → packages required by it
```

и найти strongly connected components алгоритмом Tarjan или Kosaraju за
`O(V + E)`.

В первой версии любая SCC размером больше одного является compile error.
Diagnostic должен показывать не только список файлов, но и конкретный цикл с
причинами рёбер:

```text
Cyclic Niva module dependency:
  model.user.niva:18  model.user -> service.auth  (call Auth validate:)
  service.auth.niva:9 service.auth -> model.user  (type User)
```

Self-reference внутри одного файла циклом модулей не считается.

### Можно ли автоматически разрешать циклы

Автоматически «вынести общую часть» в отдельный файл в общем случае нельзя:
граница может проходить через типы, constructors, eager initializers и
состояние, а выбор публичного интерфейса является архитектурным решением.

Алгоритмически возможно другое решение: объединить каждый SCC целиком в один
generated Clojure namespace, а затем строить DAG из SCC. Текущий mangling уже
содержит package id, поэтому коллизии имён в объединённом namespace в основном
разрешимы.

SCC coalescing не входит в первую реализацию, потому что при появлении или
исчезновении цикла меняются namespace names, пути generated-файлов и identity
состояния. Его можно добавить позднее как отдельный режим после стабилизации
обычной перезагрузки.
