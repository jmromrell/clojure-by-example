# State in Clojure

## Clojure Reference Types

Clojure comes with four reference types, each of which have their own guide:
* [Atoms](atoms.md)
* [Vars](vars.md)
* [Refs](refs.md)
* [Agents](agents.md)

All of Clojure's reference types support [watches](watches.md) and [validators](validators.md).

## Toggling Mutability

Clojure's core data structures are immutable by default.

However, you can temporarily enable mutability of many of these data structures using [transients](transients.md).

## Best Practices For State

In functional programming it is viewed as desirable to create pure functions wherever possible.

A function is "pure" if it:

1. Is referentially transparent (always returns the same result if given the same inputs)
   1. No randomness or time-dependency
   1. Result not influenced by the state of the system outside of the function
1. Has no side-effects (makes no changes observable outside of the function)
   1. Does not change system state
   1. Does not write to a file, database, etc.
   1. Does not kick off processes which outlive the function call

Most useful programs accept input from a user or its environment as it runs.
The purpose of most programs is to create desirable side-effects.
Most programs will involve state, be it within the application logic or the state of a database or file system.

Functional programming does not try to ignore this reality, but instead tries to limit how far state and side-effects propagate through your code.

There are a few patterns which help accomplish this goal:
1. Use of immutable data structures
1. Avoiding state in business logic, except when scoped to a function call as an implementation detail
1. Pushing I/O and use of stateful components to system edges

### Stateful Implementation of a Pure Function

The following function has internal, but is still functionally pure:
```
(defn average [number-seq]
  (let [amount (atom 0)
        total (atom 0)]
    (doseq [num number-seq]
      (swap! amount inc)
      (swap! total #(+ % num)))
    (/ total amount)))
```

Occasionally use of state has performance benefits or is significantly more clear than a stateless implementation.
Sometimes you need to interop with a stateful library or Java object.

State may be an appropriate tool in such cases, but it should be used carefully.
Limit state to the smallest scope reasonably possible.
Do not reuse state between function calls.
Keep a purely functional interface around the stateful implementation if possible.

Unless you have a compelling reason, a functional implementation is preferred:
```
(defn average [number-seq]
  (/ (apply + number-seq)
     (count number-seq)))
```

### Pushing I/O and State to System Edges

You can often limit the propagation of state through your code by limiting it to system edges.

For example, you might need to handle a request to update a database record with a mix of request data and data from a 3rd party API.
You could pass data down through a chain of function calls that eventually conclude in database requests, transforming the data along the way.
Or you could fetch all the data you need up-front and pass those inputs into pure functions which return the desired database transaction:

```
(defn get-db-transaction [request-data api-data]
  ; This may call other pure functions totalling hundreds of lines of functionally pure implementation
  ...)

(defn lookup-data-from-external-api! [id]
  ...)

(defn commit-db-transaction! [transaction]
  ...)

(defn handle-request! [payload]
  (let [api-data (lookup-data-from-external-api! (:id payload))
        db-request (get-db-transaction payload api-data)]
    (commit-db-transaction! transaction)))
```

Interacting with the DB and external API are necessarily stateful.
`handle-request!` must be stateful as a result.
However we limit handling of all I/O to `handle-request!`, the "edge" of our program that meets user input.
This enables us to keep our business logic, represented by `get-db-transaction`, functionally pure and reusable.

## References

1. https://en.wikipedia.org/wiki/Software_transactional_memory
1. https://clojure.org/reference/atoms
1. https://clojure.org/reference/agents
1. https://clojure.org/reference/refs
1. https://clojure.org/reference/vars
1. https://www.braveclojure.com/zombie-metaphysics/
1. https://github.com/bbatsov/clojure-style-guide
