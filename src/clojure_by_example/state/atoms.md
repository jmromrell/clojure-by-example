# Atoms

In my experience, atoms are used in the vast majority of cases where state is needed in a clojure program.

They are a good default tool to manage state unless you have reason to use something else.

Atoms hold a reference to an immutable clojure value and provide an API through which the reference may be changed.

The implementation for atom can be found [here](https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Atom.java).

## Atom API

### Create an Atom

Call [atom](https://clojuredocs.org/clojure.core/atom) with a value to return a new atom referencing that value:
```
(atom 0)
; => #object[clojure.lang.Atom 0x7ddafe16 {:status :ready, :val 0}]
```

Def `my-atom` for use in later code samples:
```
(def my-atom (atom 0))
; => #'clojure-by-example.repl/my-atom
```

### Get Atom's Current Value

You can dereference an atom to get the value it currently points to:
```
@my-atom
; => 0
```

### Set New Value for Atom

Call [reset!](https://clojuredocs.org/clojure.core/reset!) on an atom with a new value to atomically replace the atom's reference with a reference to the new value.
The new value is returned:
```
@my-atom
; => 0

(reset! my-atom 100)
; => 100
```

### Atomically Update Atom's Value

Call [swap!](https://clojuredocs.org/clojure.core/swap!) on an atom with a function, `f`, to update the atom's reference to point to the value `f(x)`, where `x` is a current value referenced by the atom.
The updated value is returned:
```
@my-atom
; => 100

(swap! my-atom inc)
; => 101
```

Note the wording "**a** current value" instead of "**the** current value."
In a multi-threaded program, multiple attempts to change the value of an atom may occur simultaneously.

See atom gotchas below for more details.

### Get Value Before and After swap!

Call [swap-vals!](https://clojuredocs.org/clojure.core/swap-vals!) to perform a swap! that returns the value before and after the swap! was performed:
```
@my-atom
; => 10

(swap-vals! my-atom inc)
; => [10 11]
```

This can be useful to determine if swap! actually changed state and to get information about state removed by swap!
```
(def queue (atom '()))

(swap-vals! queue rest)
; => [() ()]
; Note: Before and after are equal. State was not changed. No item was claimed.

(reset! queue '(1 2 3))
; => (1 2 3)

(swap-vals! queue rest)
; => [(1 2 3) (2 3)]
; Note: Before and after are not equal. Can see `1` was claimed, which would not be returned by swap!.
```

### Conditionally Set Atom Value

Call [compare-and-set!](https://clojuredocs.org/clojure.core/compare-and-set!) on an atom to replace the atom's value with a new value, but only if its current value matches the provided old value.
Return true if the new value was set, false otherwise:
```
@my-atom
; => 1

(compare-and-set! my-atom -1 10)
; => false

@my-atom
; => 1

(compare-and-set! my-atom 1 10)
; => true

@my-atom
; => 10
```

This is how swap! updates the atom only if the current value hasn't changed since swap! began.

There are cases it is useful to call this yourself, but most often you will want to use swap! rather than managing this yourself.

## Atom Gotchas

### swap! Function Run Multiple Times

swap! gets the currently referenced value, `x1`, and runs your function on it to generate `f(x1)`.
It atomically updates the atom's reference to point to `f(x1)` only if the atom state equals `x1`.
Otherwise it will get the new state `x2` and repeat.

This guarantees swap! only transitions the atom to state `f(x)` if the preceding state is `x`.
However, `x` might not be the atom's state at the time `f` was called, and *`f` might be called more than once*.

Demonstration:
```
(do (future (Thread/sleep 100) ; Delay reset to occur during swap below 
            (reset! my-atom 0))

    (swap! my-atom
           (fn [old-val]
             (Thread/sleep 200) ; Simulate function taking some time
             (let [new-val (inc old-val)]
               (printf "Attempting to increment my-atom from %d to %d.\n" old-val new-val)
               new-val))))
; Attempting to increment my-atom from 101 to 102.
; Attempting to increment my-atom from 0 to 1.
; => 1
```

`f` must be free from side effects which cannot be repeated and should be inexpensive to recompute.
This becomes especially important if the atom may frequently see simultaneous updates.

If you cannot meet these criteria, another reference type might be a better fit, or you might consider synchronizing access to the resource using [locking](https://clojuredocs.org/clojure.core/locking).

### State Reset When Namespace Loaded

*Usually* it is wrong to `(def state (atom initial-value))`.
If you define an atom this way, whenever the namespace is evaluated it will redefine `state` with its initial value, blowing away any previously held value.

Most often this merely causes unexpected behavior during local development in one's repl.
However, it can be possible to attach a repl to a running service, in which case someone loading a namespace could actually corrupt service state.

To solve this use `(defonce state (atom initial-value))`. This will only define `state` if that symbol is not already defined.

### State Changes Between Atom Operations

It is an easy and subtle mistake to dereference an atom and treat the dereferenced value as a valid representation of the atom's current state.
In reality, another thread may change the atom's state immediately after a dereference.

For example, this is buggy:
```
(defn run-job-from-queue! []
  (let [claimed-job (first @job-queue)]
    (when claimed-job
      (swap! job-queue remove-first-job)
      (run-job claimed-job))))
```

Two threads might run `(first @job-queue)` at the same time.

Both threads will end up running job1, but both job1 and job2 will be dropped.

Fixed Implementation:
```
(defn run-job-from-queue! []
  (let [[old-queue new-queue] (swap-vals! job-queue remove-first-job)]
    (when (not= old-queue new-queue)
      (let [claimed-job (first old-queue)]
        (run-job claimed-job))))) 
```
