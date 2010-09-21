
(define (sub_map1 proc list)
  (if list
      (cons (apply proc (car list))
            (sub_map proc (cdr list)))
      '()))

(define (sub_map2 proc a b)
  (cond ((and a b)
         (let ((head   (cons (car a) (car b)))
               (rest-a (cdr a))
               (rest-b (cdr b)))
           (cons (apply proc head)
                 (sub_map2 proc rest-a rest-b))))
        ((or a b)
         (error))
        (else 
         '())))

(define (sub_mapN proc . lists)
  (cond ((apply and lists)
         (let ((heads   (sub_map1 car lists))
               (rests   (sub_map1 cdr lists)))
           (cons (apply proc heads)
                 (sub_mapN proc . rests))))
        ((apply or lists)
         (error))
        (else 
         '())))

; Getting pretty hairy, still not tail-recursive, too many walks over
; the lists for my tasts.
;
; But in any case, a fully variadic sub_mapN is starting to feel
; out-of-scope for the fundamental layer.  
