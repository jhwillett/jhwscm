#!/usr/bin/guile
!#

;; Figuring out how to do splicing, as per unquote-splice.

(define (exhibit func expr expect)
  (display expr)
  (newline)
  (display 'exp)
  (display " ")
  (display expect)
  (newline)
  (display 'got)
  (display " ")
  (display (func expr))
  (newline))

(define (splice expr)
  (if (not (pair? expr))
      expr
      (let ((head (car expr))
            (rest (cdr expr)))
        (if (not (pair? head))
            (cons (splice head) (splice rest))
            (let ((headfirst (car head)))
              (if (not (eq? 'spliceme headfirst))
                  (cons (splice head) (splice rest))
                  (append (car (cdr head)) (splice rest))))))))

;;            { "`(1 ,@(list 2 3))",             "(1 2 3)"     },
;;            { "`(1 ,(list 2 3))",              "(1 (2 3))"   },
;;            { "`(1 ,@(list 2 3) 4)",           "(1 2 3 4)"   },
;;            { "`(1 ,(list 2 3) 4)",            "(1 (2 3) 4)" },
;;            { "`(,@2)",                        "2"           },
;;            { "`(1 ,@2)",                      "(1 . 2)"     },
;;            { "`(1 ,@2 3)",                    SEMANTIC      },
;;            { "` (1 ,@ 2 3)",                  SEMANTIC      },
;;            { "` (1 ,@2 3)",                   SEMANTIC      },


(exhibit splice '(1 (spliceme (2 3)))    '(1 2 3)    )
(exhibit splice '(1 (spliceme (2 3)) 4)  '(1 2 3 4)  )
(exhibit splice '((spliceme 2))          '2          )
(exhibit splice '(1 (spliceme 2))        '(1 . 2)    )
(exhibit splice '(1 (spliceme 2) 3)      '(whatever) ) ;; err expected
