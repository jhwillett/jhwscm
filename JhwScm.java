/**
 * Damnit, I'm writing my own Scheme again.
 *
 * This class is not reentrant.  The caller is advised to call into
 * any one JhwScm object from only one thread, or to take
 * responsibility for synchronizing 
 *
 * Provided that this class is not called in a reentrant fashion, the
 * general contract for this class is that it never, under any
 * circumstances, throws.
 *
 * Similarly, except for drive(-1), this class is guaranteed to halt
 * in all circumstances: it may be INCOMPETE, but it'll halt.  Hmmm:
 * maybe drive(-1) is no business of this class: let the client do
 * that themselves....
 *
 * The API is designed to facilitate exception-free interaction
 * between this class and its clients.  Any Throwable thrown by this
 * class is an internal bug.  That is, even if you feed broken or
 * overly resource-hungry Scheme code to this class, this class is
 * responsible for handling it without raising a Java exception.
 *
 * Also, although I'm implementing the canoncial recursive language,
 * I'm avoiding recursive functions in the implementation: I would
 * like the resultant engine to us only a shallow stack of definite
 * size.
 *
 * Why this draconian and even unweildy general contract which is
 * non-idomatic for Java? 
 *
 * Because this code is meant to be a prototype for a much lower level
 * future project, such as a reimplementation in C, assembly, or
 * possibly hardware.  
 *
 * It is desirable to get this class right in the easy language, but
 * without depending on features of the easy language which won't be
 * available down below.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett, motherfuckers!
 * All rights reserved.
 */

public class JhwScm
{
   // DEBUG instruments checks of things which ought *never* happen
   // e.g. errors which theoretically can only arise due to bugs in
   // JhwScm, not user errors or JVM resource exhaustion.
   //
   public static final boolean DEBUG                     = true;
   public static final boolean PROFILE                   = true;
   public static final boolean DEFER_HEAP_INIT           = true;
   public static final boolean PROPERLY_TAIL_RECURSIVE   = false;
   public static final boolean CLEVER_TAIL_CALL_MOD_CONS = true;
   public static final boolean CLEVER_STACK_RECYCLING    = true;

   // TODO: permeable abstraction barrier
   public static boolean SILENT = false;

   public static final int     SUCCESS          = 0;
   public static final int     INCOMPLETE       = 1;
   public static final int     BAD_ARG          = 2;
   public static final int     OUT_OF_MEMORY    = 3;
   public static final int     FAILURE_LEXICAL  = 4;
   public static final int     FAILURE_SEMANTIC = 5;
   public static final int     UNIMPLEMENTED    = 6;
   public static final int     INTERNAL_ERROR   = 7;

   public JhwScm ()
   {
      this(true);
   }

   public JhwScm ( final boolean doREP )
   {
      final boolean verb = false;

      if ( verb ) log("JhwScm.JhwScm()");
      for ( int i = 0; i < reg.length; i++ )
      {
         reg[i] = NIL;
      }

      reg[regFreeCellList] = NIL;
      heapTop              = 0;
      numCallsToCons       = 0;
      maxHeapTop           = 0;

      reg[regPc]  = doREP ? sub_rep : sub_rp;
      reg[regIn]  = queueCreate();
      reg[regOut] = queueCreate();
      reg[regEnv] = cons(NIL,NIL);

      prebind("+",      sub_add);
      prebind("*",      sub_mul);
      prebind("-",      sub_sub);
      prebind("<",      sub_lt_p);
      prebind("+0",     sub_add0);
      prebind("+1",     sub_add1);
      prebind("+3",     sub_add3);
      prebind("cons",   sub_cons);
      prebind("car",    sub_car);
      prebind("cdr",    sub_cdr);
      prebind("list",   sub_list);
      prebind("if",     sub_if);
      prebind("quote",  sub_quote);
      prebind("define", sub_define);
      prebind("lambda", sub_lambda);
      prebind("equal?", sub_equal_p);
      prebind("let",    sub_let);
      prebind("begin",  sub_begin);
      prebind("cond",   sub_cond);
      prebind("case",   sub_case);
   }

   /**
    * Places all characters into the VM's input queue.
    *
    * On failure, the VM's input queue is left unchanged: input() is
    * all-or-nothing.
    *
    * @param input characters to be copied into the VM's input queue.
    * @throws nothing, not ever
    * @returns SUCCESS on success, otherwise an error code.
    */
   public int input ( final CharSequence input ) 
   {
      final boolean verb = true && !SILENT;

      if ( DEBUG ) javaDepth = 0;
      if ( null == input )
      {
         if ( verb ) log("input():  null arg");
         return BAD_ARG;
      }
      if ( verb ) log("input():  \"" + input + "\"");
      if ( DEBUG && TYPE_CELL != type(reg[regIn]) )
      {
         raiseError(ERR_INTERNAL);
         return INTERNAL_ERROR;
      }
      if ( DEBUG && NIL != reg[regError] )
      {
         // TODO: is this really a legit use case?  Need a new code?
         //
         // What we're doing here is saying "can't accept input on a
         // VM in an error state", which is different than saying
         // "encountered an error processing this input".
         raiseError(ERR_INTERNAL);
         return INTERNAL_ERROR;
      }
      final int oldCar = car(reg[regIn]);
      final int oldCdr = car(reg[regIn]);
      for ( int i = 0; i < input.length(); ++i )
      {
         final char c    = input.charAt(i);
         final int  code = code(TYPE_CHAR,c);
         queuePushBack(reg[regIn],code);
         if ( NIL != reg[regError] )
         {
            setcar(reg[regIn],oldCar);
            setcdr(reg[regIn],oldCdr);
            return INTERNAL_ERROR; // TODO: proper proxy for reg[regError]
         }
      }
      return SUCCESS;
   }

   /**
    * Pulls all pending characters in the VM's output queue into the
    * output argument.
    *
    * @param output where the output is copied to.
    * @throws nothing, not ever
    * @returns SUCCESS on success, otherwise an error code.
    */
   public int output ( final Appendable output ) 
   {
      final boolean verb = true && !SILENT;
      if ( DEBUG ) javaDepth = 0;
      if ( null == output )
      {
         if ( verb ) log("output(): null arg");
         return BAD_ARG;
      }
      if ( NIL == reg[regOut] )
      {
         raiseError(ERR_INTERNAL);
         return INTERNAL_ERROR;
      }
      // TODO: make this all-or-nothing, like input()?
      for ( int f = 0; EOF != ( f = queuePeekFront(reg[regOut]) ); /*below*/ )
      {
         final int  v = value(f);
         final char c = (char)(MASK_VALUE & v);
         try
         {
            output.append(c);
         }
         catch ( Throwable e )
         {
            // TODO: change signature so we don't need this guard here?
            return OUT_OF_MEMORY;
         }
         queuePopFront(reg[regOut]);
      }
      if ( verb ) log("output(): \"" + output + "\"");
      return SUCCESS;
   }

   /**
    * Drives all pending computation to completion.
    *
    * @throws nothing, not ever
    * @returns SUCCESS on success, otherwise an error code.
    */
   public int drive ()
   {
      return drive(-1);
   }

   /**
    * Drives all pending computation to completion.
    *
    * @param numSteps the number of VM steps to execute.  If numSteps
    * < 0, runs to completion.
    * @throws nothing, not ever
    * @returns SUCCESS on success, INCOMPLETE if more cycles are
    * needed, otherwise an error code.
    */
   public int drive ( final int numSteps )
   {
      final boolean verb = true && !SILENT;

      if ( DEBUG ) javaDepth = 0;
      if ( verb ) log("drive():");
      if ( DEBUG ) javaDepth = 1;
      if ( verb ) log("numSteps: " + numSteps);

      if ( numSteps < -1 )
      {
         return BAD_ARG;
      }

      // Temp variables: note, any block can overwrite any of these.
      // Any data which must survive a block transition should be
      // saved in registers and on the stack instead.
      //
      int c    = 0;
      int c0   = 0;
      int v0   = 0;
      int c1   = 0;
      int v1   = 0;
      int tmp0 = 0;
      int tmp1 = 0;
      int tmp2 = 0;

      for ( int step = 0; -1 == numSteps || step < numSteps; ++step )
      {
         if ( DEBUG ) javaDepth = 1;
         if ( verb ) log("step: " + pp(reg[regPc]));
         if ( DEBUG ) javaDepth = 2;
         switch ( reg[regPc] )
         {
         case sub_rep:
            // Reads the next expr from reg[regIn], evaluates it, and
            // prints the result in reg[regOut].
            //
            // Top-level entry point for the interactive interpreter.
            //
            // Does not return in the Scheme sense, but may exit the
            // VM (returning in the Java sense).
            //
            // (define (sub_rep)
            //   (begin (sub_print (sub_eval (sub_read) global_env))
            //          (sub_rep)))
            //
            gosub(sub_read,sub_rep+0x1);
            break;
         case sub_rep+0x1:
            if ( EOF == reg[regRetval] )
            {
               reg[regPc] = sub_rep;
               return SUCCESS;
            }
            reg[regArg0] = reg[regRetval];
            reg[regArg1] = reg[regEnv];
            gosub(sub_eval,sub_rep+0x2);
            break;
         case sub_rep+0x2:
            reg[regArg0] = reg[regRetval];
            gosub(sub_print,sub_rep+0x3);
            break;
         case sub_rep+0x3:
            gosub(sub_rep,blk_tail_call);
            break;

         case sub_rp:
            // Reads the next expr from reg[regIn], and prints the
            // result in reg[regOut].
            //
            // A useful entry point for testing sub_read and sub_print
            // decoupled from sub_eval and sub_apply.
            //
            // Does not return in the Scheme sense, but may exit the
            // VM (returning in the Java sense).
            //
            // (define (sub_rp)
            //   (begin (sub_print (sub_read))
            //          (sub_rp)))
            //
            gosub(sub_read,sub_rp+0x1);
            break;
         case sub_rp+0x1:
            if ( EOF == reg[regRetval] )
            {
               reg[regPc] = sub_rp;
               return SUCCESS;
            }
            reg[regArg0] = reg[regRetval];
            gosub(sub_print,sub_rp+0x2);
            break;
         case sub_rp+0x2:
            gosub(sub_rp,blk_tail_call);
            break;

         case sub_read:
            // Parses the next expr from reg[regIn], and
            // leaves the results in reg[regRetval].
            //
            // Top-level entry point for the parser.
            //
            // Returns EOF if nothing was found, else the next expr.
            //
            // From R5RS Sec 66:
            //
            //   If an end of file is encountered in the input before
            //   any characters are found that can begin an object,
            //   then an end of file object is returned. The port
            //   remains open, and further attempts to read will also
            //   return an end of file object. If an end of file is
            //   encountered after the beginning of an object's
            //   external representation, but the external
            //   representation is incomplete and therefore not
            //   parsable, an error is signalled.
            //
            // (define (sub_read)
            //   (begin (sub_read_burn_space)
            //          (let ((c (queue_peeek_front)))
            //            (case c
            //              (( EOF ) EOF)
            //              (( #\) ) (err_lexical))
            //              (( #\( ) (sub_read_list))
            //              (else    (sub_read_atom))))))
            //
            gosub(sub_read_burn_space,sub_read+0x1);
            break;
         case sub_read+0x1:
            c = queuePeekFront(reg[regIn]);
            if ( EOF == c )
            {
               reg[regRetval] = EOF;
               returnsub();
               break;
            }
            if ( DEBUG && TYPE_CHAR != type(c) )
            {
               if ( verb ) log("non-char in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (value(c))
            {
            case ')':
               if ( verb ) log("mismatch close paren");
               raiseError(ERR_LEXICAL);
               break;
            case '(':
               gosub(sub_read_list,blk_tail_call);
               break;
            default:
               gosub(sub_read_atom,blk_tail_call);
               break;
            }
            break;

         case sub_read_list:
            // Reads the next list expr from reg[regIn], returning the
            // result in reg[regRetval]. Also handles dotted lists.
            // 
            // On entry, expects the next char from reg[regIn] to be
            // the opening '(' a list expression.
            // 
            // On exit, precisely the list expression will have been
            // consumed from reg[regIn], up to and including the final
            // ')'.
            //
            // (define (sub_read_list)
            //   (if (!= #\( (queue_peek_front))
            //       (err_lexical "expected open paren")
            //       (begin (queue_pop_front)
            //              (sub_read_list_open))))
            //
            if ( code(TYPE_CHAR,'(') != queuePeekFront(reg[regIn]) )
            {
               raiseError(ERR_LEXICAL);
               break;
            }
            queuePopFront(reg[regIn]);
            reg[regArg0] = FALSE;
            gosub(sub_read_list_open,blk_tail_call);
            break;

         case sub_read_list_open:
            // Reads all exprs from reg[regIn] until a loose EOF, a
            // ')', or a '.' is encountered.
            //
            // EOF results in an error (mismatchd paren).
            //
            // ')' results in returning a list containing all the
            // exprs in order.
            //
            // '.' results in a dotted list, provided subsequent
            // success reading a single expression further and finding
            // the closing ')'.
            // 
            // On exit, precisely the list expression will have been
            // consumed from reg[regIn], up to and including the final
            // ')'.
            //  
            // (define (sub_read_list_open)
            //   (burn-space)
            //   (case (queue_peek_front)
            //     ((eof) (error_lexical "eof in list expr"))
            //     ((#\)  (begin (queue_peek_front) '()))
            //     (else
            //       (let ((next (sub_read))
            //             (rest (sub_read_list_open))) ; wow, token lookahead!
            //         ;; Philosophical question: is it an abuse to let the
            //         ;; next be parsed as a symbol before rejecting it, when
            //         ;; what I am after is not the semantic entity "the 
            //         ;; symbol with name '.'" but rather the syntactic entity
            //         ;; "the lexeme of an isolated dot in the last-but-one
            //         ;; position in a list expression"?
            //         (cond 
            //          ((not (eqv? '. next)) (cons next rest))
            //          ((null? rest)         (err_lexical "danging dot"))
            //          ((null? (cdr rest))   (cons next (car rest)))
            //          (else                 (err_lexical "many after dot"))
            //          )))))
            //
            gosub(sub_read_burn_space,sub_read_list_open+0x1);
            break;
         case sub_read_list_open+0x1:
            c = queuePeekFront(reg[regIn]);
            if ( EOF == c )
            {
               if ( verb ) log("eof in list expr");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( code(TYPE_CHAR,')') == c )
            {
               if ( verb ) log("matching close-paren");
               queuePopFront(reg[regIn]);
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            gosub(sub_read,sub_read_list_open+0x2);
            break;
         case sub_read_list_open+0x2:
            store(reg[regRetval]);
            gosub(sub_read_list_open,sub_read_list_open+0x3);
            break;
         case sub_read_list_open+0x3:
            reg[regTmp0] = restore();      // next
            reg[regTmp1] = reg[regRetval]; // rest
            if ( TYPE_CELL           != type(reg[regTmp0])     ||
                 IS_SYMBOL           != car(reg[regTmp0])      ||
                 code(TYPE_CHAR,'.') != car(cdr(reg[regTmp0])) ||
                 NIL                 != cdr(cdr(reg[regTmp0]))  )
            {
               reg[regRetval] = cons(reg[regTmp0],reg[regTmp1]);
               returnsub();
               break;
            }
            if ( NIL == reg[regTmp1] )
            {
               log("dangling dot");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( NIL == cdr(reg[regTmp1]) )
            {
               log("happy dotted list");
               log("  " + pp(reg[regTmp0]) + " " + pp(car(reg[regTmp0])));
               log("  " + pp(reg[regTmp1]) + " " + pp(car(reg[regTmp1])));
               reg[regRetval] = car(reg[regTmp1]);
               returnsub();
               break;
            }
            log("many after dot");
            raiseError(ERR_LEXICAL);
            break;

         case sub_read_atom:
            // Reads the next atomic expr from reg[regIn], returning
            // the result in reg[regRetval].
            // 
            // On entry, expects the next char from reg[regIn] to be
            // the initial character of an atomic expression.
            // 
            // On exit, precisely the atomic expression will have been
            // consumed from reg[regIn].
            //
            // (define (sub_read_atom)
            //   (let ((c (queue_peek_front)))
            //     (case c
            //       (( #\' ) (err_not_impl))
            //       (( #\" ) (sub_read_string))
            //       (( #\# ) (sub_read_octo_tok))
            //       (( #\0 #\1 #\2 #\3 #\4 #\5 #\6 #\7 #\8 #\9 ) 
            //        (sub_read_num))
            //       (( #\- ) (begin 
            //                (queue_pop_front)
            //                (let ((c (queue_peek_front)))
            //                  (case c
            //                    (( #\0 #\1 #\2 #\3 #\4 #\5 #\6 #\7 #\8 #\9 )
            //                     (- (sub_read_num)))
            //                    (else (prepend #\- 
            //                                   (sub_read_symbol_body))))))))))
            //
            c = queuePeekFront(reg[regIn]);
            v0 = value(c);
            if ( DEBUG && TYPE_CHAR != type(c) )
            {
               if ( verb ) log("non-char in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (v0)
            {
            case '\'':
               if ( verb ) log("quote (not belong here in sub_read_atom?)");
               queuePopFront(reg[regIn]);
               gosub(sub_read,sub_read_atom+0x3);
               break;
            case '"':
               if ( verb ) log("string literal");
               gosub(sub_read_string,blk_tail_call);
               break;
            case '#':
               if ( verb ) log("octothorpe special");
               gosub(sub_read_octo_tok,blk_tail_call);
               break;
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
               if ( verb ) log("non-negated number");
               gosub(sub_read_num,blk_tail_call);
               break;
            case '-':
               // The minus sign is special.  We need to look ahead
               // *again* before we can decide whether it is part of a
               // symbol or part of a number.
               queuePopFront(reg[regIn]);
               c1 = queuePeekFront(reg[regIn]);
               v1 = value(c1);
               if ( TYPE_CHAR == type(c1) && '0' <= v1 && v1 <= '9' )
               {
                  if ( verb ) log("minus-starting-number");
                  gosub(sub_read_num,sub_read_atom+0x1);
               }
               else if ( EOF == c1 )
               {
                  if ( verb ) log("lonliest minus in the world");
                  reg[regTmp0]   = cons(code(TYPE_CHAR,'-'),NIL);
                  reg[regRetval] = cons(IS_SYMBOL,reg[regTmp0]);
                  returnsub();
               }
               else
               {
                  if ( verb ) log("minus-starting-symbol");
                  reg[regArg0] = queueCreate();
                  if ( verb ) log("pushing: minus onto " + pp(reg[regArg0]));
                  queuePushBack(reg[regArg0],code(TYPE_CHAR,'-'));
                  store(reg[regArg0]);
                  gosub(sub_read_symbol_body,sub_read_atom+0x2);
               }
               break;
            default:
               if ( verb ) log("symbol");
               gosub(sub_read_symbol,blk_tail_call);
               break;
            }
            break;
         case sub_read_atom+0x1:
            c = reg[regRetval];
            if ( TYPE_FIXINT != type(c) )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            if ( verb ) log("negating: " + pp(c));
            reg[regRetval] = code(TYPE_FIXINT,-value(c));
            if ( verb ) log("  to:       " + pp(reg[regRetval]));
            returnsub();
            break;
         case sub_read_atom+0x2:
            reg[regTmp0]   = restore();
            reg[regTmp1]   = car(reg[regTmp0]);
            reg[regRetval] = cons(IS_SYMBOL,reg[regTmp1]);
            if ( verb ) log("YO YO YO: " + pp(reg[regTmp0]) + " " + pp(reg[regTmp1]));
            if ( DEBUG )
            {
               reg[regTmp1] = reg[regTmp0];
               while ( NIL != reg[regTmp1] )
               {
                  if ( verb ) log("  YO: " + pp(car(reg[regTmp1])));
                  reg[regTmp1] = cdr(reg[regTmp1]);
               }
            }
            returnsub();
            break;
         case sub_read_atom+0x3:
            // after single quote
            //
            // TODO: Think about this one, by putting sub_quote here
            // instead of the symbol 'quote there are some funny
            // consequences: like for instance it kind of implies that
            // sub_quote needs to be self-evaluating (if not all
            // sub_foo).
            //
            // Also - how should it print?
            //
            reg[regTmp0]   = cons(reg[regRetval],NIL);
            reg[regRetval] = cons(sub_quote,reg[regTmp0]);
            returnsub();
            break;

         case sub_read_num:
            // Parses the next number from reg[regIn].
            //
            reg[regArg0] = code(TYPE_FIXINT,0);
            gosub(sub_read_num_loop,blk_tail_call);
            break;
         case sub_read_num_loop:
            // Parses the next number from reg[regIn], expecting the
            // accumulated value-so-far as a TYPE_FIXINT in
            // reg[regArg0].
            //
            // A helper for sub_read_num, but still a sub_ in its own
            // right.
            //
            c0 = queuePeekFront(reg[regIn]);
            v0 = value(c0);
            if ( EOF == c0 )
            {
               if ( verb ) log("eof: returning " + pp(reg[regArg0]));
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            }
            if ( TYPE_CHAR != type(c0) )
            {
               if ( verb ) log("non-char in input: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            c1 = reg[regArg0];
            v1 = value(c1);
            if ( TYPE_FIXINT != type(c1) )
            {
               if ( verb ) log("non-fixint in arg: " + pp(c1));
               raiseError(ERR_LEXICAL);
               break;
            }
            switch (v0)
            {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
            case '(':
            case ')':
               if ( verb ) log("terminator: " + pp(c0) + " return " + pp(reg[regArg0]));
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            default:
               if ( v0 < '0' || v0 > '9' )
               {
                  if ( verb ) log("non-digit in input: " + pp(c0));
                  raiseError(ERR_LEXICAL);
                  break;
               }
               tmp0 = 10*v1 + (v0-'0');
               if ( verb ) log("first char: " + (char)v0);
               if ( verb ) log("old accum:  " +       v1);
               if ( verb ) log("new accum:  " +       tmp0);
               queuePopFront(reg[regIn]);
               reg[regArg0] = code(TYPE_FIXINT,tmp0);
               gosub(sub_read_num_loop,blk_tail_call);
               break;
            }
            break;

         case sub_read_octo_tok:
            // Parses the next octothorpe literal reg[regIn].
            //
            c = queuePeekFront(reg[regIn]);
            if ( c != code(TYPE_CHAR,'#') )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePopFront(reg[regIn]);
            c0 = queuePeekFront(reg[regIn]);
            if ( EOF == c0 )
            {
               if ( verb ) log("eof after octothorpe");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( DEBUG && TYPE_CHAR != type(c0) )
            {
               if ( verb ) log("non-char in input: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePopFront(reg[regIn]);
            switch (value(c0))
            {
            case 't':
               if ( verb ) log("true");
               reg[regRetval] = TRUE;
               returnsub();
               break;
            case 'f':
               if ( verb ) log("false");
               reg[regRetval] = FALSE;
               returnsub();
               break;
            case '\\':
               c1 = queuePeekFront(reg[regIn]);
               if ( EOF == c1 )
               {
                  if ( verb ) log("eof after octothorpe slash");
                  raiseError(ERR_LEXICAL);
                  break;
               }
               if ( DEBUG && TYPE_CHAR != type(c1) )
               {
                  if ( verb ) log("non-char in input: " + pp(c1));
                  raiseError(ERR_INTERNAL);
                  break;
               }
               if ( verb ) log("character literal: " + pp(c1));
               queuePopFront(reg[regIn]);
               reg[regRetval] = c1;
               returnsub();
               // TODO: so far, we only handle the 1-char sequences...
               break;
            default:
               if ( verb ) log("unexpected after octothorpe: " + pp(c0));
               raiseError(ERR_LEXICAL);
               break;
            }
            break;

         case sub_read_symbol:
            // Parses the next symbol from reg[regIn].
            //
            reg[regArg0] = queueCreate();
            store(reg[regArg0]);
            gosub(sub_read_symbol_body,sub_read_symbol+0x1);
            break;
         case sub_read_symbol+0x1: // blk_tail_call_m_cons-ish??
            reg[regTmp0]   = restore();
            reg[regRetval] = cons(IS_SYMBOL,car(reg[regTmp0]));
            returnsub();
            break;

         case sub_read_symbol_body:
            // Parses the next symbol from reg[regIn], expecting the
            // accumulated value-so-far as a queue in reg[regArg0].
            //
            // A helper for sub_read_symbol, but still a sub_ in its
            // own right.
            //
            // Return value undefined, works via side-effects.
            //
            if ( DEBUG && TYPE_CELL != type(reg[regArg0]) )
            {
               if ( verb ) log("non-queue in arg: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            c0 = queuePeekFront(reg[regIn]);
            if ( EOF == c0 )
            {
               if ( verb ) log("eof: returning");
               reg[regRetval] = UNDEFINED;
               returnsub();
               break;
            }
            if ( TYPE_CHAR != type(c0) )
            {
               if ( verb ) log("non-char in input: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (value(c0))
            {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
            case '(':
            case ')':
            case '"':
               if ( verb ) log("eot: returning");
               returnsub();
               break;
            default:
               if ( verb ) log("pushing: " + pp(c0) + " onto " + pp(reg[regArg0]));
               queuePushBack(reg[regArg0],c0);
               queuePopFront(reg[regIn]);
               gosub(sub_read_symbol_body,blk_tail_call);
               break;
            }
            break;

         case sub_read_string:
            // Parses the next string literal from reg[regIn].
            //
            c = queuePeekFront(reg[regIn]);
            if ( code(TYPE_CHAR,'"') != c )
            {
               if ( verb ) log("non-\" leading string literal: " + pp(c));
               raiseError(ERR_LEXICAL);
               break;
            }
            queuePopFront(reg[regIn]);
            reg[regArg0] = queueCreate();
            store(reg[regArg0]);
            gosub(sub_read_string_body,sub_read_string+0x1);
            break;
         case sub_read_string+0x1:
            reg[regTmp0]   = restore();
            reg[regRetval] = cons(IS_STRING,car(reg[regTmp0]));
            c = queuePeekFront(reg[regIn]);
            if ( code(TYPE_CHAR,'"') != c )
            {
               if ( verb ) log("non-\" terminating string literal: " + pp(c));
               raiseError(ERR_LEXICAL);
               break;
            }
            queuePopFront(reg[regIn]);
            returnsub();
            break;

         case sub_read_string_body:
            // Parses the next string from reg[regIn], expecting the
            // accumulated value-so-far as a queue in reg[regArg0].
            //
            // A helper for sub_read_string, but still a sub_ in its
            // own right.
            //
            // Expects that the leading \" has already been consumed,
            // and stops on the trailing \" (which is left
            // unconsumed for balance).
            //
            if ( DEBUG && TYPE_CELL != type(reg[regArg0]) )
            {
               if ( verb ) log("non-queue in arg: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            c0 = queuePeekFront(reg[regIn]);
            if ( EOF == c0 )
            {
               if ( verb ) log("eof in string literal");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( TYPE_CHAR != type(c0) )
            {
               if ( verb ) log("non-char in input: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (value(c0))
            {
            case '"':
               reg[regRetval] = car(reg[regArg0]);
               if ( verb ) log("eot, returning: " + pp(reg[regRetval]));
               returnsub();
               break;
            default:
               if ( verb ) log("pushing: " + pp(c0));
               queuePushBack(reg[regArg0],c0);
               queuePopFront(reg[regIn]);
               gosub(sub_read_string_body,blk_tail_call);
               break;
            }
            break;

         case sub_read_burn_space:
            // Consumes any whitespace from reg[regIn].  Returns TRUE
            // if any was found, false otherwise.
            //
            // Return value undefined.
            //
            c = queuePeekFront(reg[regIn]);
            if ( EOF == c )
            {
               reg[regRetval] = UNDEFINED;
               returnsub();
               break;
            }
            switch (value(c))
            {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
               queuePopFront(reg[regIn]);
               gosub(sub_read_burn_space,blk_tail_call);
               break;
            default:
               reg[regRetval] = UNDEFINED;
               returnsub();
               break;
            }
            break;

         case sub_eval:
            // Evaluates the expr in reg[regArg0] in the env in
            // reg[regArg1], and leaves the results in reg[regRetval].
            //
            switch (type(reg[regArg0]))
            {
            case TYPE_CHAR:
            case TYPE_FIXINT:
            case TYPE_BOOLEAN:
            case TYPE_SUBS:    // TODO: is this a valid decision?  Off-spec?
            case TYPE_SUBP:    // TODO: is this a valid decision?  Off-spec?
               // these types are self-evaluating
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            case TYPE_CELL:
               tmp0 = car(reg[regArg0]);
               if ( verb ) log("h: " + pp(tmp0));
               switch (tmp0)
               {
               case IS_STRING:
                  // Strings are self-evaluating.
                  reg[regRetval] = reg[regArg0];
                  returnsub();
                  break;
               case IS_SYMBOL:
                  // Lookup the symbol in the environment.
                  //
                  //   reg[regArg0] already contains the symbol
                  //   reg[regArg1] already contains the env
                  gosub(sub_eval_look_env,sub_eval+0x1);
                  break;
               default:
                  // Evaluate the operator: the type of the result
                  // will determine whether we evaluate the args prior
                  // to apply.
                  store(cdr(reg[regArg0]));    // store the arg exprs
                  store(reg[regArg1]);         // store the env
                  reg[regArg0] = tmp0;         // forward the op
                  reg[regArg1] = reg[regArg1]; // forward the env
                  gosub(sub_eval,sub_eval+0x2);
                  break;
               }
               break;
            case TYPE_NIL:
               // TODO: it may be that NIL is self-evaluating
               //
               // Guile is weird, it gives different errors for
               // top-level () vs ()-in-op-position:
               //
               //   guile> ()
               //   ERROR: In procedure memoization:
               //   ERROR: Illegal empty combination ().
               //   ABORT: (syntax-error)
               //   guile> (())
               //   
               //   Backtrace:
               //   In current input:
               //      2: 0* [()]
               //   
               //   <unnamed port>:2:1: In expression (()):
               //   <unnamed port>:2:1: Wrong type to apply: ()
               //   ABORT: (misc-error)
               //
               // Don't know what to make of this.
               //
               raiseError(ERR_SEMANTIC);
               break;
            default:
               if ( verb ) log("unexpected type in eval: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            break;
         case sub_eval+0x1:
            // following symbol lookup
            if ( NIL == reg[regRetval] )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( DEBUG && TYPE_CELL != type(reg[regRetval]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regRetval] = cdr(reg[regRetval]);
            returnsub();
            break;
         case sub_eval+0x2:
            // following eval of the first elem

            // If it's a function, evaluate the args next, following
            // up with apply.
            //
            // If it's a special form, don't evaluate the args,
            // just pass it off to apply.
            //
            reg[regTmp1] = restore();         // restore the env
            reg[regTmp0] = restore();         // restore the arg exprs
            reg[regTmp2] = reg[regRetval];    // the value of the operator
            tmp0 = type(reg[regTmp2]);
            if ( TYPE_SUBP == tmp0 || 
                 TYPE_CELL == tmp0 && IS_PROCEDURE == car(reg[regTmp2]) )
            {
               // eval args
               // 
               // apply op to args
               store(reg[regTmp2]);      // store the value of the operator
               reg[regArg0] = reg[regTmp0];
               reg[regArg1] = reg[regTmp1];
               gosub(sub_eval_list,sub_eval+0x3);
               break;
            }
            if ( TYPE_SUBS == tmp0 || 
                 TYPE_CELL == tmp0 && IS_SPECIAL_FORM == car(reg[regTmp2]) )
            {
               // apply op to args
               reg[regArg0] = reg[regTmp2];
               reg[regArg1] = reg[regTmp0];
               gosub(sub_apply,blk_tail_call);
               break;
            }
            logrec("wtf",tmp0);
            raiseError(ERR_SEMANTIC);
            break;
         case sub_eval+0x3:
            // following eval of the args
            reg[regArg0] = restore();      // restore value of the operator
            reg[regArg1] = reg[regRetval]; // restore list of args
            gosub(sub_apply,blk_tail_call);
            break;

         case sub_eval_list:
            // Evaluates all the expressions in the list in
            // reg[regArg0] in the env in reg[regArg1], and returns a
            // list of the results.
            //
            //   (define (sub_eval_list list env)
            //     (if (null? list) 
            //         '()
            //         (cons (sub_eval (car list) env)
            //               (sub_eval_list (cdr list) env))))
            //   (define (sub_eval_list list env)
            //     (if (null? list) 
            //         '()
            //         (let ((first (sub_eval (car list) env))
            //           (cons first (sub_eval_list (cdr list) env)))))
            //   (define (sub_eval_list list env)
            //     (if (null? list) 
            //         '()
            //         (let ((first (sub_eval (car list) env))
            //               (rest  (sub_eval_list (cdr list) env))
            //           (cons first rest))))
            //
            // Using something most like the second varsion: I get to
            // exploit blk_tail_call_m_cons again! :)
            //
            // TODO: is this almost sub_map? ;)
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            store(cdr(reg[regArg0]));          // the rest of the list
            store(reg[regArg1]);               // the env
            reg[regArg0] = car(reg[regArg0]);  // the head of the list
            reg[regArg1] = reg[regArg1];       // the env
            gosub(sub_eval,sub_eval_list+0x1);
            break;
         case sub_eval_list+0x1:
            reg[regArg1] = restore();          // the env
            reg[regArg0] = restore();          // the rest of the list
            store(reg[regRetval]);             // feed blk_tail_call_m_cons
            gosub(sub_eval_list,blk_tail_call_m_cons);
            break;

         case sub_eval_look_env:
            // Looks up the symbol in reg[regArg0] in the env in
            // reg[regArg1].
            //
            // Returns NIL if not found, else the binding of the
            // symbol: a cell whose car is a symbol equivalent to the
            // argument symbol, and whose cdr is the value.
            //
            // Subsequent setcdrs on that cell change the value of the
            // symbol.
            //
            // Finds the *first* binding in the *first* frame for the
            // symbol.
            //
            // (define (sub_eval_look_env sym env)
            //   (if (null? env) 
            //       '()
            //       (let ((bind (sub_eval_look_frame sym (car env))))
            //         (if (null? bind) 
            //             (sub_eval_look_env sym (cdr env))
            //             bind))))
            //
            if ( true )
            {
               logrec("sub_eval_look_env SYM",reg[regArg0]);
               log(   "sub_eval_look_env ENV " + pp(reg[regArg1]));
            }
            if ( NIL == reg[regArg1] )
            {
               if ( verb ) log("empty env: symbol not found");
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            store(reg[regArg0]);
            store(reg[regArg1]);
            reg[regArg1] = car(reg[regArg1]);
            gosub(sub_eval_look_frame,sub_eval_look_env+0x1);
            break;
         case sub_eval_look_env+0x1:
            reg[regArg1] = restore();
            reg[regArg0] = restore();
            if ( NIL != reg[regRetval] )
            {
               if ( verb ) log("symbol found w/ bind: " + pp(reg[regRetval]));
               returnsub();
               break;
            }
            reg[regArg1] = cdr(reg[regArg1]);
            gosub(sub_eval_look_env,blk_tail_call);
            break;

         case sub_eval_look_frame:
            // Looks up the symbol in reg[regArg0] in the env frame in
            // reg[regArg1].
            //
            // Returns NIL if not found, else the binding of the
            // symbol: a cell whose car is a symbol equivalent to the
            // argument symbol, and whose cdr is the value.
            //
            // Subsequent setcdrs on that cell change the value of the
            // symbol.
            //
            // Finds the *first* binding in the frame for the symbol.
            //
            // (define (sub_eval_look_frame sym frame)
            //   (if (null? frame) 
            //       '()
            //       (let ((s (car (car frame))))
            //         (if (equal? sym s) 
            //             (car frame)
            //             (sub_eval_look_frame (cdr frame))))))
            //
            if ( true )
            {
               logrec("FRAME SYM   ",reg[regArg0]);
               //logrec("FRAME FRAME ",reg[regArg1]);
            }
            if ( NIL == reg[regArg1] )
            {
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            store(reg[regArg0]);
            store(reg[regArg1]);
            reg[regArg1] = car(car(reg[regArg1]));
            gosub(sub_equal_p,sub_eval_look_frame+0x1);
            break;
         case sub_eval_look_frame+0x1:
            reg[regArg1] = restore();
            reg[regArg0] = restore();
            if ( TRUE == reg[regRetval] )
            {
               reg[regRetval] = car(reg[regArg1]);
               returnsub();
               break;
            }
            reg[regArg1] = cdr(reg[regArg1]);
            gosub(sub_eval_look_frame,blk_tail_call);
            break;

         case sub_equal_p:
            // Compares the objects in reg[regArg0] and reg[regArg1].
            //
            // Returns TRUE in reg[regRetval] if they are equivalent,
            // being identical or having the same shape and same value
            // everywhere, FALSE otherwise.
            //
            // Does not handle cycles gracefully - and it may not be
            // necessary that it do so if we don't expose this to
            // users and ensure that it can only be called on objects
            // (like symbols) that are known to be cycle-free.
            //
            // NOTE: this is meant to be the equal? described in R5RS.
            //
            if ( verb ) log("arg0: " + pp(reg[regArg0]));
            if ( verb ) log("arg1: " + pp(reg[regArg1]));
            if ( reg[regArg0] == reg[regArg1] )
            {
               if ( verb ) log("identical");
               reg[regRetval] = TRUE;
               returnsub();
               break;
            }
            if ( type(reg[regArg0]) != type(reg[regArg1]) )
            {
               if ( verb ) log("different types");
               reg[regRetval] = FALSE;
               returnsub();
               break;
            }
            if ( type(reg[regArg0]) != TYPE_CELL )
            {
               if ( verb ) log("not cells");
               reg[regRetval] = FALSE;
               returnsub();
               break;
            }
            if ( verb ) log("checking car");
            store(reg[regArg0]);
            store(reg[regArg1]);
            reg[regArg0] = car(reg[regArg0]);
            reg[regArg1] = car(reg[regArg1]);
            gosub(sub_equal_p,sub_equal_p+0x1);
            break;
         case sub_equal_p+0x1:
            reg[regArg1] = restore();
            reg[regArg0] = restore();
            if ( FALSE == reg[regRetval] )
            {
               if ( verb ) log("car mismatch");
               returnsub();
               break;
            }
            if ( verb ) log("checking cdr");
            reg[regArg0] = cdr(reg[regArg0]);
            reg[regArg1] = cdr(reg[regArg1]);
            gosub(sub_equal_p,blk_tail_call);
            break;

         case sub_let:
            store(reg[regArg1]);              // store body
            gosub(sub_let_bindings,sub_let+0x1);
            break;
         case sub_let+0x1:
            reg[regTmp0] = restore();         // re body
            reg[regTmp1] = cons(reg[regRetval],reg[regEnv]);
            reg[regEnv]  = reg[regTmp1];
            reg[regArg0] = reg[regTmp0];
            reg[regArg1] = reg[regEnv];
            logrec("sub_let body ",reg[regArg0]);
            logrec("sub_let frame",reg[regRetval]);
            gosub(sub_eval,blk_tail_call);
            break;

         case sub_let_bindings:
            // reg[regArg0] is expected to be a list of lists of the
            // form:
            //
            //   ((symbol expr) (symbol expr) (symbol expr))
            // 
            // Each expr is evaluated in the current env (with none of
            // the symbols bound).  Returns a new frame with each
            // symbol bound to the results of evaluating each expr.
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               logrec("let dang A",reg[regArg0]);
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regTmp0] = car(reg[regArg0]); // first binding
            if ( TYPE_CELL != type(reg[regTmp0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regTmp1] = car(reg[regTmp0]); // first symbol
            if ( TYPE_CELL != type(reg[regTmp1]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( IS_SYMBOL != car(reg[regTmp1]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regTmp2] = cdr(reg[regTmp0]);
            if ( TYPE_CELL != type(reg[regTmp2]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            store(reg[regTmp1]);              // store symbol
            store(cdr(reg[regArg0]));         // store rest of bindings
            reg[regArg0] = car(reg[regTmp2]); // first expr
            reg[regArg1] = reg[regEnv];       // current env
            logrec("sub_let_bindings symbol",reg[regTmp1]);
            logrec("sub_let_bindings expr  ",reg[regArg0]);
            gosub(sub_eval,sub_let_bindings+0x1);
            break;
         case sub_let_bindings+0x1:
            reg[regTmp1] = restore();         // restore rest of bindings
            reg[regTmp0] = restore();         // restore symbol
            reg[regTmp2] = cons(reg[regTmp0],reg[regRetval]); // new binding
            logrec("sub_let_bindings symbol",reg[regTmp0]);
            logrec("sub_let_bindings value ",reg[regRetval]);
            store(reg[regTmp2]);
            reg[regArg0] = reg[regTmp1];
            gosub(sub_let_bindings,blk_tail_call_m_cons);
            break;

         case sub_begin:
            // Evaluates all its args, returning the result of the
            // last.  If no args, returns VOID.
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = VOID;
               returnsub();
               break;
            }
            reg[regTmp0] = car(reg[regArg0]);
            reg[regTmp1] = cdr(reg[regArg0]);
            reg[regArg0] = reg[regTmp0];
            reg[regArg1] = reg[regEnv];
            reg[regTmp2] = UNDEFINED;
            if ( NIL == reg[regTmp1] )
            {
               reg[regTmp2] = blk_tail_call;
            }
            else
            {
               store(reg[regTmp1]);             // store rest exprs
               reg[regTmp2] = sub_begin+0x1;
            }
            gosub(sub_eval,reg[regTmp2]);
            break;
         case sub_begin+0x1:
            reg[regArg0] = restore();           // restore rest exprs
            gosub(sub_begin,blk_tail_call);
            break;

         case sub_cond:
            // Does this:
            //
            //   (cond (#f 1) (#f 2) (#t 3))  ==> 3
            //
            // That is, expects the args to be a list of lists, each
            // of which is a test expression followed by a body.
            //
            // Evaluates the tests in order until one is true,
            // whereupon it evaluates the body of that clause as an
            // implicit (begin) statement.  If no args, returns VOID.
            //
            // Where the body of a clause is empty, returns the value
            // of the test e.g.:
            //
            //   (cond (#f) (#t))   ==> 1
            //   (cond (3)  (#t 1)) ==> 3
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = VOID;
               returnsub();
               break;
            }
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regTmp0] = car(car(reg[regArg0]));  // test of first clause
            reg[regTmp1] = cdr(car(reg[regArg0]));  // body of first clause
            reg[regTmp2] = cdr(reg[regArg0]);       // rest of clauses
            store(reg[regTmp1]);                    // store body of 1st clause
            store(reg[regTmp2]);                    // store rest of clauses
            logrec("test",reg[regTmp0]);
            reg[regArg0] = reg[regTmp0];
            reg[regArg1] = reg[regEnv];
            gosub(sub_eval,sub_cond+0x1);
            break;
         case sub_cond+0x1:
            reg[regTmp2] = restore();               // store rest of clauses
            reg[regTmp1] = restore();               // store body of 1st clause
            if ( FALSE == reg[regRetval] )
            {
               logrec("rest",reg[regTmp2]);
               reg[regArg0] = reg[regTmp2];
               gosub(sub_cond,blk_tail_call);
            }
            else if ( NIL == reg[regTmp1] )
            {
               log("no body");
               reg[regRetval] = reg[regRetval];
               returnsub();
            }
            else
            {
               logrec("body",reg[regTmp1]);
               reg[regArg0] = reg[regTmp1];
               gosub(sub_begin,blk_tail_call);
            }
            break;

         case sub_case:
            // Does:
            //
            //   (case 7 ((2 3) 100) ((4 5) 200) ((6 7) 300)) ==> 300
            //
            // Returns VOID if no match is found.
            //
            // Note: the key gets evaluated, and the body of the
            // matching clause gets evaluated as an implicit (begin)
            // form, but the labels do *not* get evaluated.
            //
            // This means (case) is useful with atomic literals as
            // labels, and not for compound expressions, variables, or
            // anything else fancy like that.  Think fixints,
            // booleans, and character literals: even strings and
            // symbols won't match
            //
            // Matching is per eqv?, not equal?.
            //
            // This makes (case) much less useful without "else" than
            // (cond) is: (cond) can fall back on #t for the default
            // catchall clause.  With (case), that would only work if
            // the key's value were identical to #t, not just non-#f.
            //
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);       // missing key
               break;
            }
            reg[regTmp0] = car(reg[regArg0]);  // key
            reg[regTmp1] = cdr(reg[regArg0]);  // clauses
            if ( TYPE_CELL != type(reg[regTmp1]) )
            {
               raiseError(ERR_SEMANTIC);       // missing clauses
               break;
            }
            logrec("key expr:  ",reg[regTmp0]);
            store(reg[regTmp1]);               // store clauses
            reg[regArg0] = reg[regTmp0];
            reg[regArg1] = reg[regEnv];
            gosub(sub_eval,sub_case+0x1);
            break;
         case sub_case+0x1:
            reg[regTmp0] = reg[regRetval];     // value of key
            reg[regTmp1] = restore();          // restore clauses
            logrec("key value: ",reg[regTmp0]);
            logrec("clauses:   ",reg[regTmp1]);
            reg[regArg0] = reg[regTmp0];
            reg[regArg1] = reg[regTmp1];
            gosub(sub_case_search,blk_tail_call);
            break;

         case sub_case_search:
            // reg[regArg0] is the value of the key
            // reg[regArg1] is the list of clauses
            logrec("key value:   ",reg[regArg0]);
            logrec("clause list: ",reg[regArg1]);
            if ( NIL == reg[regArg1] ) 
            {
               reg[regRetval] = VOID;
               returnsub();
               break;
            }
            if ( TYPE_CELL != type(reg[regArg1]) )
            {
               raiseError(ERR_SEMANTIC);      // bogus clause list
               break;
            }
            reg[regTmp0] = car(reg[regArg1]); // first clause
            reg[regTmp1] = cdr(reg[regArg1]); // rest clauses
            logrec("first clause:",reg[regTmp0]);
            logrec("rest clauses:",reg[regTmp1]);
            if ( TYPE_CELL != type(reg[regTmp0]) )
            {
               raiseError(ERR_SEMANTIC);      // bogus clause
               break;
            }
            reg[regTmp2] = car(reg[regTmp0]); // first clause label list
            reg[regTmp3] = cdr(reg[regTmp0]); // first clause body
            logrec("label list:  ",reg[regTmp2]);
            store(reg[regArg0]);              // store key
            store(reg[regTmp1]);              // store rest clauses
            store(reg[regTmp3]);              // store body
            reg[regArg0] = reg[regArg0];
            reg[regArg1] = reg[regTmp2];
            gosub(sub_case_in_list_p,sub_case_search+0x1);
            break;
         case sub_case_search+0x1:
            reg[regTmp3] = restore();         // restore body
            reg[regArg1] = restore();         // restore rest clauses
            reg[regArg0] = restore();         // restore key
            logrec("key:         ",reg[regArg0]);
            logrec("rest clauses:",reg[regArg1]);
            logrec("matchup:     ",reg[regRetval]);
            if ( FALSE == reg[regRetval] )
            {
               gosub(sub_case_search,blk_tail_call);
            }
            else
            {
               reg[regArg0] = reg[regTmp3];
               gosub(sub_begin,blk_tail_call);
            }
            break;

         case sub_case_in_list_p:
            // Returns TRUE if reg[regArg0] is hard-equal to any of
            // the elements in the proper list in reg[regArg1], else
            // FALSE.
            //
            // Only works w/ lables as per sub_case: fixints,
            // booleans, and characer literals.  Nothing else will
            // match.
            logrec("key:   ",reg[regArg0]);
            logrec("labels:",reg[regArg1]);
            if ( NIL == reg[regArg1] ) 
            {
               reg[regRetval] = FALSE;
               returnsub();
               break;
            }
            if ( TYPE_CELL != type(reg[regArg1]) ) 
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regTmp0] = car(reg[regArg1]);  // first element
            reg[regTmp1] = cdr(reg[regArg1]);  // rest of elements
            if ( reg[regArg0] == reg[regTmp0] )
            {
               // TODO: Check type?  We would not want them to both be
               // interned strings, or would we...?
               reg[regRetval] = TRUE;
               returnsub();
               break;
            }
            reg[regArg0] = reg[regArg0];
            reg[regArg1] = reg[regTmp1];
            gosub(sub_case_in_list_p,blk_tail_call);
            break; 

         case sub_apply:
            // Applies the op in reg[regArg0] to the args in
            // reg[regArg1], and return the results.
            //
            switch (type(reg[regArg0]))
            {
            case TYPE_SUBP:
            case TYPE_SUBS:
               gosub(sub_apply_builtin,blk_tail_call);
               break;
            case TYPE_CELL:
               switch (car(reg[regArg0]))
               {
               case IS_PROCEDURE:
               case IS_SPECIAL_FORM:
                  gosub(sub_apply_user,blk_tail_call);
                  break;
               default:
                  raiseError(ERR_INTERNAL);
                  break;
               }
               break;
            default:
               raiseError(ERR_INTERNAL);
               break;
            }
            break;
         
         case sub_apply_builtin:
            // Applies the sub_foo in reg[regArg0] to the args in
            // reg[regArg1], and return the results.
            //
            // get arity
            //
            // - if AX, just put the list of args in reg[regArg0].
            //
            // - if A<N>, assign N entries from list at
            //   reg[regArg1] into reg[regArg0.regArg<N>].
            //   Freak out if there are not exactly N args.
            //
            // Then just gosub()!
            tmp0 = reg[regArg0];
            final int arity = (tmp0 & MASK_ARITY) >>> SHIFT_ARITY;
            log("tmp0:  " + pp(tmp0));
            log("tmp0:  " + hex(tmp0,8));
            log("arity: " + arity);
            log("arg1:  " + pp(reg[regArg1]));
            reg[regTmp0] = reg[regArg1];
            reg[regArg0] = UNDEFINED;
            reg[regArg1] = UNDEFINED;
            reg[regArg2] = UNDEFINED;
            // TODO: icky dependency on reg order
            // 
            // TODO: on the other hand, first use of
            // register-index-as-variable.... hmmm
            //
            tmp2         = regArg0;
            switch (arity << SHIFT_ARITY)
            {
            case AX:
               reg[regArg0] = reg[regTmp0];
               gosub(tmp0,blk_tail_call);
               break;
            case A3:
               if ( NIL == reg[regTmp0] )
               {
                  log("too few args");
                  raiseError(ERR_SEMANTIC);
                  break;
               }
               reg[tmp2]    = car(reg[regTmp0]);
               reg[regTmp0] = cdr(reg[regTmp0]);
               log("pop arg: " + pp(reg[regArg0]));
               tmp2++;
               // fall through
            case A2:
               if ( NIL == reg[regTmp0] )
               {
                  log("too few args");
                  raiseError(ERR_SEMANTIC);
                  break;
               }
               reg[tmp2]    = car(reg[regTmp0]);
               reg[regTmp0] = cdr(reg[regTmp0]);
               log("pop arg: " + pp(reg[regArg0]));
               tmp2++;
            case A1:
               if ( NIL == reg[regTmp0] )
               {
                  log("too few args");
                  raiseError(ERR_SEMANTIC);
                  break;
               }
               reg[tmp2]    = car(reg[regTmp0]);
               reg[regTmp0] = cdr(reg[regTmp0]);
               log("pop arg: " + pp(reg[regArg0]));
               tmp2++;
            case A0:
               if ( NIL != reg[regTmp0] )
               {
                  log("too many args");
                  raiseError(ERR_SEMANTIC);
                  break;
               }
               log("arg0: " + pp(reg[regArg0]));
               log("arg1: " + pp(reg[regArg1]));
               log("arg2: " + pp(reg[regArg2]));
               gosub(tmp0,blk_tail_call);
               break;
            default:
               raiseError(ERR_INTERNAL);
               break;
            }
            break;

         case sub_apply_user:
            // Applies the user-defined procedure or special form in
            // reg[regArg0] to the args in reg[regArg1], and return
            // the results.
            //
            // We construct an env frame with the positional params
            // bound to their corresponding args, extend the current
            // env with that frame, and evaluate the body within the
            // new env.
            //
            // The internal representation of a user-defined is:
            //
            //   '(IS_PROCEDURE arg-list body lexical-env)
            //
            if ( DEBUG && TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            if ( DEBUG && IS_PROCEDURE != car(reg[regArg0]) )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            store(reg[regArg0]);
            store(reg[regArg1]);
            reg[regArg0] = car(cdr(reg[regArg0]));
            reg[regArg1] = reg[regArg1];
            gosub(sub_zip,sub_apply_user+0x1);
            break;
         case sub_apply_user+0x1:
            reg[regArg1] = restore();                   // restore args UNUSED!
            reg[regArg0] = restore();                   // restore operator
            reg[regTmp0] = reg[regRetval];              // extract env frame
            reg[regTmp1] = car(cdr(cdr(reg[regArg0]))); // extract body
            reg[regTmp2] = cons(reg[regTmp0],reg[regEnv]);
            logrec("sub_apply_user BODY ",reg[regTmp1]);
            logrec("sub_apply_user FRAME",reg[regTmp0]);
            log("sub_apply_user ENV  " + pp(reg[regTmp2]));
            reg[regArg0] = reg[regTmp1];
            reg[regArg1] = reg[regTmp2];
            if ( true )
            {
               // TODO: should this be eval's job?
               log("MAKING THE LEAP");
               store(reg[regEnv]);
               reg[regEnv] = reg[regTmp2];
               gosub(sub_eval, sub_apply_user+0x2);
            }
            else
            {
               gosub(sub_eval, blk_tail_call);
            }
            break;
         case sub_apply_user+0x2:
            // I am so sad that pushing that env above means we cannot
            // be tail recursive.
            //
            log("LANDING!!!");
            reg[regEnv] = restore();
            returnsub();
            break;

         case sub_zip:
            // Expects lists of equal lengths in reg[regArg0] and
            // reg[regArg1]. Returns a new list of the same length,
            // whose elments are cons() of corresponding elements from
            // reg[regArg0] and reg[regArg1] respectively.
            //
            // Note, if we had a sub_mapcar, this is really just:
            //
            //   (mapcar cons listA listB)
            //
            if ( NIL == reg[regArg0] && NIL == reg[regArg1] )
            {
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            if ( NIL == reg[regArg0] || NIL == reg[regArg1] )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regTmp0] = car(reg[regArg0]);
            reg[regTmp1] = car(reg[regArg1]);
            reg[regTmp2] = cons(reg[regTmp0],reg[regTmp1]);
            reg[regArg0] = cdr(reg[regArg0]);
            reg[regArg1] = cdr(reg[regArg1]);
            store(reg[regTmp2]);
            gosub(sub_zip,blk_tail_call_m_cons);
            break;

         case sub_print:
            // Prints the expr in reg[regArg0] to reg[regOut].
            //
            // Return value undefined.
            //
            c = reg[regArg0];
            if ( verb ) log("printing: " + pp(c));
            switch (type(c))
            {
            case TYPE_NIL:
               gosub(sub_print_list,blk_tail_call);
               break;
            case TYPE_CELL:
               c0 = car(c);
               c1 = cdr(c);
               switch (c0)
               {
               case IS_STRING:
                  reg[regArg0] = c1;
                  gosub(sub_print_string,blk_tail_call);
                  break;
               case IS_SYMBOL:
                  reg[regArg0] = c1;
                  gosub(sub_print_chars,blk_tail_call);
                  break;
               case IS_PROCEDURE:
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
                  returnsub();
                  break;
               default:
                  reg[regArg0] = c;
                  gosub(sub_print_list,blk_tail_call);
                  break;
               }
               break;
            case TYPE_CHAR:
               queuePushBack(reg[regOut],code(TYPE_CHAR,'#'));
               queuePushBack(reg[regOut],code(TYPE_CHAR,'\\'));
               switch (value(c))
               {
               case ' ':
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'s'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'p'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'a'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'c'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'e'));
                  break;
               case '\n':
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'n'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'e'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'w'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'l'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'i'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'n'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'e'));
                  break;
               default:
                  queuePushBack(reg[regOut],c);
                  break;
               }
               reg[regRetval] = UNDEFINED;
               returnsub();
               break;
            case TYPE_BOOLEAN:
               queuePushBack(reg[regOut],code(TYPE_CHAR,'#'));
               switch (c)
               {
               case TRUE:
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'t'));
                  returnsub();
                  break;
               case FALSE:
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'f'));
                  returnsub();
                  break;
               default:
                  raiseError(ERR_INTERNAL);
                  break;
               }
               break;
            case TYPE_FIXINT:
               // We trick out the sign extension of our 28-bit
               // twos-complement FIXINTs to match Java's 32 bits
               // before proceeding.
               v0 = (value(c) << (32-SHIFT_TYPE)) >> (32-SHIFT_TYPE);
               // TODO: this is a huge cop-out, implement it right
               final String str = "" + v0;
               for ( tmp0 = 0; tmp0 < str.length(); ++tmp0 )
               {
                  queuePushBack(reg[regOut],
                                code(TYPE_CHAR,str.charAt(tmp0)));
               }
               reg[regRetval] = UNDEFINED;
               returnsub();
               break;
            case TYPE_SUBP:
            case TYPE_SUBS:
               // TODO: this is a huge cop-out, implement it right
               final String str2 = pp(c);
               for ( tmp0 = 0; tmp0 < str2.length(); ++tmp0 )
               {
                  queuePushBack(reg[regOut],
                                code(TYPE_CHAR,str2.charAt(tmp0)));
               }
               reg[regRetval] = UNDEFINED;
               returnsub();
               break;
            case TYPE_ERROR:
               raiseError(ERR_INTERNAL);
               break;
            case TYPE_SENTINEL:
               if ( VOID == c )
               {
                  reg[regRetval] = UNDEFINED;
                  returnsub();
                  break;
               }
               else
               {
                  // TYPE_SENTINEL is used by sub_print, true, but should
                  // not come up in a top-level argument to sub_print.
                  //
                  // TODO: clearly having rules about TYPE_SENTINEL
                  // break down, and UNDEFINED vs VOID vs NIL really
                  // needs to be thought out.
                  raiseError(ERR_INTERNAL);
                  break;
               }
            default:
               raiseError(ERR_INTERNAL);
               break;
            }
            break;
            
         case sub_print_string:
            // Prints the list in reg[regArg0], whose elements are
            // expected to all be TYPE_CHAR, to reg[regOut] in
            // double-quotes.
            //
            queuePushBack(reg[regOut],code(TYPE_CHAR,'"'));
            gosub(sub_print_chars,sub_print_string+0x1);
            break;
         case sub_print_string+0x1:
            queuePushBack(reg[regOut],code(TYPE_CHAR,'"'));
            reg[regRetval] = UNDEFINED;
            returnsub();
            break;

         case sub_print_chars:
            // Prints the list in reg[regArg0], whose elements are
            // expected to all be TYPE_CHAR, to reg[regOut].
            //
            c = reg[regArg0];
            if ( NIL == c )
            {
               returnsub();
               break;
            }
            if ( TYPE_CELL != type(c) )
            {
               if ( verb ) log("bogus non-cell: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            c0 = car(c);
            c1 = cdr(c);
            if ( TYPE_CHAR != type(c0) )
            {
               if ( verb ) log("bogus: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePushBack(reg[regOut],c0);
            reg[regArg0] = c1;
            gosub(sub_print_chars,blk_tail_call);
            break;

         case sub_print_list:
            // Prints the list (NIL or a cell) in reg[regArg0] to
            // reg[regOut] in parens.
            //
            reg[regArg0] = reg[regArg0];
            reg[regArg1] = TRUE;
            queuePushBack(reg[regOut],code(TYPE_CHAR,'('));
            gosub(sub_print_list_elems,sub_print_list+0x1);
            break;
         case sub_print_list+0x1:
            queuePushBack(reg[regOut],code(TYPE_CHAR,')'));
            returnsub();
            break;

         case sub_print_list_elems:
            // Prints the elements in the list (NIL or a cell) in
            // reg[regArg0] to reg[regOut] with a space between each.
            //
            // Furthermore, reg[regArg1] should be TRUE if
            // reg[regArg0] is the first item in the list, FALSE
            // otherwise.
            //
            // Return value is undefined.
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = UNDEFINED;
               returnsub();
               break;
            }
            if ( FALSE == reg[regArg1] )
            {
               queuePushBack(reg[regOut],code(TYPE_CHAR,' '));
            }
            store(reg[regArg0]);
            reg[regTmp0] = car(reg[regArg0]);
            reg[regTmp1] = cdr(reg[regArg0]);
            if ( NIL       != reg[regTmp1]       &&
                 TYPE_CELL != type(reg[regTmp1])  )
            {
               log("dotted list");
               reg[regArg0] = reg[regTmp0];
               gosub(sub_print,sub_print_list_elems+0x2);
            }
            else
            {
               log("regular list so far");
               reg[regArg0] = reg[regTmp0];
               gosub(sub_print,sub_print_list_elems+0x1);
            }
            break;
         case sub_print_list_elems+0x1:
            reg[regTmp0] = restore();
            reg[regArg0] = cdr(reg[regTmp0]);
            reg[regArg1] = FALSE;
            gosub(sub_print_list_elems,blk_tail_call);
            break;
         case sub_print_list_elems+0x2:
            reg[regTmp0] = restore();
            reg[regArg0] = cdr(reg[regTmp0]);
            queuePushBack(reg[regOut],code(TYPE_CHAR,' '));
            queuePushBack(reg[regOut],code(TYPE_CHAR,'.'));
            queuePushBack(reg[regOut],code(TYPE_CHAR,' '));
            gosub(sub_print,blk_tail_call);
            break;

         case sub_add:
            if ( TYPE_FIXINT != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( TYPE_FIXINT != type(reg[regArg1]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            tmp0 = value(reg[regArg0]);
            tmp1 = value(reg[regArg1]);
            tmp0 <<= (32-SHIFT_TYPE);
            tmp0 >>= (32-SHIFT_TYPE);
            tmp1 <<= (32-SHIFT_TYPE);
            tmp1 >>= (32-SHIFT_TYPE);
            reg[regRetval] = code(TYPE_FIXINT,(tmp0+tmp1));
            returnsub();
            break;

         case sub_add0:
            reg[regRetval] = code(TYPE_FIXINT,0);
            returnsub();
            break;

         case sub_add1:
            if ( TYPE_FIXINT != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regRetval] = reg[regArg0];
            returnsub();
            break;

         case sub_add3:
            if ( TYPE_FIXINT != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( TYPE_FIXINT != type(reg[regArg1]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( TYPE_FIXINT != type(reg[regArg2]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            tmp0 = value(reg[regArg0]);
            tmp1 = value(reg[regArg1]);
            tmp2 = value(reg[regArg2]);
            tmp0 <<= (32-SHIFT_TYPE);
            tmp0 >>= (32-SHIFT_TYPE);
            tmp1 <<= (32-SHIFT_TYPE);
            tmp1 >>= (32-SHIFT_TYPE);
            tmp2 <<= (32-SHIFT_TYPE);
            tmp2 >>= (32-SHIFT_TYPE);
            reg[regRetval] = code(TYPE_FIXINT,(tmp0+tmp1+tmp2));
            returnsub();
            break;

         case sub_mul:
            if ( TYPE_FIXINT != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( TYPE_FIXINT != type(reg[regArg1]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            tmp0 = value(reg[regArg0]);
            tmp1 = value(reg[regArg1]);
            tmp0 <<= (32-SHIFT_TYPE);
            tmp0 >>= (32-SHIFT_TYPE);
            tmp1 <<= (32-SHIFT_TYPE);
            tmp1 >>= (32-SHIFT_TYPE);
            reg[regRetval] = code(TYPE_FIXINT,(tmp0*tmp1));
            returnsub();
            break;

         case sub_sub:
            if ( TYPE_FIXINT != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( TYPE_FIXINT != type(reg[regArg1]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            tmp0 = value(reg[regArg0]);
            tmp1 = value(reg[regArg1]);
            tmp0 <<= (32-SHIFT_TYPE);
            tmp0 >>= (32-SHIFT_TYPE);
            tmp1 <<= (32-SHIFT_TYPE);
            tmp1 >>= (32-SHIFT_TYPE);
            reg[regRetval] = code(TYPE_FIXINT,(tmp0-tmp1));
            returnsub();
            break;

         case sub_lt_p:
            if ( TYPE_FIXINT != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( TYPE_FIXINT != type(reg[regArg1]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            tmp0 = value(reg[regArg0]);
            tmp1 = value(reg[regArg1]);
            tmp0 <<= (32-SHIFT_TYPE);
            tmp0 >>= (32-SHIFT_TYPE);
            tmp1 <<= (32-SHIFT_TYPE);
            tmp1 >>= (32-SHIFT_TYPE);
            reg[regRetval] = (tmp0 < tmp1) ? TRUE : FALSE;
            returnsub();
            break;

         case sub_cons:
            log("cons: " + pp(reg[regArg0]));
            log("cons: " + pp(reg[regArg1]));
            reg[regRetval] = cons(reg[regArg0],reg[regArg1]);
            returnsub();
            break;

         case sub_car:
            log("car: " + pp(reg[regArg0]));
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regRetval] = car(reg[regArg0]);
            returnsub();
            break;

         case sub_cdr:
            log("cdr: " + pp(reg[regArg0]));
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regRetval] = cdr(reg[regArg0]);
            returnsub();
            break;

         case sub_list:
         case sub_quote:
            // Commentary: I *love* how sub_quote and sub_list are the
            // same once you abstract special form vs procedure into
            // (eval) and arity into (apply).
            //
            // I totally get off on it!
            //
            reg[regRetval] = reg[regArg0];
            returnsub();
            break;

         case sub_if:
            log("arg0: " + pp(reg[regArg0]));
            log("arg1: " + pp(reg[regArg1]));
            log("arg2: " + pp(reg[regArg2]));
            store(reg[regArg1]);
            store(reg[regArg2]);
            reg[regArg0] = reg[regArg0];
            reg[regArg1] = reg[regEnv];
            gosub(sub_eval,sub_if+0x1);
            break;
         case sub_if+0x1:
            reg[regArg2] = restore();
            reg[regArg1] = restore();
            if ( FALSE != reg[regRetval] )
            {
               reg[regArg0] = reg[regArg1];
            }            
            else
            {
               reg[regArg0] = reg[regArg2];
            }
            reg[regArg1] = reg[regEnv];
            gosub(sub_eval,blk_tail_call);
            break;

         case sub_define:
            // If a variable is bound in the current environment
            // *frame*, changes it.  Else creates a new binding in the
            // current *frame*.
            //
            // That is - at the top-level define creates or mutates a
            // top-level binding, and an inner define creates or
            // mutates an inner binding, but an inner define will not
            // create or (most importantly) mutate a higher-level
            // binding.
            //
            // Also - we have two forms of define, the one with a
            // symbol arg which just defines a variable (define x 1),
            // and the one with a list arg which is sugar (define (x)
            // 1) is (define x (lambda () 1)).
            //
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            if ( IS_SYMBOL == car(reg[regArg0]) )
            {
               reg[regTmp0] = reg[regArg0];
               reg[regTmp1] = reg[regArg1];
            }
            else
            {
               // TODO: By putting sub_list here is like putting
               // sub_quote in other lists: forces the hand on other
               // design decisions.
               //
               reg[regTmp0] = car(reg[regArg0]);
               reg[regTmp1] = cons(reg[regArg1],NIL);
               reg[regTmp2] = cons(cdr(reg[regArg0]),reg[regTmp1]);
               reg[regTmp1] = cons(sub_lambda,reg[regTmp2]);
            }
            logrec("DEFINE SYMBOL: ",reg[regTmp0]);
            logrec("DEFINE BODY:   ",reg[regTmp1]);
            store(reg[regTmp0]);              // store the symbol
            reg[regArg0] = reg[regTmp1];      // eval the body
            reg[regArg1] = reg[regEnv];       // we need an env arg here!
            gosub(sub_eval,sub_define+0x1);
            break;
         case sub_define+0x1:
            reg[regTmp0] = restore();         // restore the symbol
            store(reg[regTmp0]);              // store the symbol INEFFICIENT
            store(reg[regRetval]);            // store the body's value
            reg[regArg0] = reg[regTmp0];      // lookup the binding
            reg[regArg1] = car(reg[regEnv]);  // we need an env arg here!
            gosub(sub_eval_look_frame,sub_define+0x2);
            break;
         case sub_define+0x2:
            reg[regTmp1] = restore();         // restore the body's value
            reg[regTmp0] = restore();         // restore the symbol
            if ( NIL == reg[regRetval] )
            {
               // create a new binding        // we need an env arg here!
               reg[regTmp1] = cons(reg[regTmp0],reg[regTmp1]);
               reg[regTmp2] = cons(reg[regTmp1],car(reg[regEnv]));
               setcar(reg[regEnv],reg[regTmp2]);
               log("define new binding");
               
            }
            else
            {
               // change the existing binding
               setcdr(reg[regRetval],reg[regTmp1]);
               log("define old binding");
            }
            //logrec("define B",reg[regEnv]);
            reg[regRetval] = VOID;
            returnsub();
            break;

         case sub_lambda:
            // Some key decisions here.  
            //
            // To get lambda, gotta decide how to represent a
            // function.  
            //
            // From stubbing out sub_eval and sub_apply, I already
            // know it's gonna be a list whose 1st element is
            // IS_PROCEDURE.
            //
            // We're gonna need an arg list, a body, and a lexical
            // environment as well.  Let's just say that's it:
            //
            //   '(IS_PROCEDURE arg-list body lexical-env)
            //
            // OK, done!
            //
            // TODO: gotta get that current environment here...
            //
            reg[regRetval] = cons(reg[regEnv], NIL);
            reg[regRetval] = cons(reg[regArg1],reg[regRetval]);
            reg[regRetval] = cons(reg[regArg0],reg[regRetval]);
            reg[regRetval] = cons(IS_PROCEDURE,reg[regRetval]);
            returnsub();
            break;

         case blk_tail_call:
            // Just returns whatever retval left behind by the
            // subroutine which continued to here.
            //
            returnsub();
            break;

         case blk_tail_call_m_cons:
            // Returns the cons of the value on the stack with
            // reg[regRetval].
            //
            // This has a distinctive pattern of use:
            //
            //   store(head_to_be);
            //   gosub(sub_foo,blk_tail_call_m_cons);
            //
            if ( CLEVER_TAIL_CALL_MOD_CONS )
            {
               // Recycles the stack cell holding the m cons argument
               // for the m cons operation.
               //
               // In effect, just reverses the end of the stack onto
               // the return result.
               reg[regTmp0]   = reg[regStack];
               reg[regStack]  = cdr(reg[regStack]);
               setcdr(reg[regTmp0],reg[regRetval]);
               reg[regRetval] = reg[regTmp0];
            }
            else
            {
               reg[regTmp0]   = restore();
               reg[regTmp1]   = reg[regRetval];
               reg[regRetval] = cons(reg[regTmp0],reg[regTmp1]);
            }
            returnsub();
            break;

         case blk_error:
            // Returns to outside control, translating the internally
            // visible error codes into externally visible ones.
            //
            switch ( reg[regError] )
            {
            case ERR_OOM:       return OUT_OF_MEMORY;
            case ERR_INTERNAL:  return INTERNAL_ERROR;
            case ERR_LEXICAL:   return FAILURE_LEXICAL;
            case ERR_SEMANTIC:  return FAILURE_SEMANTIC;
            case ERR_NOT_IMPL:  return UNIMPLEMENTED;
            default:            
               if ( verb ) log("unknown error code: " + pp(reg[regError]));
               return INTERNAL_ERROR;
            }

         default:
            if ( verb ) log("bogus op: " + pp(reg[regPc]));
            raiseError(ERR_INTERNAL);
            break;
         }
      }

      return INCOMPLETE;
   }

   // Notes:
   // 
   // A slot is a 32 bit unsigned quantity.  
   //
   // Each register is an independent slot.
   //
   // The heap is an array of slots organized into adjacent pairs.
   // Each pair constitutes a cell, which is referred to by the offset
   // of the first slot.  As these offsets are always even, for
   // bandwidth in cell pointers they are generally encoded by
   // shifting them right 1 bit.
   // 
   // A list is a chain of cells, with the second slot in each cell
   // containing a pointer to the next cell.  NIL is used to indicate
   // the empty list.
   // 
   // A queue is implemented as a cell.  If the queue is empty, both
   // slots in the cell are NIL.  Otherwise, the first slot points to
   // the first cell of a list, and the second slot points to the last
   // cell of the same list.  While more complex than a simple list,
   // this makes it easier to extend the queue either at the front or
   // at the back.
   // 
   // I have deliberately chosen for the bit pattern 0x00000000 to
   // always be invalid in any slot: it doesn't mean 0, NIL, the empty
   // list, false, or anything else.  Although I rule out some cute
   // optimizations this way, I also enforce an extra discipline which
   // prevents me from from relying on the Java runtime to initialize
   // things cleanly.
   //
   // This decision may be subject to change if I ever run out of
   // critical bandwidth or come to care about performance here.
   
   private static final int MASK_TYPE    = 0xF0000000; // matches SHIFT_TYPE
   private static final int SHIFT_TYPE   = 28;         // matches MASK_TYPE
   private static final int MASK_VALUE   = ~MASK_TYPE;

   private static final int TYPE_NIL      = 0x10000000;
   private static final int TYPE_FIXINT   = 0x20000000;
   private static final int TYPE_CELL     = 0x30000000;
   private static final int TYPE_CHAR     = 0x40000000;
   private static final int TYPE_ERROR    = 0x50000000;
   private static final int TYPE_BOOLEAN  = 0x60000000;
   private static final int TYPE_SENTINEL = 0x70000000;
   private static final int TYPE_SUBP     = 0x80000000; // procedure-like
   private static final int TYPE_SUBS     = 0x90000000; // special-form-like

   // In many of these constants, I would prefer to initialize them as
   // code(TYPE_FOO,n) rather than TYPE_FOO|n, for consistency and to
   // maintain the abstraction barrier.
   //
   // Unfortunately, even though code() is static, idempotent, and a
   // matter of simple arithmetic, javac does not let me use the
   // resultant names in switch statements.  In C, I'd just make
   // code() a macro and be done with it.
   //
   // Also, I'm using fairly random values for the differentiators
   // among TYPE_NIL, TYPE_SENTINEL, TYPE_BOOLEAN, and TYPE_ERROR.
   //
   // Since each of these has only a finite, definite number of valid
   // values, using junk there is a good error-detection mechanism
   // which validates that my checks are precise and I'm not doing any
   // silly arithmetic or confusing, say, TYPE_NIL with the value NIL
   // - although one imagines lower-level implementations where it
   // would be more efficient to use 0, 1, 2, ...etc.

   private static final int NIL                 = TYPE_NIL      | 37;

   private static final int EOF                 = TYPE_SENTINEL | 97;
   private static final int UNDEFINED           = TYPE_SENTINEL | 16;
   private static final int VOID                = TYPE_SENTINEL | 65;
   private static final int IS_SYMBOL           = TYPE_SENTINEL | 79;
   private static final int IS_STRING           = TYPE_SENTINEL | 32;
   private static final int IS_PROCEDURE        = TYPE_SENTINEL | 83;
   private static final int IS_SPECIAL_FORM     = TYPE_SENTINEL | 54;

   private static final int TRUE                = TYPE_BOOLEAN  | 37;
   private static final int FALSE               = TYPE_BOOLEAN  | 91;

   private static final int ERR_OOM             = TYPE_ERROR    | 42;
   private static final int ERR_INTERNAL        = TYPE_ERROR    | 18;
   private static final int ERR_LEXICAL         = TYPE_ERROR    | 11;
   private static final int ERR_SEMANTIC        = TYPE_ERROR    |  7;
   private static final int ERR_NOT_IMPL        = TYPE_ERROR    | 87;

   private static final int regFreeCellList     =   0; // unused cells

   private static final int regStack            =   1; // the runtime stack
   private static final int regPc               =   2; // opcode to return to

   private static final int regError            =   3; // NIL or a TYPE_ERROR
   private static final int regErrorPc          =   4; // reg[regPc] of err
   private static final int regErrorStack       =   5; // reg[regStack] of err

   private static final int regIn               =   6; // input char queue
   private static final int regOut              =   7; // output char queue

   private static final int regArg0             =   8; // argument
   private static final int regArg1             =   9; // argument
   private static final int regArg2             =  10; // argument
   private static final int regTmp0             =  11; // temporary
   private static final int regTmp1             =  12; // temporary
   private static final int regTmp2             =  13; // temporary
   private static final int regTmp3             =  14; // temporary
   private static final int reg_Unused          =  15; // temporary
   private static final int regRetval           =  16; // return value

   private static final int regEnv              =  17; // list of env frames

   private static final int numRegisters        =  32;          // in slots
   private static final int heapSize            =   4 * 1024;   // in cells

   //  16 kcells:  0.5 sec
   //  32 kcells:  0.6 sec
   //  64 kcells:  1.0 sec
   // 128 kcells:  4.2 sec  *** big nonlinearity up
   // 256 kcells: 10.6 sec  *** small nonlinearity up
   // 512 kcells: 11.5 sec  *** small nonlinearity down

   private final int[] reg     = new int[numRegisters];
   private final int[] heap    = new int[2*heapSize];
   private int         heapTop        = 0; // in slots
   public  int         numCallsToCons = 0;
   public  int         maxHeapTop     = 0;

   // With opcodes, proper subroutines entry points (entry points
   // which can be expected to follow stack discipline and balance)
   // get names, and must be a multiple of 0x10.
   //
   // Helper opcodes do not get a name: they use their parent's name
   // plus 0x0..0xF.
   //
   // An exception to the naming policy is blk_tail_call and
   // blk_error.  These are not proper subroutines, but instead they
   // are utility blocks used from many places.

   private static final int MASK_BLOCKID         =               0x0000000F;
   private static final int SHIFT_BLOCKID        =                        0;
   private static final int MASK_ARITY           =               0x00F00000;
   private static final int SHIFT_ARITY          =                       20;

   private static final int A0                   =       0x0 << SHIFT_ARITY;
   private static final int A1                   =       0x1 << SHIFT_ARITY;
   private static final int A2                   =       0x2 << SHIFT_ARITY;
   private static final int A3                   =       0x3 << SHIFT_ARITY;
   private static final int AX                   =       0xF << SHIFT_ARITY;

   private static final int sub_rep              = TYPE_SUBP | A0 |  0x1000;
   private static final int sub_rp               = TYPE_SUBP | A0 |  0x1100;

   private static final int sub_read             = TYPE_SUBP | A0 |  0x2000;
   private static final int sub_read_list        = TYPE_SUBP | A0 |  0x2100;
   private static final int sub_read_list_open   = TYPE_SUBP | A0 |  0x2110;
   private static final int sub_read_atom        = TYPE_SUBP | A0 |  0x2200;
   private static final int sub_read_num         = TYPE_SUBP | A0 |  0x2300;
   private static final int sub_read_num_loop    = TYPE_SUBP | A0 |  0x2310;
   private static final int sub_read_octo_tok    = TYPE_SUBP | A0 |  0x2400;
   private static final int sub_read_symbol      = TYPE_SUBP | A0 |  0x2500;
   private static final int sub_read_string      = TYPE_SUBP | A0 |  0x2600;
   private static final int sub_read_symbol_body = TYPE_SUBP | A0 |  0x2700;
   private static final int sub_read_string_body = TYPE_SUBP | A0 |  0x2800;
   private static final int sub_read_burn_space  = TYPE_SUBP | A0 |  0x2900;

   private static final int sub_eval             = TYPE_SUBS | A2 |  0x3000;
   private static final int sub_eval_look_env    = TYPE_SUBS | A2 |  0x3100;
   private static final int sub_eval_look_frame  = TYPE_SUBS | A2 |  0x3110;
   private static final int sub_eval_list        = TYPE_SUBS | A2 |  0x3200;

   private static final int sub_apply            = TYPE_SUBP | A2 |  0x4000;
   private static final int sub_apply_builtin    = TYPE_SUBP | A2 |  0x4100;
   private static final int sub_apply_user       = TYPE_SUBP | A2 |  0x4200;

   private static final int sub_print            = TYPE_SUBP | A1 |  0x5000;
   private static final int sub_print_list       = TYPE_SUBP | A1 |  0x5100;
   private static final int sub_print_list_elems = TYPE_SUBP | A1 |  0x5200;
   private static final int sub_print_string     = TYPE_SUBP | A1 |  0x5300;
   private static final int sub_print_chars      = TYPE_SUBP | A1 |  0x5400;

   private static final int sub_equal_p          = TYPE_SUBP | A2 |  0x6000;
   private static final int sub_zip              = TYPE_SUBP | A2 |  0x6100;
   private static final int sub_let              = TYPE_SUBS | A2 |  0x6200;
   private static final int sub_let_bindings     = TYPE_SUBS | A2 |  0x6210;
   private static final int sub_begin            = TYPE_SUBS | AX |  0x6300;
   private static final int sub_case             = TYPE_SUBS | AX |  0x6400;
   private static final int sub_case_search      = TYPE_SUBS | A2 |  0x6410;
   private static final int sub_case_in_list_p   = TYPE_SUBP | A2 |  0x6420;
   private static final int sub_cond             = TYPE_SUBS | AX |  0x6500;

   private static final int sub_add              = TYPE_SUBP | A2 |  0x7000;
   private static final int sub_add0             = TYPE_SUBP | A0 |  0x7010;
   private static final int sub_add1             = TYPE_SUBP | A1 |  0x7020;
   private static final int sub_add3             = TYPE_SUBP | A3 |  0x7030;
   private static final int sub_mul              = TYPE_SUBP | A2 |  0x7040;
   private static final int sub_sub              = TYPE_SUBP | A2 |  0x7050;
   private static final int sub_lt_p             = TYPE_SUBP | A2 |  0x7060;

   private static final int sub_cons             = TYPE_SUBP | A2 |  0x7200;
   private static final int sub_car              = TYPE_SUBP | A1 |  0x7300;
   private static final int sub_cdr              = TYPE_SUBP | A1 |  0x7400;
   private static final int sub_list             = TYPE_SUBP | AX |  0x7500;
   private static final int sub_if               = TYPE_SUBS | A3 |  0x7600;
   private static final int sub_quote            = TYPE_SUBS | A1 |  0x7700;
   private static final int sub_define           = TYPE_SUBS | A2 |  0x7800;
   private static final int sub_lambda           = TYPE_SUBS | A2 |  0x7900;

   private static final int blk_tail_call        = TYPE_SUBP | A0 | 0x10001;
   private static final int blk_tail_call_m_cons = TYPE_SUBP | A0 | 0x10002;
   private static final int blk_error            = TYPE_SUBP | A0 | 0x10003;

   // jump() is icky.  I have deliberately not used it, in favor of
   // tail recursion wherever possible - even *before* the tail
   // recursion optimization has been written.
   //
   // I feel like, wherever I'm tempted to use jump(), there was a
   // recursive form that I missed - and when I find it, I end up with
   // something a lot shorter and more reusable.
   //
   // Seems to me I should be eating my own dogfood on the recursion a
   // bit more.
   // 
   // Of course, at some point I'll probably be introducing a
   // conditional branch...

   private void gosub ( final int nextOp, final int continuationOp )
   {
      final boolean verb = false;
      if ( verb ) log("  gosub()");
      if ( verb ) log("    old stack: " + reg[regStack]);
      if ( DEBUG )
      {
         final int tn = type(nextOp);
         if ( TYPE_SUBP != tn && TYPE_SUBS != tn )
         {
            if ( verb ) log("    non-op: " + pp(nextOp));
            raiseError(ERR_INTERNAL);
            return;
         }
         if ( 0 != ( MASK_BLOCKID & nextOp ) )
         {
            if ( verb ) log("    non-sub: " + pp(nextOp));
            raiseError(ERR_INTERNAL);
            return;
         }
         final int tc = type(continuationOp);
         if ( TYPE_SUBP != tc && TYPE_SUBS != tc )
         {
            if ( verb ) log("    non-op: " + pp(continuationOp));
            raiseError(ERR_INTERNAL);
            return;
         }
         if ( 0 == ( MASK_BLOCKID & continuationOp ) )
         {
            // I believe, but am not certain, that due to the demands
            // of maintaining stack discipline, it is always invalid
            // to return to a subroutine entrypoint.
            //
            // I could be wrong about this being an error.
            if ( verb ) log("    full-sub: " + pp(continuationOp));
            raiseError(ERR_INTERNAL);
            return;
         }
      }
      if ( NIL != reg[regError] )
      {
         if ( verb ) log("    flow suspended for error: " + reg[regError]);
         return;
      }
      if ( PROPERLY_TAIL_RECURSIVE && blk_tail_call == continuationOp )
      {
         // Tail recursion is so cool.
      }
      else
      {
         store(continuationOp);
         if ( NIL != reg[regError] )
         {
            // error already reported in store()
            return;
         }
         if ( DEBUG ) scmDepth++;
      }
      reg[regPc] = nextOp;
   }

   private void returnsub ()
   {
      if ( DEBUG ) scmDepth--;
      final int c = restore();
      final int t = type(c);
      if ( TYPE_SUBP != t && TYPE_SUBS != t)
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      reg[regPc] = c;
   }

   private void store ( final int value )
   {
      final boolean verb = false;
      if ( NIL != reg[regError] )
      {
         if ( verb ) log("store(): flow suspended for error");
         return;
      }
      final int cell = cons(value,reg[regStack]);
      if ( NIL == cell )
      {
         // error already raised in cons()
         if ( verb ) log("store(): oom");
         return;
      }
      if ( verb ) log("stored:   " + pp(value));
      reg[regStack] = cell;
   }

   private int restore ()
   {
      final boolean verb = false;
      if ( NIL != reg[regError] )
      {
         if ( verb ) log("restore(): flow suspended for error");
         return NIL; // TODO: don't like this use of NIL
      }
      if ( DEBUG && NIL == reg[regStack] )
      {
         if ( verb ) log("restore(): stack underflow");
         raiseError(ERR_INTERNAL);
         return NIL; // TODO: don't like this use of NIL
      }
      if ( DEBUG && TYPE_CELL != type(reg[regStack]) )
      {
         if ( verb ) log("restore(): corrupt stack");
         raiseError(ERR_INTERNAL);
         return NIL; // TODO: don't like this use of NIL
      }
      final int cell = reg[regStack];
      final int head = car(cell);
      final int rest = cdr(cell);
      reg[regStack]  = rest;
      if ( verb ) log("restored: " + pp(head));
      if ( CLEVER_STACK_RECYCLING && NIL == reg[regError] )
      {
         // Recycle stack cell which is unreachable from user code.
         //
         // That assertion is only true and this is only a valid
         // optimization if we haven't stashed the continuation
         // someplace.  
         //
         // This optimization may not be sustainable in the
         // medium-term.
         setcdr(cell,reg[regFreeCellList]);
         reg[regFreeCellList] = cell;
      }
      return head;
   }

   /**
    * Documents an error and pushes the VM into an error-trap state.
    *
    * If an error is already documented, raiseError() reasserts the
    * error-trap state but does not document the new error.  This on
    * the principle that we care more about the first error and the
    * subsequent error is more likely a side-effect of the first.
    */
   private void raiseError ( final int err )
   {
      final boolean verb = true;
      if ( verb ) log("raiseError():");
      if ( verb ) log("  err:   " + pp(err));
      if ( verb ) log("  pc:    " + pp(reg[regPc]));
      if ( verb ) log("  stack: " + pp(reg[regStack]));
      if ( verb )
      {
         final Thread              thread = Thread.currentThread();
         final StackTraceElement[] stack  = thread.getStackTrace();
         boolean                   active = false;
         for ( int i = 0; i < stack.length; ++i )
         {
            final StackTraceElement elm = stack[i];
            if ( !active )
            {
               if ( !"raiseError".equals(elm.getMethodName()))        continue;
               if ( !getClass().getName().equals(elm.getClassName())) continue;
               active = true;
               continue;
            }
            if ( verb ) log("  java:  " + elm);
         }
         for ( int c = reg[regStack]; NIL != c; c = cdr(c) )
         {
            // TODO: hopefully the stack isn't corrupt....
            if ( verb ) log("  scm:   " + pp(car(c)));
         }
      }
      if ( NIL == reg[regError] ) 
      {
         if ( verb ) log("  first: documenting");
         reg[regError]      = err;
         reg[regErrorPc]    = reg[regPc];
         reg[regErrorStack] = reg[regStack];
      }
      else
      {
         if ( verb ) log("  late:  supressing");
      }
      reg[regPc]    = blk_error;
      reg[regStack] = NIL;
      if ( DEBUG && TYPE_ERROR != type(err) )
      {
         // TODO: Bad call to raiseError()? Are we out of tricks?
         throw new RuntimeException("bogus error code: " + pp(err));
      }
      if ( DEBUG && ERR_INTERNAL == err )
      {
         throw new RuntimeException("internal error");
      }
   }

   /**
    * Checks that the VM is internally consistent, that all internal
    * invariants are still true.
    *
    * @returns SUCCESS on success, else (FAILURE+code)
    */
   public int selfTest ()
   {
      final boolean verb = !SILENT;
      if ( verb ) log("selfTest()");

      // consistency check
      final int t = 0x12345678 & TYPE_FIXINT;
      final int v = 0x12345678 & MASK_VALUE;
      final int c = code(t,v);
      if ( t != type(c) )
      {
         return INTERNAL_ERROR;
      }
      if ( v != value(c) )
      {
         return INTERNAL_ERROR;
      }

      final int numFree      = listLength(reg[regFreeCellList]);
      final int numStack     = listLength(reg[regStack]);
      final int numGlobalEnv = listLength(reg[regEnv]);
      if ( verb ) log("  numFree:      " + numFree);
      if ( verb ) log("  numStack:     " + numStack);
      if ( verb ) log("  numGlobalEnv: " + numGlobalEnv);

      // if this is a just-created selfTest(), we should see i = heap.length/2

      // Now a test which burns a free cell.
      //
      // TODO: find a way to make this a non-mutating test?
      //
      final int i0    = code(TYPE_FIXINT,0x01234567);
      final int i1    = code(TYPE_FIXINT,0x07654321);
      final int i2    = code(TYPE_FIXINT,0x01514926);
      final int cell0 = cons(i0,i1); 
      if ( !DEFER_HEAP_INIT && (NIL == cell0) != (numFree <= 0) )
      {
         if ( verb ) log("  A: " + pp(cell0) + " " + numFree);
         return INTERNAL_ERROR;
      }
      if ( NIL == cell0 )
      {
         return OUT_OF_MEMORY;
      }

      if ( i0 != car(cell0) )
      {
         if ( verb ) log("  B: " + pp(car(cell0)) + " " + pp(i0));
         return INTERNAL_ERROR;
      }
      if ( i1 != cdr(cell0) )
      {
         if ( verb ) log("  C: " + pp(cdr(cell0)) + " " + pp(i1));
         return INTERNAL_ERROR;
      }
      final int cell1 = cons(i2,cell0); 
      if ( !DEFER_HEAP_INIT && (NIL == cell1) != (numFree <= 1) )
      {
         if ( verb ) log("  D");
         return INTERNAL_ERROR;
      }
      if ( NIL != cell1 )
      {
         if ( i2 != car(cell1) )
         {
            if ( verb ) log("  E");
            return INTERNAL_ERROR;
         }
         if ( cell0 != cdr(cell1) )
         {
            if ( verb ) log("  F");
            return INTERNAL_ERROR;
         }
         if ( i0 != car(cdr(cell1)) )
         {
            if ( verb ) log("  G");
            return INTERNAL_ERROR;
         }
         if ( i1 != cdr(cdr(cell1)) )
         {
            if ( verb ) log("  H");
            return INTERNAL_ERROR;
         }
         if ( !DEFER_HEAP_INIT && 
              listLength(reg[regFreeCellList]) != numFree-2 )
         {
            if ( verb ) log("  I");
            return INTERNAL_ERROR;
         }
      }

      final int newNumFree = listLength(reg[regFreeCellList]);
      if ( verb ) log("  newNumFree: " + newNumFree);

      return SUCCESS;
   }


   ////////////////////////////////////////////////////////////////////
   //
   // encoding slots
   //
   ////////////////////////////////////////////////////////////////////

   private static int type ( final int code )
   {
      return MASK_TYPE  & code;
   }
   private static int value ( final int code )
   {
      return MASK_VALUE & code;
   }
   private static int code ( final int type, final int value )
   {
      return (MASK_TYPE & type) | (MASK_VALUE & value);
   }
   

   ////////////////////////////////////////////////////////////////////
   //
   // encoding cells
   //
   ////////////////////////////////////////////////////////////////////

   /**
    * @returns NIL in event of error (in which case an error is
    * raised), else a newly allocated and initialize cons cell.
    *
    * TODO: I feel funny about using NIL this way
    */
   private int cons ( final int car, final int cdr )
   {
      if ( PROFILE )
      {
         numCallsToCons++;
      }
      int cell = reg[regFreeCellList];
      if ( NIL == cell )
      {
         if ( heapTop >= heap.length )
         {
            raiseError(ERR_OOM);
            return NIL;
         }
         final int top;
         if (DEFER_HEAP_INIT )
         {
            // heapTop in slots, 2* for cells, only init a piece of it
            // this pass.
            top = heapTop + 2*256;
         }
         else
         {
            // init all of the heap: pretty slow, even if you might
            // eventually use it, because it's a badly non-local pass
            // accros the entire heap on startup.
            //
            // Even if you're going to use all of it, at least we
            // don't initialize a piece of heap until right before the
            // higher-level program was going to get to it anyhow.
            top = heap.length;
         }
         final int lim = (top < heap.length) ? top : heap.length;
         for ( ; heapTop < lim; heapTop += 2 )
         {
            // TODO: is the car of free list, e.g. heap[i+0], superfluous
            // here?  Don't we always assign it before returning it from
            // cons()?
            //
            // Hmmm.  Suggests there might be a lot of extra space
            // sitting in the free cell list... we could maintain it as a
            // heap and prefer pulling lower indices, to improve locality
            // of reference and reduce the work required for heap
            // compaction...
            heap[heapTop+0]      = NIL;
            heap[heapTop+1]      = reg[regFreeCellList];
            reg[regFreeCellList] = code(TYPE_CELL,(heapTop >>> 1));
         }
         cell = reg[regFreeCellList];
         if ( PROFILE )
         {
            if ( heapTop > maxHeapTop )
            {
               maxHeapTop = heapTop;
            }
         }
      }
      final int t          = type(cell);
      if ( DEBUG && TYPE_CELL != t )
      {
         raiseError(ERR_INTERNAL);
         return NIL;
      }
      final int v          = value(cell);
      final int ar         = v << 1;
      final int dr         = ar + 1;
      reg[regFreeCellList] = heap[dr];
      heap[ar]             = car;
      heap[dr]             = cdr;
      return cell;
   }
   private int car ( final int cell )
   {
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return NIL;
      }
      return heap[(value(cell) << 1) + 0];
   }
   private int cdr ( final int cell )
   {
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return NIL;
      }
      return heap[(value(cell) << 1) + 1];
   }
   private void setcar ( final int cell, final int value )
   {
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      heap[(value(cell) << 1) + 0] = value;
   }
   private void setcdr ( final int cell, final int value )
   {
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      heap[(value(cell) << 1) + 1] = value;
   }

   ////////////////////////////////////////////////////////////////////
   //
   // encoding lists (mostly same as w/ cons)
   //
   ////////////////////////////////////////////////////////////////////

   /**
    * @returns the length of the list rooted at cell: as a regular
    * int, not as a TYPE_FIXINT!  Is unsafe about cycles!
    */
   private int listLength ( final int cell )
   {
      int len = 0;
      for ( int p = cell; NIL != p; p = cdr(p) )
      {
         len++;
      }   
      return len;
   }


   ////////////////////////////////////////////////////////////////////
   //
   // encoding queues (over laps a lot w/ cons and list)
   //
   ////////////////////////////////////////////////////////////////////

   /**
    * @returns a new, empty queue, or NIL on failure
    *
    * TODO: I feel funny about using NIL this way
    */
   private int queueCreate ()
   {
      final boolean verb = false;
      final int queue = cons(NIL,NIL);
      if ( verb ) log("  queueCreate(): returning " + pp(queue));
      return queue;
   }

   /**
    * Pushes value onto the back of the queue.
    */
   private void queuePushBack ( final int queue, final int value )
   {
      final boolean verb = false;
      final int queue_t = type(queue);
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verb ) log("  queuePushBack(): non-queue " + pp(queue));
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( DEBUG && EOF == value ) 
      {
         // EOF cannot go in queues, lest it confuse the return value
         // channel in one of the peeks or pops.
         if ( verb ) log("  queuePushBack(): EOF");
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( DEBUG && TYPE_CHAR != type(value) ) 
      {
         // OK, this is BS: I haven't decided for queues to be only of
         // characters.  But so far I'm only using them as such ...
         if ( verb ) log("  queuePushBack(): non-char " + pp(value));
         raiseError(ERR_INTERNAL);
         return;
      }

      final int new_cell = cons(value,NIL);
      if ( NIL == new_cell )
      {
         if ( verb ) log("  queuePushBack(): oom");
         return; // avoid further damage
      }

      // INVARIANT: head and tail are both NIL (e.g. empty) or they
      // are both cells!
      final int h = car(queue);
      final int t = cdr(queue);

      if ( NIL == h || NIL == t )
      {
         if ( NIL != h || NIL != t )
         {
            if ( verb ) log("  queuePushBack(): bad " + pp(h) + " " + pp(t));
            raiseError(ERR_INTERNAL); // corrupt queue
            return;
         }
         if ( verb ) log("  queuePushBack(): pushing to empty " + pp(value));
         setcar(queue,new_cell);
         setcdr(queue,new_cell);
         return;
      }

      if ( (TYPE_CELL != type(h)) || (TYPE_CELL != type(t)) )
      {
         if ( verb ) log("  queuePushBack(): bad " + pp(h) + " " + pp(t));
         raiseError(ERR_INTERNAL); // corrupt queue
         return;
      }

      if ( verb ) log("  queuePushBack(): pushing to nonempty " + pp(value));
      setcdr(t,    new_cell);
      setcdr(queue,new_cell);
   }

   /**
    * @returns the object at the front of the queue (in which case the
    * queue is mutated to remove the object), or EOF if empty
    */
   private int queuePopFront ( final int queue )
   {
      final boolean verb = false;
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verb ) log("  queuePopFront(): non-queue " + pp(queue));
         raiseError(ERR_INTERNAL);
         return EOF;
      }
      final int head = car(queue);
      if ( NIL == head )
      {
         if ( verb ) log("  queuePopFront(): empty " + pp(queue));
         return EOF;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         if ( verb ) log("  queuePopFront(): corrupt queue " + pp(head));
         raiseError(ERR_INTERNAL); // corrupt queue
         return EOF;
      }
      final int value = car(head);
      setcar(queue,cdr(head));
      if ( NIL == car(queue) )
      {
         setcdr(queue,NIL);
      }
      if ( verb ) log("  queuePopFront(): popped " + pp(value));
      return value;
   }

   /**
    * @returns the object at the front of the queue, or EOF if empty
    */
   private int queuePeekFront ( final int queue )
   {
      final boolean verb = false;
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verb ) log("  queuePeekFront(): non-queue " + pp(queue));
         raiseError(ERR_INTERNAL);
         return EOF;
      }
      final int head = car(queue);
      if ( NIL == head )
      {
         if ( verb ) log("  queuePeekFront(): empty " + pp(queue));
         return EOF;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         if ( verb ) log("  queuePeekFront(): corrupt queue " + pp(head));
         raiseError(ERR_INTERNAL); // corrupt queue
         return EOF;
      }
      final int value = car(head);
      if ( verb ) log("  queuePeekFront(): peeked " + pp(value));
      return value;
   }

   // scmDepth and javaDepth are ONLY used for debug: they are *not*
   // sanctioned VM state.
   //
   private int scmDepth  = 0;
   private int javaDepth = 0;
   private void log ( final Object msg )
   {
      if ( SILENT ) return;
      final int lim = (scmDepth + javaDepth);
      for (int i = 0; i < lim; ++i)
      {
         System.out.print("  ");
      }
      System.out.println(msg);
   }

   private void logrec ( String tag, int c )
   {
      if ( SILENT ) return;
      tag += " ";
      if ( TYPE_CELL == type(c) )
      {
         final int first = car(c);
         switch (car(c))
         {
         case IS_SYMBOL:
         case IS_STRING:
            final StringBuilder buf = new StringBuilder();
            for ( c = cdr(c); NIL != c; c = cdr(c) )
            {
               buf.append((char)value(car(c)));
            }
            log(tag + pp(first) + " " + buf);
            break;
         default:
            log(tag + pp(c));
            tag += " ";
            logrec(tag,car(c));
            logrec(tag,cdr(c));
            break;
         }
      }
      else
      {
         log(tag + pp(c));
      }
   }

   private static String pp ( final int code )
   {
      switch (code)
      {
      case NIL:                  return "NIL";
      case EOF:                  return "EOF";
      case UNDEFINED:            return "UNDEFINED";
      case VOID:                 return "VOID";
      case IS_STRING:            return "IS_STRING";
      case IS_SYMBOL:            return "IS_SYMBOL";
      case IS_PROCEDURE:         return "IS_PROCEDURE";
      case IS_SPECIAL_FORM:      return "IS_SPECIAL_FORM";
      case TRUE:                 return "TRUE";
      case FALSE:                return "FALSE";
      case ERR_OOM:              return "ERR_OOM";
      case ERR_INTERNAL:         return "ERR_INTERNAL";
      case ERR_LEXICAL:          return "ERR_LEXICAL";
      case ERR_SEMANTIC:         return "ERR_SEMANTIC";
      case ERR_NOT_IMPL:         return "ERR_NOT_IMPL";
      case blk_tail_call:        return "blk_tail_call";
      case blk_tail_call_m_cons: return "blk_tail_call_m_cons";
      case blk_error:            return "blk_error";
      }
      final int t = type(code);
      final int v = value(code);
      final StringBuilder buf = new StringBuilder();
      switch (t)
      {
      case TYPE_NIL:      buf.append("nil");      break;
      case TYPE_FIXINT:   buf.append("fixint");   break;
      case TYPE_CELL:     buf.append("cell");     break;
      case TYPE_CHAR:     buf.append("char");     break;
      case TYPE_ERROR:    buf.append("error");    break;
      case TYPE_BOOLEAN:  buf.append("boolean");  break;
      case TYPE_SENTINEL: buf.append("sentinel"); break;
      case TYPE_SUBP:
      case TYPE_SUBS:
         switch (code & ~MASK_BLOCKID)
         {
         case sub_rep:              buf.append("sub_rep");              break;
         case sub_rp:               buf.append("sub_rp");               break;
         case sub_read:             buf.append("sub_read");             break;
         case sub_read_list:        buf.append("sub_read_list");        break;
         case sub_read_list_open:   buf.append("sub_read_list_open");   break;
         case sub_read_atom:        buf.append("sub_read_atom");        break;
         case sub_read_num:         buf.append("sub_read_num");         break;
         case sub_read_num_loop:    buf.append("sub_read_num_loop");    break;
         case sub_read_octo_tok:    buf.append("sub_read_octo_tok");    break;
         case sub_read_symbol:      buf.append("sub_read_symbol");      break;
         case sub_read_string:      buf.append("sub_read_string");      break;
         case sub_read_symbol_body: buf.append("sub_read_symbol_body"); break;
         case sub_read_string_body: buf.append("sub_read_string_body"); break;
         case sub_read_burn_space:  buf.append("sub_read_burn_space");  break;
         case sub_eval:             buf.append("sub_eval");             break;
         case sub_eval_list:        buf.append("sub_eval_list");        break;
         case sub_eval_look_env:    buf.append("sub_eval_look_env");    break;
         case sub_eval_look_frame:  buf.append("sub_eval_look_frame");  break;
         case sub_apply:            buf.append("sub_apply");            break;
         case sub_apply_builtin:    buf.append("sub_apply_builtin");    break;
         case sub_apply_user:       buf.append("sub_apply_user");       break;
         case sub_print:            buf.append("sub_print");            break;
         case sub_print_list:       buf.append("sub_print_list");       break;
         case sub_print_list_elems: buf.append("sub_print_list_elems"); break;
         case sub_print_string:     buf.append("sub_print_string");     break;
         case sub_print_chars:      buf.append("sub_print_chars");      break;
         case sub_equal_p:          buf.append("sub_equal_p");          break;
         case sub_let:              buf.append("sub_let");              break;
         case sub_let_bindings:     buf.append("sub_let_bindings");     break;
         case sub_begin:            buf.append("sub_begin");            break;
         case sub_case:             buf.append("sub_case");             break;
         case sub_case_search:      buf.append("sub_case_search");      break;
         case sub_case_in_list_p:   buf.append("sub_case_in_list_p");   break;
         case sub_cond:             buf.append("sub_cond");             break;
         case sub_zip:              buf.append("sub_zip");              break;
         case sub_add:              buf.append("sub_add");              break;
         case sub_add0:             buf.append("sub_add0");             break;
         case sub_add1:             buf.append("sub_add1");             break;
         case sub_add3:             buf.append("sub_add3");             break;
         case sub_mul:              buf.append("sub_mul");              break;
         case sub_sub:              buf.append("sub_sub");              break;
         case sub_lt_p:             buf.append("sub_lt_p");             break;
         case sub_cons:             buf.append("sub_cons");             break;
         case sub_car:              buf.append("sub_car");              break;
         case sub_cdr:              buf.append("sub_cdr");              break;
         case sub_list:             buf.append("sub_list");             break;
         case sub_if:               buf.append("sub_if");               break;
         case sub_quote:            buf.append("sub_quote");            break;
         case sub_define:           buf.append("sub_define");           break;
         case sub_lambda:           buf.append("sub_lambda");           break;
         default:
            buf.append("sub_"); 
            hex(buf,v & ~MASK_BLOCKID,SHIFT_TYPE/4); 
            break;
         }
         break;
      default:          
         buf.append('?'); 
         buf.append(t>>SHIFT_TYPE); 
         buf.append('?'); 
         break;
      }
      buf.append("|");
      switch (t)
      {
      case TYPE_NIL:    
         buf.append("nil");  
         break;
      case TYPE_CHAR:   
         if ( ' ' <= v && v < '~' )
         {
            buf.append('\''); 
            buf.append((char)v); 
            buf.append('\''); 
         }
         else if ( 0 <= v && v <= 255 )
         {
            // TODO: I think R2R5 demands bigger characters than ASCII
            buf.append(v); 
         }
         else
         {
            buf.append('?'); 
            buf.append(v); 
            buf.append('?'); 
         }
         break;
      case TYPE_BOOLEAN:   
         buf.append('#'); 
         switch (code)
         {
         case TRUE:  
            buf.append('t'); 
            break;
         case FALSE: 
            buf.append('f'); 
            break;
         default:    
            buf.append('?'); 
            buf.append(v); 
            buf.append('?'); 
            break;
         }
         break;
      case TYPE_SUBP:   
      case TYPE_SUBS:   
         hex(buf,v & MASK_BLOCKID,1);
         break;
      default:          
         buf.append(v);       
         break;
      }
      return buf.toString();
   }

   private static void hex ( final StringBuilder buf, int code, int nibbles )
   {
      buf.append('0');
      buf.append('x');
      for ( ; nibbles > 0; --nibbles )
      {
         final int  nib = 0xF & (code >>> ( (nibbles-1) * 4 ));
         final char c;
         if ( nib < 10 )
         {
            c = (char)(nib + (int)'0');
         }
         else
         {
            c = (char)(nib + (int)'A' - 10);
         }
         buf.append(c);
      }
   }

   private static String hex ( int code, int nibbles )
   {
      final StringBuilder buf = new StringBuilder();
      hex(buf,code,nibbles);
      return buf.toString();
   }

   private void prebind ( final String name, final int code )
   {
      // TODO: this is sloppy, non-LISPy, magic, and has no error
      // checking.
      //
      // Longer-term I want lexical-level bindings for this stuff, and
      // to just express the "standard" name bindings as a series of
      // defines against the lexically supported stuff.
      //
      // But I did this now because I don't want to go inventing some
      // off-specification lexical bindings which I will be committed
      // to maintianging longer term until *after* I've got (eval)
      // working and the entry points in the microcode tied down more.
      //
      final int queue = queueCreate();
      for ( int i = 0; i < name.length(); i++ )
      {
         queuePushBack(queue,code(TYPE_CHAR,name.charAt(i)));
      }
      final int symbol   = cons(IS_SYMBOL,car(queue));
      final int binding  = cons(symbol,code);
      final int frame    = car(reg[regEnv]);
      final int newframe = cons(binding,frame);
      setcar(reg[regEnv],newframe);
   }
}
