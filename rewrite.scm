#!/usr/bin/guile
!#

;; A little ditty I wrote to see what a rewrite rule for transforming
;; let expressions into a lambda-based expression.  Guided the
;; maturation of sub_let.

(define (rewrite expr)
  (let ((params (map car  (cadr expr)))
        (values (map cadr (cadr expr)))
        (body   (caddr expr)))
    (cons (list 'lambda params body) values)))
(define let-expr '(let ((a 1) (b 2)) (+ a b)))
(define lambda-expr (rewrite let-expr))
(display let-expr)
(newline)
(display lambda-expr)
(newline)
