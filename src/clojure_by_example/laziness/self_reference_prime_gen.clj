(ns clojure-by-example.laziness.self-reference-prime-gen)

; Not particularly useful due to keeping a global reference to the head of the sequence, but interesting

(declare primes)

(defn is-prime? [n]
  (boolean
    (when (> n 1)
      (let [possible-prime-factors (take-while #(<= % (Math/sqrt n)) primes)]
        (not-any? #(zero? (mod n %)) possible-prime-factors)))))

(def primes (cons 2 (filter is-prime? (iterate #(+ % 2) 3))))
