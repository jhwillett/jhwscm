#!/usr/bin/guile
!#

;; Figuring out how to do splicing, as per unquote-splice.

(newline)
(define (exhibit func expr expect)
  (display 'expr)
  (display "   ")
  (display expr)
  (newline)
  (display 'expect)
  (display " ")
  (display expect)
  (newline)
  (let ((v (func expr)))
    (display 'got)
    (display "    ")
    (display v)
    (newline)
    (display (if (equal? expect v) 'happy 'unhappy))
    (newline))
  (newline))

(define bad-splice 'failure-bad-splice)

(define (splice expr)
  ;;(display 'a) (newline)
  ;;(display expr) (newline)
  (if (not (pair? expr))
      expr
      (let ((head (car expr))
            (rest (cdr expr)))
        ;;(display 'b) (newline)
        ;;(display head) (newline)
        ;;(display rest) (newline)
        (if (eq? 'spliceme head)
            bad-splice
            (if (not (pair? head))
                (cons (splice head) (splice rest))
                (let ((headfirst (car head)))
                  ;;(display 'c) (newline)
                  ;;(display headfirst) (newline)
                  (if (not (eq? 'spliceme headfirst))
                      (begin
                        ;;(display 'c1) (newline)
                        (cons (splice head) (splice rest))
                        )
                      (let ((headexpr (car (cdr head))))
                        (begin
                          ;;(display 'c2) (newline)
                          ;;(display headexpr) (newline)
                          ;;(display 'c2x) (newline)
                          (if (not (pair? headexpr))
                              bad-splice
                              (append headexpr (splice rest))))))))))))

(exhibit splice '(1 (spliceme (2 3)))      '(1 2 3)      )
(exhibit splice '(1 (spliceme (2 3)) 4)    '(1 2 3 4)    )
(exhibit splice '(1 ((spliceme (2 3))) 4)  '(1 (2 3) 4)  )
(exhibit splice '(1 4 ((spliceme (2 3))))  '(1 4 (2 3))  )
(exhibit splice '(1 4 ((spliceme ())))     '(1 4 ())     )
(exhibit splice '(1 4 (spliceme ()))       '(1 4)        )

(exhibit splice '(spliceme 2)              bad-splice    )
(exhibit splice '((spliceme 2))            '2            )
(exhibit splice '(1 (spliceme 2))          '(1 . 2)      )
(exhibit splice '(1 (spliceme 2) 3)        bad-splice    )

;;            { "`(1 ,@(list 2 3))",             "(1 2 3)"     },
;;            { "`(1 ,(list 2 3))",              "(1 (2 3))"   },
;;            { "`(1 ,@(list 2 3) 4)",           "(1 2 3 4)"   },
;;            { "`(1 ,(list 2 3) 4)",            "(1 (2 3) 4)" },
;;            { "`(,@2)",                        "2"           },
;;            { "`(1 ,@2)",                      "(1 . 2)"     },
;;            { "`(1 ,@2 3)",                    SEMANTIC      },
;;            { "` (1 ,@ 2 3)",                  SEMANTIC      },
;;            { "` (1 ,@2 3)",                   SEMANTIC      },
