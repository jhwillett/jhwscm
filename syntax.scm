#!/usr/bin/guile
!#

;; To learn how Scheme's define-syntax works, I've had to search far
;; and wide for good coverage.  R5RS is really vague.
;;
;; Here, I try some great, clean examples from:
;;
;;   http://blog.willdonnelly.net/2008/09/04/a-scheme-syntax-rules-primer/
;;
;; Good man, Will Donnelly!

(use-modules (ice-9 r5rs))
(use-syntax (ice-9 syncase))

;; Guile lacks a prior definition for (print), no problem!
;;
(define (print x) (display x) (newline))

;; WD's example: A simple while loop.
;;
;; Prints 5 lines: 1, 2, 3, 4, 5.
;;
(define x 0)
(while (< x 5)
  (set! x (+ x 1))
  (print x))

;; WD's definition for (while), named (wdWhile) to avoid redefining
;; Guile's native (while).
;;
;; SURPRISE!  Guile whined:
;;
;;  ERROR: Unbound variable: define-syntax
;;
;; Up above, I added:
;;
;;   (use-modules (ice-9 r5rs))
;; 
;; and then get:
;;
;;  ERROR: invalid syntax ()
;;
;; Which I suspect may be that funky (let) expression.
;;
;; More webhunting, and I find I really wanted:
;;
;;  (use-syntax (ice-9 syncase))
;;
;; With which, surprisingly, the funny (let) expression in the
;; original wdWhile is accepted.
;;
(define-syntax wdWhile
  (syntax-rules ()
    ((wdWhile condition body ...)
     (let loop ()
       (if condition
           (begin
             body ...
             (loop))
           #f)))))

;; Calling WD's form.  Note x is still live from before, w/ value 5,
;; so this should print 5 lines: 6, 7, 8, 9, 10.
;;
;;
(print "GOT THIS FAR")
(wdWhile (< x 5)
  (set! x (+ x 1))
  (print x))
(print "GOT ULTIMA FAR")
