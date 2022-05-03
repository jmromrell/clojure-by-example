# Refs

Refs are a lot like atoms, except you can safely coordinate changes across multiple refs simultaneously.

## Software Transactional Memory

[Software Transactional Memory (STM)](https://en.wikipedia.org/wiki/Software_transactional_memory) is a mechanism for managing in-memory state using transactions.

A transaction prepares a change to affected memory in a manner that does not expose intermediate states to other threads.
Transactions conclude with an atomic commit, which atomically applies changes to affected state, making changes visible to other threads.

Transactions may be aborted or restarted before being committed.
A transaction might be aborted if the result of the transaction is invalid.
A transaction might be restarted if state it relies on has changed.

## Unsafe Function Naming Convention

bbatsov's [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide) includes a naming recommendation for "unsafe functions":

> The names of functions/macros that are not safe in STM transactions should end with an exclamation mark (e.g. reset!).

clojure.core follows this convention and seen previously in functions like `reset!` and `swap!`.

Functions which may have side-effects visible to other threads or processes (such as mutating state not contained entirely within the function) are unsafe.
As are functions that cannot be safely retried.

A function which writes to a file would be considered unsafe, as would a function which changes records in a database.

### Tic Tac Toe Example

```
(defn play-tic-tac-toe []
  (let [game-state (atom NEW_GAME_STATE)]
    (while (not (game-ended? game-state))
      (take-turn! game-state))))
```

`take-turn!` is "unsafe" because it manipulates state that lives outside of `take-turn!`.
If I called `take-turn!` within a transaction and the transaction aborts, changes to game state would not be rolled back.
If the transaction were restarted, it may result in two turns being taken.

I don't consider `play-tic-tac-toe` "unsafe" because `game-state` lives and dies with a given invocation of `play-tic-tac-toe`.
It is harmless to blow away and recreate that state, as nothing outside of `play-tic-tac-toe` can see it.

`game-ended?` is not "unsafe" despite involving state because it reads, not mutates, the state.

## How to Use Refs

### Create A Ref

Call [ref](https://clojuredocs.org/clojure.core/ref) on an initial value to create a ref:
```
(ref 0)
; => #object[clojure.lang.Ref 0x78f4151b {:status :ready, :val 0}]
```

### Get A Ref's Value

As with atoms, you can dereference a ref to gets its current value:
```
(def some-ref (ref 0))

@some-ref
; => 0
```

### Ref Transactions

Ref mutation is only allowed to occur within a transaction.
A transaction occurs within a [dosync](https://clojuredocs.org/clojure.core/dosync) macro.
The transaction will attempt to commit once the end of the macro is reached.

### Reset A Ref's Value

You can set a ref to an explicit value using [ref-set](https://clojuredocs.org/clojure.core/ref-set).
This behaves like `reset!` for atoms:
```
(def some-ref (ref 0))

(dosync
  (ref-set some-ref 10))
; => 10

@some-ref
; => 10
```

As with all ref mutation, changes are not visible until after the transaction has completed:
```
(def some-ref (ref 0))

(future (dosync
          (ref-set some-ref 10)
          (Thread/sleep 500))) ; Simulate a long-running transaction

; At this point `ref-set` has been called above, but the transaction has not been committed
@some-ref
; => 0

(Thread/sleep 1000) ; Wait for transaction to complete

@some-ref
; => 10
```

### Mutate A Ref With A Function

You can apply a function to mutate a ref with [alter](https://clojuredocs.org/clojure.core/alter).
This behaves like `swap!` for atoms, except changes are not visible outside of the transaction until it is committed:
```
(def some-ref (ref 0))

(dosync
  (alter some-ref inc))
; => 1

@some-ref
; => 1
```

### A Multi-Ref Transaction

Refs enable you to create safe transactions involving multiple separate refs:
```
; Map account-ids to account-balance
(def accounts (ref {1 10000
                    2 5000
                    3 50
                    4 1000000}))

; Pending transfers between accounts
(def pending-transfers
  (ref [{:from 1 :to 2 :amount 1000}
        {:from 3 :to 4 :amount 50}]))

(defn apply-next-transfer! []
  (dosync
    (let [{:keys [from to amount]} (first @pending-transfers)]
      (alter pending-transfers (comp vec rest))
      (alter accounts (fn [accounts] (update accounts from #(- % amount))))
      (alter accounts (fn [accounts] (update accounts to #(+ % amount)))))))

(apply-next-transaction!)
; => {1 9000, 2 6000, 3 50, 4 1000000}

(apply-next-transaction!)
; => {1 9000, 2 6000, 3 0, 4 1000050}
```

### Failing A Transaction

A transaction will fail if an exception is thrown from it:
```
(def accounts (ref {1 10000
                    2 5000
                    3 50
                    4 1000000}))

(def pending-transfers
  (ref []))

(defn apply-next-transfer! []
  (dosync
    (when (> 1 (count @pending-transfers))
      (throw (IllegalStateException. "Cannot apply transaction: No pending transfers")))

    (let [{:keys [from to amount]} (first @pending-transfers)]
      (alter pending-transfers (comp vec rest))
      (alter accounts (fn [account-map] (update account-map from #(- % amount))))
      (alter accounts (fn [account-map] (update account-map to #(+ % amount))))

      (when (neg? (get @accounts from))
        (throw (IllegalStateException. "Cannot apply transaction: 'From' account does not have sufficient funds"))))))

(apply-next-transfer!)
; => Execution error (IllegalStateException) at [...] Cannot apply transaction: No pending transfers

; Add a transfer that would result in an illegal state
(dosync (ref-set pending-transfers [{:from 3 :to 4 :amount 1000}]]))

(apply-next-transfer!)
; => Execution error (IllegalStateException) at [...] Cannot apply transaction: 'From' account does not have sufficient funds
```

No change to state occurred as transactions failed to commit:
```
@accounts
; => {1 10000, 2 5000, 3 50, 4 1000000}

@pending-transfers
; => [{:from 3, :to 4, :amount 1000}]
```

### Validators

Validators have their own guide.
Here, I will only call out that they exist and can be used to automatically fail transactions which result in an illegal state.
This way you do not need to manually assert state validity after every transaction:
```
(def accounts (ref {1 10000
                    2 5000
                    3 50
                    4 1000000}
                   :validator #(not-any? neg? (vals %))))

(def pending-transfers (ref [{:from 3 :to 4 :amount 1000}]))

(defn apply-next-transfer! []
  (dosync
    (when (> 1 (count @pending-transfers))
      (throw (IllegalStateException. "Cannot apply transaction: No pending transfers")))

    (let [{:keys [from to amount]} (first @pending-transfers)]
      (alter pending-transfers (comp vec rest))
      (alter accounts (fn [account-map] (update account-map from #(- % amount))))
      (alter accounts (fn [account-map] (update account-map to #(+ % amount)))))))

(apply-next-transfer!)
; => Execution error (IllegalStateException) at [...] Invalid reference state
```

As before, no changes were committed:
```
@accounts
; => {1 10000, 2 5000, 3 50, 4 1000000}

@pending-transfers
; => [{:from 3, :to 4, :amount 1000}]
```

### Transaction Retry Behavior

Like `swap!` for atoms, transactions may end up getting retried.
Unlike `swap!`, transactions will attempt to acquire locks on each ref referenced and will retry immediately if any lock cannot be acquired:
```
(def some-ref (ref []))

(future (Thread/sleep 500) ; Delay this transaction to start during following transaction
        (dosync
          (println "Starting second transaction")
          (alter some-ref #(conj % 2))
          (println "Committing second transaction")))

(dosync
  (println "Starting first transaction")
  (alter some-ref #(conj % 1))
  (Thread/sleep 1000) ; Simulate long-running transaction
  (println "Committing first transaction"))

; Prints: Starting first transaction
; Prints: Starting second transaction
; Prints: Starting second transaction
; (repeats many times)
; Prints: Committing first transaction
; Prints: Starting second transaction
; Prints: Committing second transaction
```

Despite one of the transactions being applied many times, each is only committed once:
```
@some-ref
; => [1 2]
```

### Avoid Locking On A Ref

Sometimes such locking behavior is undesirable and you want behavior closer to `swap!`.
This can be accomplished by using [commute](https://clojuredocs.org/clojure.core/commute) in place of `alter`:
```
(def attempts (ref []))
(def ref-a (ref 0))
(def ref-b (ref 0))

(future (Thread/sleep 500) ; Delay this transaction to start during following transaction
        (dosync
          (println "Starting second transaction")
          (commute attempts #(conj % 2))
          (ref-set ref-a 10)
          (println "Committing second transaction")))

(dosync
  (println "Starting first transaction")
  (commute attempts #(conj % 1))
  (Thread/sleep 1000) ; Simulate long-running transaction
  (ref-set ref-b 20)
  (println "Committing first transaction"))

; Printed: Starting first transaction
; Printed: Starting second transaction
; Printed: Committing second transaction
; Printed: Committing first transaction
```

This allowed the transactions to overlap without conflict.
Commute gets applied to the value of the ref at time of commit, so invalid states due to race conditions are avoided:
```
@attempts
; => [2 1]

@ref-a
; => 10

@ref-b
; => 20
```

## Ref / Transaction Gotchas

I have almost no experience using refs. These are initial observations, not my actual experience.

### Avoid Use Of "Unsafe" Functions Within Transactions

Unsafe operations may be unintentionally repeated if a transaction must restart:
```
(def some-atom (atom 0))
(def expected-atom-val (ref 0))
(def shared-ref (ref []))

(future (Thread/sleep 500) ; Delay this transaction to start during following transaction
        (dosync
          (swap! some-atom inc)
          (alter expected-atom-val inc)
          (alter shared-ref #(conj % 2))))

(dosync
  (alter shared-ref #(conj % 1))
  (Thread/sleep 1000)) ; Simulate long-running transaction

@some-atom
; => 6

@expected-atom-val
; => 1
```

### Contention May Affect Performance And Transaction Order

In this example, 100 transactions taking ~10ms each should complete in about 1000ms:
```
(def start-time (System/currentTimeMillis))
(def commit-order (ref []))

(->> (range 100)
     (map (fn [i]
            (future (dosync
                      (alter commit-order #(conj % i))
                      (Thread/sleep 10)))))
     doall ; Remove laziness, start all futures now
     (map deref)) ; Block on completion of all futures

(def time-elapsed (- (System/currentTimeMillis) start-time))
```

In reality it takes about 50% longer in this case:
```
time-elapsed
; => 1552
```

The transactions are also not completed in the order they were started:
```
(sorted? @commit-order)
; => false
```

Performance can be dramatically increased by using `commute` to allow transactions to process concurrently, but it still doesn't guarantee transaction order:
```
(def start-time (System/currentTimeMillis))
(def commit-order (ref []))

(doseq [i (range 100)]
  (future ))

(->> (range 100)
     (map (fn [i]
            (future (dosync
                      (commute commit-order #(conj % i))
                      (Thread/sleep 10)))))
     doall ; Remove laziness, start all futures now
     (map deref)) ; Block on completion of all futures

(def time-elapsed (- (System/currentTimeMillis) start-time))

time-elapsed
; => 38

(sorted? @commit-order)
; => false
```