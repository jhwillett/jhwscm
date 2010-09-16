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

;;(use-modules (ice-9 r5rs))
(use-syntax (ice-9 syncase))

;; Guile lacks a prior definition for (print), no problem!
;;
(define (print x) (display x) (newline))

;; WD's example: A simple while loop.
;;
;; Prints 5 lines: 1, 2, 3, 4, 5.
;;
(print "while")
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
;; original (define wdWhile ...) is accepted.
;;
;; Calling WD's form: it works!
;;
(print "wdWhile")
(define-syntax wdWhile
  (syntax-rules ()
    ((wdWhile condition body ...)
     (let loop ()
       (if condition
           (begin
             body ...
             (loop))
           #f)))))
(define x 0)
(wdWhile (< x 5)
  (set! x (+ x 1))
  (print x))
(print x)    ;; x is 5 now!

;; My rewrite wdWhile2, out of paranoia of the weird (let), does not
;; work.
;;
;; It seems to not iterate: it only prints 1, then complains of:
;;
;;   ERROR: Unbound variable: loop
;;
;; Duh.  Can't recurse with a binding defined w/ plain let.  Changing
;; it to letrec, and wdWhile2 works as well.
;;
(print "wdWhile2")
(define-syntax wdWhile2
  (syntax-rules ()
    ((wdWhile2 condition body ...)
     (letrec ((loop (lambda ()
                   (if condition
                       (begin
                         body ...
                         (loop))
                       #f))))
       (loop)))))
(define x 0)
(wdWhile2 (< x 5)
  (set! x (+ x 1))
  (print x))
(print x)    ;; x is 5 now!

;; Trying some examples from R5RS sec 7.5:
;;
;; Sweet, these work!
;;
(print "and2")
(define-syntax and2
  (syntax-rules ()
    ((and2) #t)
    ((and2 test) test)
    ((and2 test1 test2 ...)
     (if test1 (and2 test2 ...) #f))))
(print (and  7 8))                        ; prints 8
(print (and2 7 8))                        ; prints 8
(define foo 4)
(print (and  7 (set! foo (+ foo 1)) foo)) ; prints 5
(print (and2 7 (set! foo (+ foo 1)) foo)) ; prints 6

;; Moving on, I find promising coverage of this topic in 
;;
;;   JRM's Syntax-rules Primer for the Merely Eccentric
;;   http://www.xs4all.nl/~hipster/lib/scheme/gauche/define-syntax-primer.txt
;;
;; One key thing he points out that might explain my confusion about
;; the weird (let) expression in wdWhile above:
;;
;;   *** THE SYNTAX-RULES SUBLANGUAGE IS NOT SCHEME!
;;
;;   [cut] when Scheme is applying the syntax-rules rewrite there is
;;   NO SEMANTIC MEANING attached to the tokens.  The meaning will be
;;   attached at a later point in the process, but not here.

;; In syntax-rules, the token "_" can be used as the rule name.  JRM
;; discourages this as confusion, but as an implementor I'd better try
;; it.
;;
;; I cut-and-paste the and2 example above, change all "and2" to
;; "and3", then change all "and3" inside the syntax-rules to "_".
;; First try, it gives:
;; 
;;   ERROR: Unbound variable: _
;; 
;; When I change the _ in the body of the (if) back to and3, it works.
;; So the _ *only* works in the matching part of the syntax-rule
;; clause, not the transformation bodies.  That is, indeed, confusing,
;; and I am inclined to agree w/ JRM about avoidance of _ being the
;; best practice..
;; 
(print "and3")
(define-syntax and3
  (syntax-rules ()
    ((_) #t)
    ((_ test) test)
    ((_ test1 test2 ...)
     (if test1 (and3 test2 ...) #f))))
(print (and  7 8))                        ; prints 8
(print (and3 7 8))                        ; prints 8
(define foo 4)
(print (and  7 (set! foo (+ foo 1)) foo)) ; prints 5
(print (and3 7 (set! foo (+ foo 1)) foo)) ; prints 6
