# Validators

All of clojure's reference types support validators.

A validator is a function which accepts a single argument and returns falsey or throws an exception for invalid state values.

If a reference type has a validator function, it will reject any update that would result in an invalid value by throwing an exception.

## Setting a Validator

Call [set-validator!](https://clojuredocs.org/clojure.core/set-validator!) on a reference type to add a validator to it:
```
(def my-atom (atom 0))
; => #'clojure-by-example.repl/my-atom

(set-validator! my-atom even?)
; => nil

(swap! my-atom inc)
; => Execution error (IllegalStateException) at [...] Invalid reference state

(swap! my-atom #(+ % 2))
; => 2
```

The validator function is called on the referenced value when set.
set-validator! will fail to set the validator if the current value is not conformant:
```
@my-atom
; => 2

(set-validator! my-atom odd?)
; => Execution error (IllegalStateException) at [...] Invalid reference state
```

## Removing a Validator

To remove a validator just set the validator to `nil`:
```
(def my-atom (atom 0 :validator even?))
; => #'clojure-by-example.repl/my-atom

(swap! my-atom inc)
; => Execution error (IllegalStateException) at [...] Invalid reference state

(set-validator! my-atom nil)
; => nil

(swap! my-atom inc)
; => 1
```

## Get The Current Validator

Call [get-validator](https://clojuredocs.org/clojure.core/get-validator) on a reference type to get its current validator function, or else nil:
```
(def my-atom (atom 0))
; => #'clojure-by-example.repl/my-atom

(get-validator my-atom)
; => nil

(set-validator! my-atom false?)
; => nil

(def validator-fn (get-validator my-atom))
; => #'clojure-by-example.repl/validator-fn

(validator-fn 0)
; => true

(validator-fn 1)
; => false
```

## Throwing Your Own Exceptions

A value that results in an exception from the validator function will be considered invalid.

The exception thrown by the validator function will get thrown from the attempted update operation instead of the default IllegalStateException:
```
(defn validator-fn [x]
  (if (even? x)
    true
    (throw (ex-info "Validation failed!" {:value x}))))
; => #'clojure-by-example.repl/validator-fn

(def my-atom (atom 0 :validator validator-fn))
; => #'clojure-by-example.repl/my-atom

(swap! my-atom inc)
; => Execution error (ExceptionInfo) at [...] Validation failed!

(try (swap! my-atom inc)
  (catch clojure.lang.ExceptionInfo e
    (ex-data e)))
; => {:value 1}
```
