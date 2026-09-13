# Фаза 5. Namespace-per-Niva-file в Clojure backend

[← Индекс плана](clojure-repl.md)

Backend должен возвращать не один `String`, а набор generated-файлов:

```text
CljOutput
  files: Map(RelativePath, String)
  entryNamespace: String
  manifest: CljManifest
```

Пример mapping:

```text
IrPackage main       → niva.generated.main       → niva/generated/main.clj
IrPackage model.user → niva.generated.model.user → niva/generated/model/user.clj
```

Точный root-prefix должен быть единым и детерминированным. Если один JVM может
одновременно держать несколько Niva-проектов, в prefix нужно добавить
стабильный project id.

Для каждого `IrPackage` backend отдельно испускает:

- `(ns ...)`;
- `:require` всех direct local dependencies;
- external/bind requirements;
- только принадлежащие пакету types, unions, enums и functions;
- локальные `declare` для forward references внутри namespace.

### Cross-package references

Сейчас функции всех пакетов находятся в одном namespace и вызываются
неквалифицированно. После разделения требуется `CljPackageContext` с текущим
package и alias table.

Backend должен квалифицировать:

- вызовы функций и specialized generic functions;
- constructors и record classes;
- enum Vars;
- type checks в match;
- ссылки на union branches и error records.

Пример:

```clojure
(ns niva.generated.service
  (:require
   [niva.generated.model :as model]
   [niva.core :as niva-core]))

(defn service__User__save [this]
  (model/model__User__validate this))
```

Alias должен строиться детерминированно и не зависеть от порядка обхода map.
