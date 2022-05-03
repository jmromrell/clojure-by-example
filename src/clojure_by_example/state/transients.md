# Transients

Transients give you the ability to temporarily enable a mutable API on some of clojure's core data structures.

You enable mutability by calling [transient](https://clojuredocs.org/clojure.core/transient) on an immutable data structure.
You restore immutability by calling [persistent!](https://clojuredocs.org/clojure.core/persistent!) on a transient data structure:
```
(let [v1 [1 2 3]
      v-transient (transient v1)]
  (conj! v-transient 4)
  (conj! v-transient 5)
  (let [v2 (persistent! v-transient)]
    {:v1 v1
     :v2 v2}))
; => {:v1 [1 2 3], :v2 [1 2 3 4 5]}
```

This can be useful for performance, as intermediate copies of the structure did not need to be created after each mutation.

The read API is maintained between transient and persistent modes:
```
(let [v (transient [1 2 3])]
  {:count (count v)
   :nth (nth v 1)})
; => {:count 3, :nth 2}
```

Transient mutations cannot be performed on persistent data or vice versa:
```
(let [v (transient [1 2 3])]
  (conj v 4))
Execution error (ClassCastException) at [...] class clojure.lang.PersistentVector$TransientVector cannot be cast to class clojure.lang.IPersistentCollection (clojure.lang.PersistentVector$TransientVector and clojure.lang.IPersistentCollection are in unnamed module of loader 'app')

(let [v [1 2 3]]
  (conj! v 4))
Execution error (ClassCastException) at [...] class clojure.lang.PersistentVector cannot be cast to class clojure.lang.ITransientCollection (clojure.lang.PersistentVector and clojure.lang.ITransientCollection are in unnamed module of loader 'app')
```
