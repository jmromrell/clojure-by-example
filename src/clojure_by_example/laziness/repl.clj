(ns clojure-by-example.laziness.repl)

(comment

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Clojure Sequences
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ; Lisp and cons cells
  ; (CAR | CDR --)--> (CAR | CDR --)--> (CAR | nil)
  ;   |                 |                 |
  ;   10                20                30
  ; Essentially, a linked list of references
  ; CAR stands for "Contents of Address Register"
  ; CDR stands for "Contents of Decrement Register"

  ; Clojure lists effectively follow this pattern, except using Java's reference types

  ; Clojure's lazy sequences have similar structure, but can be thought of as CDR being either the reference OR a function which returns the reference

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Core Functions Commonly Return Lazy Sequences
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (type (map inc [1 2 3]))
  ; => clojure.lang.LazySeq

  (type (concat [1 2 3] [4 5 6]))
  ; => clojure.lang.LazySeq

  (type (take 2 [1 2 3 4 5 6]))
  ; => clojure.lang.LazySeq

  (type (for [i [1 2 3 4 5]]
          i))
  ; => clojure.lang.LazySeq

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Working With Lazy Sequences
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (defn lazy-side-affecting-sequence-of-size [size]
    (->> (iterate inc 1)
         (take size)
         (map (fn [n] (prn n) n))))

  ; first, nth, and last will realize all values up to and including the referenced value
  (let [my-lazy-seq (lazy-side-affecting-sequence-of-size 10)]
    (last my-lazy-seq))

  ; count will walk/realize entire sequence, don't use on potentially-infinite sequences
  (let [my-lazy-seq (lazy-side-affecting-sequence-of-size 10)]
    (count my-lazy-seq))

  ; cons will prepend a value to a sequence without realizing the sequence
  (let [my-lazy-seq (lazy-side-affecting-sequence-of-size 10)]
    (first (cons 100 my-lazy-seq)))

  ; concat is used to concatenate sequences without realizing them
  (let [lazy-seq-a (lazy-side-affecting-sequence-of-size 10)
        lazy-seq-b (lazy-side-affecting-sequence-of-size 10)]
    (type (concat lazy-seq-a lazy-seq-b)))

  ; rest realizes the first value in order to get reference to the rest of the sequence
  (let [my-lazy-seq (lazy-side-affecting-sequence-of-size 10)]
    (type (rest my-lazy-seq)))

  ; realized values are kept while reference to head is maintained, are not realized again if re-walked
  (let [my-lazy-seq (lazy-side-affecting-sequence-of-size 10)]
    (last my-lazy-seq) ; realizes entire sequence
    (last my-lazy-seq)) ; walks already-realized values

  (let [my-seq ((fn step [i]
                  (lazy-seq
                    (if (= 100 i)
                      (throw (ex-info "100" {:100 100}))
                      (cons i (step (inc i))))))
                0)]
    (take 105 my-seq))

  ; dorun walks the sequence (triggering any side-effects) without keeping a reference to head
  ; Note: You may be holding a reference to head elsewhere though

  (dorun (lazy-side-affecting-sequence-of-size 10))
  ; No reference to head is held, so values become eligible for garbage collection as soon as the walk passes them

  (let [my-lazy-seq (lazy-side-affecting-sequence-of-size 10)]
    (dorun 5 my-lazy-seq) ; walks
    ; head is still held because of binding to my-lazy-seq, so all realized values are held in memory still
    (dorun 3 my-lazy-seq)) ; walks already-realized values from saved reference to head

  ; doseq behaves like dorun in not holding the head of the sequence, but it lets you do bindings like a for
  (doseq [i (lazy-side-affecting-sequence-of-size 10)]
    (prn (str "doseq side effect: " i)))

  ; doall realizes the entire sequence, but maintains and returns a reference to the head of the sequence
  (doall (lazy-side-affecting-sequence-of-size 10))

  ; run! is like map, except it does not maintain a reference to the head of the sequence
  (run! #(prn (str "Doubled: " (* 2 %)))
        (lazy-side-affecting-sequence-of-size 10))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Build your own lazy sequences
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ; Using range, or otherwise mapped onto an existing lazy sequence
  (let [triangular-number-sequence (->> (range)
                                        (map (fn [i] (/ (* i (inc i))
                                                        2))))]
    (take 10 triangular-number-sequence))

  ; Using repeat
  (let [infinite-ones (repeat 1)]
    (take 10 infinite-ones))

  ; Using repeatedly
  (let [random-percentages (repeatedly rand)]
    (take 10 random-percentages))

  ; Using cycle
  (let [rectangular-inputs-to-move-northeast (cycle [:north :east])]
    (take 10 rectangular-inputs-to-move-northeast))

  ; Using iterate to generate a sequence of values as a function of the prior value
  (let [see-and-say-sequence (iterate (fn [prior-val]
                                        (->> prior-val
                                             (partition-by identity)
                                             (map #(str (count %) (first %)))
                                             (apply str)))
                                      "1")]
    (take 10 see-and-say-sequence))

  ; Mapping over an iteration of some intermediate representation
  (let [fibonacci-sequence (map second (iterate (fn [[a b]]
                                                  [b (+ a b)])
                                                [0 1]))]
    (take 10 fibonacci-sequence))

  ; Using lazy-seq
  (defn fibbonaci-sequence
    ([] (fibbonaci-sequence 0 1))
    ([a b]
     (lazy-seq
       (cons a (fibbonaci-sequence b (+ a b))))))

  (take 10 (fibbonaci-sequence))

  ; Use inline call to anonymous(ish) function to avoid needing to expose binding for step function/arity
  (let [fibonacci-sequence ((fn step [a b]
                              (lazy-seq
                                (cons a (step b (+ a b)))))
                            0 1)]
    (take 10 fibonacci-sequence))

  ; lazy-seq bad practice, forced immediate realization of first item in sequence, call lazy-seq early instead
  (let [fibonacci-sequence ((fn step [a b]
                              (cons a (lazy-seq (step b (+ a b)))))
                            0 1)]
    (take 10 fibonacci-sequence))

  ; While this looks like recursion, it is tail call optimized so walking the sequence does not add to stack depth
  (let [my-seq ((fn step [n]
                  (if (> n 10)
                    (throw (Exception. "Error"))
                    (cons n (step (inc n)))))
                1)]
    (doall my-seq))

  ; example of terminating take from clojure.core
  (defn take
    ([n coll]
     (lazy-seq
       (when (pos? n) ; returns nil when n is no longer positive, terminating sequence
         (when-let [s (seq coll)]
           (cons (first s) (take (dec n) (rest s))))))))

  ; can use concat or other functions which return sequences within lazy-seq
  (let [contrived-example-sequence ((fn step [s]
                                      (lazy-seq
                                        (when (< (count s) 4)
                                          (cons s
                                                (concat
                                                  (step (str s 0))
                                                  (step (str s 1))
                                                  (step (str s 2)))))))
                                    "")]
    contrived-example-sequence)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Printing large/lazy/infinite sequences
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (let [realized-count (atom 0)
        my-lazy-seq (->> (iterate inc 0)
                         (map (fn [n]
                                (swap! realized-count inc)
                                n)))]
    (binding [*print-length* 10] ; See lazy_tree.clj for an example of printing limited depth
      (prn my-lazy-seq)
      @realized-count))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Gotchas
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Use of `realized?` function
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (realized? (take 10 (iterate inc 1)))
  ; => false

  (realized? (range 10))
  ; => throws exception

  ; realized? is implemented for the LazySeq class, but is not a part of the interface shared by many lazy-seq-like things

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Chunking
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ; `range` will return a chunked sequence
  (let [my-lazy-seq (->> (range 50)
                         (map (fn [n] (prn n) n)))]
    (first my-lazy-seq)) ; get (and realize) first value

  ; Calling `seq` on a seqable structure can also return a chunked sequence
  (type (seq ["a" "b" "c" "d"]))
  => clojure.lang.PersistentVector$ChunkedSeq

  ; iterate does not chunk
  (let [my-lazy-seq (->> (iterate inc 0)
                         (take 50)
                         (map (fn [n] (prn n) n)))]
    (first my-lazy-seq)) ; get (and realize) first value

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Realization caused by apply
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ; applying functions to a lazy seq might realize more or less than you'd expect
  (let [my-lazy-seq (->> (iterate inc 0)
                         (take 50)
                         (map (fn [n] (prn n) n)))]
    ;(apply = '(0 1 2 3 4 5 6 ...))
    (apply = my-lazy-seq)) ; (= 0 1 2 3 ...) should theoretically only need to realize first two values

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Calling lazy functions for non-returned values
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ; failing to realize lazy sequence
  (let [my-lazy-seq (range 100)]
    ; print sequence values separated by spaces on one line
    (->> my-lazy-seq
         (interpose " ")
         (map print))
    ; print line break only at end
    (println))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Exceptions thrown from lazy sequences
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ; Exceptional results might be missed if entire sequence is not realized
  (let [results-of-delete-actions ((fn step [i]
                                     (lazy-seq
                                       (if (= 20 i)
                                         (throw (Exception. (str "Error, failed to delete item " i)))
                                         (cons {:status :success :item i} (step (inc i))))))
                                   0)]
    (take 10 results-of-delete-actions))

  ; Exceptions may be thrown while realizing a value in a lazy seq:
  ; * Potentially far from where the exception was generated
  ; * Potentially realized outside of intended catch boundaries
  (let [my-seq (try ((fn step [i]
                       (lazy-seq
                         (if (= 20 i)
                           (throw (Exception. "Error"))
                           (cons i (step (inc i))))))
                     0)
                    (catch Exception e
                      (prn "Anticipated exception was caught and handled.")))]
    (take 50 my-seq))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ; Takeaways
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  ; Avoid mixing side effects and laziness where possible
  ; Where impossible, test carefully and be cautious that you trigger only the side effects you intend

  ; Even without side-effects, realizing more than you intend can come with memory or computation costs

  )
