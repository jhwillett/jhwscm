#!/usr/bin/guile
!#

(define (print . args) 
  (define (p arglist) 
    (if (null? arglist)
        (newline)
        (begin (display (car arglist)) (display #\space) (p (cdr arglist)))))
  (p args))

(define (test func expr expect)
  (let ((result (func expr)))
    (print 'testing
           expr
           expect
           result
           (if (equal? expect result) 'ok 'bad))))

(test car '(1 2) 1)
(test cdr '(1 2) '(2))
(print)

(define (manytest func list)
  (if (null? list)
      (print 'done)
      (let ((first (car list))
            (rest  (cdr list)))
        (let ((expr   (car  first))
              (expect (cadr first)))
          (test func expr expect)
          (manytest func rest)))))
        
(define trials '((1 1) (1 2) (a a) (a b)))

(manytest (lambda (x) x) trials)
(print)

(define (sub_interp_atom chars)
  (let ((first (car chars))
        (rest  (cdr chars)))
    (if (eq? '- first)
        (if (null? rest) 
            (cons 'symbol chars)
            (let ((num (sub_interp_number rest 0)))
              (if (null? num) 
                  (cons 'symbol chars)
                  (- num))))
        (let ((num (sub_interp_number chars 0)))
          (if (null? num) 
              (cons 'symbol chars)
              num)))))

(define (sub_interp_number chars accum)
  (if (null? chars)
      accum
      (let ((head (car chars))
            (tail (cdr chars))) 
        (case head
          (( 0 1 2 3 4 5 6 7 8 9 ) 
           (sub_interp_number tail (+ head (* 10 accum))))
          (else '())))))

(define number_trials '(
                      (( a )        ())
                      (( a b )      ())
                      (( 0 )        0)
                      (( 1 )        1)
                      (( 0 1 )      1)
                      (( 1 2 3 )    123)
                      (( - 0 )      ())
                      (( - 1 )      ())
                      (( - 0 1 )    ())
                      (( - 1 2 3 )  ())
                      (( - )        ())
                      (( b - )      ())
                      (( - b )      ())
                      (( - 1 )      ())
                      (( - 1 2 )    ())
                      (( - 1 b )    ())
                      ))

(manytest (lambda (x) (sub_interp_number x 0)) number_trials)
(print)

(define atom_trials '(
                      (( a )        (symbol a))
                      (( a b )      (symbol a b))
                      (( 0 )        0)
                      (( 1 )        1)
                      (( 0 1 )      1)
                      (( 1 2 3 )    123)
                      (( - 0 )      0)
                      (( - 1 )      -1)
                      (( - 0 1 )    -1)
                      (( - 1 2 3 )  -123)
                      (( - )        (symbol -))
                      (( b - )      (symbol b -))
                      (( - b )      (symbol - b))
                      (( - 1 )      -1)
                      (( - 1 2 )    -12)
                      (( - 1 b )    (symbol - 1 b))
                      ))

(manytest sub_interp_atom atom_trials)
(print)
