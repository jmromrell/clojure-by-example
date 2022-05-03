# Vars

Vars are the most common reference type by far, as they are what backs every call to `def` and `defn`.

In regards to application state, however, there is a variety of vars called "dynamic vars."
You may have seen these already. They are distinguished by an "earmuffed" naming convention: `*in*`, `*out*`, `*print-level*`, etc.

These dynamic vars are given a global root value which may be rebound within the scope of a thread.

## Use of Dynamic Vars

### Create a Dynamic Var

Dynamic vars are defined like regular vars, except with the metadata `^{:dynamic true}` (`^:dynamic`, for short), and with the conventional earmuffed naming:
```
(def ^:dynamic *my-var* 0)
; => #'clojure-by-example.repl/*my-var*
```

### Reference a Dynamic Var

You reference a dynamic var just as you would a normal var:
```
*my-var*
; => 0
```

### Rebind a Dynamic Var

You can rebind a dynamic var for a given scope using the `binding` macro:
```
(binding [*my-var* 1]
  *my-var*)
; => 1
```

This is useful to rebind clojure.core configuration vars:
```
(binding [*print-length* 2
          *print-level* 2]
  (prn {:a {:b {:c {:d {}}}}
        :v [1 2 3 4 5]}))
; Prints: {:a {:b #}, :v [1 2 ...]}
; => nil
```

Bindings are kept on a stack and get pushed when you enter a binding macro and popped when you leave it:
```
(def ^:dynamic *my-var* 0)

(do (binding [*my-var* 1]
      (prn *my-var*)
      (binding [*my-var* 2]
        (prn *my-var*))
      (prn *my-var*))
    (prn *my-var*))
; Prints: 1
; Prints: 2
; Prints: 1
; Prints: 0
; => nil
```

### Rebinding Functions

You can rebind functions, even if they have not been marked `^:dynamic`, using [with-redefs](https://clojuredocs.org/clojure.core/with-redefs):
```
(defn foo [a b]
  (+ a b))

(with-redefs [foo (fn [a b] (vector a b))]
  (foo 1 2))
; => [1 2]
```

This is extremely useful for mocking arbitrary functions in tests.

## Dynamic Vars Gotchas

Dynamic vars have some really subtle gotchas that can cause major problems that are extremely difficult to debug.
As a result I generally discourage their use in production code unless you are extremely confident you understand their limitations.

A major Clojure contributor, Stuart Sierra, [warned](https://stuartsierra.com/2013/03/29/perils-of-dynamic-scope):

> Friends don't let friends use dynamic scope

### Inconsistent Conveyance to Child Threads

It once was the case that dynamic vars were not preserved when a new thread was created unless you set them on the new thread yourself.

Now, dynamic var bindings are [conveyed by default](https://clojure.org/reference/vars#conveyance) for threads created by futures, agents, pmap, and a few other cases.

However, they will not be conveyed to threads created by other means, such as with Threads created from Java:
```
(def ^:dynamic *my-var* 0)

(binding [*my-var* 1]
  (.start (Thread. (fn [] (prn *my-var*)))))
; Prints: 0
; => nil
```

This can result in unexpected behavior when making use of Java libraries or clojure wrappers around Java libraries.
This may occur with thread pools used by Java http server libraries, database libraries, http clients, etc.

### with-redefs Does Not Convey Bindings To Child Threads

`with-redefs` works by temporarily altering the var root, not rebinding its value, so there are no bindings to be conveyed:
```
(def ^:dynamic foo (fn [a b] (+ a b)))

@(binding [foo vector]
   (future
     (Thread/sleep 1000)
     (println (foo 1 2))))
; Prints: [1 2]
; => nil

@(with-redefs [foo vector]
   (future
     (Thread/sleep 1000)
     (println (foo 1 2))))
; Prints: 3
; => nil
```

### with-redefs Is Not Thread-Safe

`with-redefs` saves the current var root, updates the var root, and then restores the saved var root afterwards.
If multiple threads using `with-redefs` on the same function simultaneously, a race condition may leave the root var permanently changed:
```
(defn foo [a b]
  (+ a b))

(doall (pmap (fn [_] (with-redefs [foo vector]
                       (foo 1 2)))
             (range 100)))

(foo 1 2)
; => [1 2]
; (Should have been 3)
```

### Rebindings Are Not Preserved In Lazy Sequence Generation

Bindings are popped upon leaving the scope of the binding macro.
This may result in unrealized lazy sequences using a different value for different sequence elements, depending on when they are realized:
```
(def ^:dynamic *my-var* 0)

(let [my-lazy-seq (binding [*my-var* 1]
                    (iterate (fn [_] *my-var*) *my-var*))]
  (Thread/sleep 1000)
  (take 10 my-lazy-seq))
; => (1 0 0 0 0 0 0 0 0 0)
```

### Using Dynamic Vars for Resources Forces Resource to be a Singleton

It is tempting to expose a resource required by a library or module as a dynamic var.
However, this includes the implicit assumption that a thread will only ever use one instance of that resource at a time.
That assumption might not always remain true and may result in a difficult refactor.

For example, it isn't uncommon for apps to have simultaneous connections to multiple databases.
If a database wrapping library used a dynamic var for the database connection it would make it impossible for one thread to work with two databases at one time.
