

(define (sub_read_list)
  (if (= #\( (queue_peek_front))
      (begin (queue_pop_front)
             (sub_read_list_open))
      (err_lexical "expected open paren")))

(define (sub_read_list_open)
  (burn-space)
  (if (= #\) (queue_peek_front))
      (begin (queue_pop_front) '())
      (let ((next (sub_read))
            (rest (sub_read_list_open))) ; wow, 1 token lookahead!
        ;; Philosophical question: is it an abuse to let the next be
        ;; parsed as a symbol before rejecting it, when what I'm
        ;; after is not the semantic entity "the symbol with name
        ;; '.'" but rather the syntactic entity "the lexeme of an
        ;; isolated dot in the last-but-one position in a list
        ;; expression"?
        (cond 
         ((not (eqv? '. next)) (cons next rest))
         ((null? rest)         (err_lexical "danging dot"))
         ((null? (cdr rest))   (cons next (car rest)))
         (else                 (err_lexical "too much after dot"))
         ))))))
