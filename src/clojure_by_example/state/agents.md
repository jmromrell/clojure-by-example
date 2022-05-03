# Agents

Agents are similar to atoms, except with an asychronous API.

## Using Agents

### Creating an Agent

Call [agent](https://clojuredocs.org/clojure.core/agent) with an initial value to create an agent:
```
(agent 0)
; => #object[clojure.lang.Agent 0x1ce51a74 {:status :ready, :val 0}]
```

### Getting The Value of An Agent

As with other reference types, agents can be dereferenced to get their current value:
```
(def my-agent (agent 0))

@my-agent
; => 0
```

### Changing An Agent's Value

Asynchronous mutation of an agent's value can be performed with [send](https://clojuredocs.org/clojure.core/send):
```
(def my-agent (agent 0))

(send my-agent (fn [old-val]
                 (Thread/sleep 500)
                 (inc old-val)))
; => #object[clojure.lang.Agent 0x28860481 {:status :ready, :val 0}]

@my-agent
; => 0

(Thread/sleep 1000)

@my-agent
; => 1
```

The same can also be accomplished with [send-off](https://clojuredocs.org/clojure.core/send-off):
```
(def my-agent (agent 0))

(send-off my-agent (fn [old-val]
                     (Thread/sleep 500)
                     (inc old-val)))
; => #object[clojure.lang.Agent 0xc3a002a {:status :ready, :val 0}]

@my-agent
; => 0

(Thread/sleep 1000)

@my-agent
; => 1
```

`send` is run directly on a thread from a limited thread pool for agents, to avoid overloading the CPU with too many threads.

`send-off` is run on a new thread.
`send-off` should be preferred for operations that may block for some time, to avoid locking up the limited agent thread pool.

### Failed Operations

When an exception is thrown in a `send` operation, it puts the agent in a failed state.
Already in-flight `send` operations will wait in a queue until the failure is cleared.
Newly-created `send` operations will immediately throw an exception.

The failure can be inspected with [agent-error](https://clojuredocs.org/clojure.core/agent-error).
The failure state can be cleared by calling [restart-agent](https://clojuredocs.org/clojure.core/restart-agent) on the agent with a new desired state:
```
(def my-agent (agent 0))

(send my-agent (fn [old-val]
                 (Thread/sleep 200)
                 (throw (ex-info "Operation failed!" {}))))

(send my-agent (fn [old-val]
                 (Thread/sleep 400)
                 (inc old-val)))

@my-agent
; => 0

(Thread/sleep 1000)

@my-agent
; => 0

(send my-agent (fn [old-val]
                 (Thread/sleep 400)
                 (inc old-val)))
; => Execution error (ExceptionInfo) at [...] Operation failed!

(agent-error my-agent)
; => #error{:cause "Operation failed!", ...}

(restart-agent my-agent 100)

(Thread/sleep 1000) ; Give time for queue to process

@my-agent
; => 101

; New operations can be `sent` again:

(send my-agent (fn [old-val]
                 (Thread/sleep 100)
                 (inc old-val)))

(Thread/sleep 1000)

@my-agent
; => 102
```
