(ns clojure-by-example.laziness.lazy-tree)

(defn get-lazy-collatz-tree
  "A tree where walking from any node to the root will follow the collatz sequence.
   The tree is lazy such that only the path walked through the tree is realized."
  []
  ((fn step [n]
     (if (and (zero? (mod (dec n) 3)) (> n 1))
       (list n
             (lazy-seq (step (* 2 n)))
             (lazy-seq (step (/ (dec n) 3))))
       (list n
             (lazy-seq (step (* 2 n))))))
   1))

(comment
  ; Prints a limited perspective of a large (potentially infinitely deep or wide) structure
  (binding [*print-length* 10
            *print-level* 10]
    (clojure.pprint/pprint (get-lazy-collatz-tree))))
