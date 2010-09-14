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

import java.util.Random; // TODO: this doesn't belong here

public class JhwScm
{
   public static final boolean PROFILE                   = true;
   public static final boolean DEFER_HEAP_INIT           = true;
   public static final boolean PROPERLY_TAIL_RECURSIVE   = true;
   public static final boolean CLEVER_TAIL_CALL_MOD_CONS = true;
   public static final boolean CLEVER_STACK_RECYCLING    = true;

   public static final int     SUCCESS          =  0;
   public static final int     INCOMPLETE       = -1;
   public static final int     BAD_ARG          = -2;
   public static final int     OUT_OF_MEMORY    = -3;
   public static final int     FAILURE_LEXICAL  = -4;
   public static final int     FAILURE_SEMANTIC = -5;
   public static final int     UNIMPLEMENTED    = -6;
   public static final int     INTERNAL_ERROR   = -7;

   private static final int    STRESS_OUTPUT_PERCENT = 13;
   private static final Random debugRand             = new Random(1234);

   private final boolean SILENT;
   private final boolean DEBUG;  // check things which should never happen

   public JhwScm ( final boolean doREP, 
                   final boolean SILENT, 
                   final boolean DEBUG )
   {
      this.SILENT = SILENT;
      this.DEBUG  = DEBUG;

      final boolean verb = false;

      if ( verb ) log("JhwScm.JhwScm()");
      for ( int i = 0; i < reg.length; i++ )
      {
         reg[i] = NIL;
      }

      reg[regFreeCellList] = NIL;

      reg[regPc]  = doREP ? sub_rep : sub_rp;
      reg[regIn]  = queueCreate();
      reg[regOut] = queueCreate();
      reg[regEnv] = cons(NIL,NIL);
      log("INITIAL ENV: " + pp(reg[regEnv]));

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
      prebind("read",   sub_read);
      if ( false )
      {
         prebind("print",sub_print);   // SICP, my preference from tradition
      }
      else
      {
         prebind("display",sub_print); // R5RS, Guile, Scsh, get off my lawn!
      }
      prebind("map",    sub_map);
   }

   /**
    * Transfers up to len bytes from in[off..len-1] to the VM's input
    * port buffer.
    *
    * @returns the number of bytes copied, else an error code <= -2.
    */
   public int input ( final byte[] buf, final int off, final int len ) 
   {
      final boolean verb = true && !SILENT;
      if ( DEBUG ) javaDepth = 0;
      if ( verb ) log("input(): " + off + "+" + len + " / " + buf.length);
      if ( null == buf )
      {
         if ( verb ) log("input():  null arg");
         return BAD_ARG;
      }
      if ( off < 0 )
      {
         if ( verb ) log("input(): bad off: " + off);
         return BAD_ARG;
      }
      if ( len < 0 )
      {
         if ( verb ) log("input(): bad len: " + len);
         return BAD_ARG;
      }
      if ( off+len > buf.length )
      {
         if ( verb ) log("output(): " + off + "+" + len + " / " + buf.length);
         return BAD_ARG;
      }
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
      final int num    = DEBUG ? debugRand.nextInt(len+1) : len;
      final int oldCar = car(reg[regIn]);
      final int oldCdr = car(reg[regIn]);
      for ( int i = 0; i < num; ++i )
      {
         final byte c    = buf[off+i];
         final int  code = code(TYPE_CHAR,0xFF&c);
         queuePushBack(reg[regIn],code);
         if ( NIL != reg[regError] )
         {
            setcar(reg[regIn],oldCar);
            setcdr(reg[regIn],oldCdr);
            return INTERNAL_ERROR; // TODO: proper proxy for reg[regError]
         }
         if ( PROFILE ) numInput++;
         if ( PROFILE ) universalNumInput++;
      }
      return num;
   }

   /**
    * Transfers up to len bytes from the VM's output port buffer and
    * copies them to buf[off..len-1].
    *
    * @returns the number of bytes written, or -1 if none were written
    * and the VM's output buffer is empty, else an error code <= -2.
    */
   public int output ( final byte[] buf, final int off, final int len ) 
   {
      final boolean verb = true && !SILENT;
      if ( DEBUG ) javaDepth = 0;
      if ( verb ) log("output(): " + off + "+" + len + " / " + buf.length);
      if ( null == buf )
      {
         if ( verb ) log("output(): null arg");
         return BAD_ARG;
      }
      if ( off < 0 )
      {
         if ( verb ) log("output(): bad off: " + off);
         return BAD_ARG;
      }
      if ( len < 0 )
      {
         if ( verb ) log("output(): bad len: " + len);
         return BAD_ARG;
      }
      if ( off+len > buf.length )
      {
         if ( verb ) log("output(): " + off + "+" + len + " / " + buf.length);
         return BAD_ARG;
      }
      if ( NIL == reg[regOut] )
      {
         if ( verb ) log("output(): internal");
         raiseError(ERR_INTERNAL);
         return INTERNAL_ERROR;
      }
      for ( int i = 0; i < len; ++i )
      {
         if ( DEBUG && debugRand.nextInt(100) < STRESS_OUTPUT_PERCENT )
         {
            if ( verb ) log("output(): stress: " + i);
            return i;
         }
         final int f = queuePeekFront(reg[regOut]);
         if ( EOF == f )
         {
            if ( verb ) log("output(): eof: " + i);
            if ( 0 == i )
            {
               return -1;
            }
            else
            {
               return i;
            }
         }
         buf[off+i] = (byte)value(f);
         if ( verb ) log("output(): popping: " + (char)buf[off+i] + " at " + (off+i) );
         queuePopFront(reg[regOut]);
         if ( PROFILE ) numOutput++;
         if ( PROFILE ) universalNumOutput++;
      }
      if ( verb ) log("output(): done: " + len);
      return len;
   }

   /**
    * Drives all pending computation to completion.
    *
    * @param numSteps the number of VM steps to execute.  If numSteps
    * < 0, runs to completion.
    *
    * @throws nothing, not ever
    *
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
      // TODO: render these as registers!
      //
      int tmp0 = 0;
      int tmp1 = 0;

      for ( int step = 0; -1 == numSteps || step < numSteps; ++step )
      {
         if ( PROFILE ) numCycles          += 1;
         if ( PROFILE ) universalNumCycles += 1;
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
            reg[regTmp0] = queuePeekFront(reg[regIn]);
            if ( EOF == reg[regTmp0] )
            {
               reg[regRetval] = EOF;
               returnsub();
               break;
            }
            if ( DEBUG && TYPE_CHAR != type(reg[regTmp0]) )
            {
               if ( verb ) log("non-char in input: " + pp(reg[regTmp0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (value(reg[regTmp0]))
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
            reg[regTmp0] = queuePeekFront(reg[regIn]);
            if ( EOF == reg[regTmp0] )
            {
               if ( verb ) log("eof in list expr");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( code(TYPE_CHAR,')') == reg[regTmp0] )
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
            reg[regTmp0] = queuePeekFront(reg[regIn]);
            if ( DEBUG && TYPE_CHAR != type(reg[regTmp0]) )
            {
               if ( verb ) log("non-char in input: " + pp(reg[regTmp0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (value(reg[regTmp0]))
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
               reg[regTmp2] = queuePeekFront(reg[regIn]);
               if ( TYPE_CHAR == type(reg[regTmp2]) && 
                    '0' <= value(reg[regTmp2])      && 
                    '9' >= value(reg[regTmp2])       )
               {
                  if ( verb ) log("minus-starting-number");
                  gosub(sub_read_num,sub_read_atom+0x1);
               }
               else if ( EOF == reg[regTmp2] )
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
            if ( TYPE_FIXINT != type(reg[regRetval]) )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            if ( verb ) log("negating: " + pp(reg[regRetval]));
            reg[regRetval] = code(TYPE_FIXINT,-value(reg[regRetval]));
            if ( verb ) log("  to:       " + pp(reg[regRetval]));
            returnsub();
            break;
         case sub_read_atom+0x2:
            reg[regTmp0]   = restore();
            reg[regTmp1]   = car(reg[regTmp0]);
            reg[regRetval] = cons(IS_SYMBOL,reg[regTmp1]);
            if ( DEBUG )
            {
               reg[regTmp1] = reg[regTmp0];
               while ( NIL != reg[regTmp1] )
               {
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
            // Note: by leveraging sub_quote in this way, putting the
            // literal value sub_quote at the head of a constructed
            // expression which is en route to sub_eval, we force the
            // hand on other design decisions about the direct
            // (eval)ability and (apply)ability of sub_foo in general.
            //
            // We do the same in sub_define with sub_lambda, so
            // clearly I'm getting comfortable with this decision.
            // It's a syntax rewrite, nothing more, and sub_read can
            // stay simple and let the rest of the system handle it.
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
            reg[regTmp1] = queuePeekFront(reg[regIn]);
            if ( EOF == reg[regTmp1] )
            {
               if ( verb ) log("eof: returning " + pp(reg[regArg0]));
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            }
            if ( TYPE_CHAR != type(reg[regTmp1]) )
            {
               if ( verb ) log("non-char in input: " + pp(reg[regTmp1]));
               raiseError(ERR_INTERNAL);
               break;
            }
            reg[regTmp2] = reg[regArg0];
            if ( TYPE_FIXINT != type(reg[regTmp2]) )
            {
               if ( verb ) log("non-fixint in arg: " + pp(reg[regTmp2]));
               raiseError(ERR_LEXICAL);
               break;
            }
            switch (value(reg[regTmp1]))
            {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
            case '(':
            case ')':
               // terminator
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            default:
               if ( value(reg[regTmp1]) < '0' || value(reg[regTmp1]) > '9' )
               {
                  if ( verb ) log("non-digit in input: " + pp(reg[regTmp1]));
                  raiseError(ERR_LEXICAL);
                  break;
               }
               tmp0 = 10 * value(reg[regTmp2]) + (value(reg[regTmp1]) - '0');
               if ( verb ) log("first char: " + (char)value(reg[regTmp1]));
               if ( verb ) log("old accum:  " +       value(reg[regTmp2]));
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
            reg[regTmp0] = queuePeekFront(reg[regIn]);
            if ( reg[regTmp0] != code(TYPE_CHAR,'#') )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePopFront(reg[regIn]);
            reg[regTmp1] = queuePeekFront(reg[regIn]);
            if ( EOF == reg[regTmp1] )
            {
               if ( verb ) log("eof after octothorpe");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( DEBUG && TYPE_CHAR != type(reg[regTmp1]) )
            {
               if ( verb ) log("non-char in input: " + pp(reg[regTmp1]));
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePopFront(reg[regIn]);
            switch (value(reg[regTmp1]))
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
               reg[regTmp2] = queuePeekFront(reg[regIn]);
               if ( EOF == reg[regTmp2] )
               {
                  if ( verb ) log("eof after octothorpe slash");
                  raiseError(ERR_LEXICAL);
                  break;
               }
               if ( DEBUG && TYPE_CHAR != type(reg[regTmp2]) )
               {
                  if ( verb ) log("non-char in input: " + pp(reg[regTmp2]));
                  raiseError(ERR_INTERNAL);
                  break;
               }
               if ( verb ) log("character literal: " + pp(reg[regTmp2]));
               queuePopFront(reg[regIn]);
               reg[regRetval] = reg[regTmp2];
               returnsub();
               // TODO: so far, we only handle the 1-char sequences...
               break;
            default:
               log("unexpected after octothorpe: " + pp(reg[regTmp1]));
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
            // Return value UNSPECIFIED, works via side-effects.
            //
            if ( DEBUG && TYPE_CELL != type(reg[regArg0]) )
            {
               if ( verb ) log("non-queue in arg: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            reg[regTmp1] = queuePeekFront(reg[regIn]);
            if ( EOF == reg[regTmp1] )
            {
               if ( verb ) log("eof: returning");
               reg[regRetval] = UNSPECIFIED;
               returnsub();
               break;
            }
            if ( TYPE_CHAR != type(reg[regTmp1]) )
            {
               if ( verb ) log("non-char in input: " + pp(reg[regTmp1]));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (value(reg[regTmp1]))
            {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
            case '(':
            case ')':
            case '"':
               returnsub();
               break;
            default:
               queuePushBack(reg[regArg0],reg[regTmp1]);
               queuePopFront(reg[regIn]);
               gosub(sub_read_symbol_body,blk_tail_call);
               break;
            }
            break;

         case sub_read_string:
            // Parses the next string literal from reg[regIn].
            //
            reg[regTmp0] = queuePeekFront(reg[regIn]);
            if ( code(TYPE_CHAR,'"') != reg[regTmp0] )
            {
               log("non-\" leading string literal: " + pp(reg[regTmp0]));
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
            reg[regTmp0] = queuePeekFront(reg[regIn]);
            if ( code(TYPE_CHAR,'"') != reg[regTmp0] )
            {
               log("non-\" terminating string literal: " + pp(reg[regTmp0]));
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
               log("non-queue in arg: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            reg[regTmp1] = queuePeekFront(reg[regIn]);
            if ( EOF == reg[regTmp1] )
            {
               log("eof in string literal");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( TYPE_CHAR != type(reg[regTmp1]) )
            {
               log("non-char in input: " + pp(reg[regTmp1]));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (value(reg[regTmp1]))
            {
            case '"':
               reg[regRetval] = car(reg[regArg0]);
               returnsub();
               break;
            default:
               queuePushBack(reg[regArg0],reg[regTmp1]);
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
            reg[regTmp0] = queuePeekFront(reg[regIn]);
            if ( EOF == reg[regTmp0] )
            {
               reg[regRetval] = UNSPECIFIED;
               returnsub();
               break;
            }
            switch (value(reg[regTmp0]))
            {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
               queuePopFront(reg[regIn]);
               gosub(sub_read_burn_space,blk_tail_call);
               break;
            default:
               reg[regRetval] = UNSPECIFIED;
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
            case TYPE_SENTINEL:
               switch (reg[regArg0])
               {
               case TRUE:
               case FALSE:
                  // these values are self-evaluating
                  reg[regRetval] = reg[regArg0];
                  returnsub();
                  break;
               case NIL:
                  // The *value* for the empty list is not
                  // self-evaluating.
                  //
                  // Covers expressions like "()" and "(())"..
                  raiseError(ERR_SEMANTIC);
                  break;
               default:
                  if ( verb ) log("unexpected value: " + pp(reg[regArg0]));
                  raiseError(ERR_INTERNAL);
                  break;
               }
               break;
            case TYPE_CHAR:
            case TYPE_FIXINT:
            case TYPE_SUBS:    // TODO: is this a valid decision?  Off-spec?
            case TYPE_SUBP:    // TODO: is this a valid decision?  Off-spec?
               // these types are self-evaluating
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            case TYPE_CELL:
               tmp0 = car(reg[regArg0]);
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
                  //
                  // TODO: w/ a different variant of
                  // sub_eval_look_env, could this be a tail call?
                  // And I don't just mean making a new function that
                  // calls sub_eval_look_env that has our same
                  // continuation sub_eval+0x1... I mean something
                  // that tail-recurses all the way to the
                  // success-or-symbol-not-found logic.
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
            default:
               if ( verb ) log("unexpected type: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            break;
         case sub_eval+0x1:
            // following symbol lookup
            if ( NIL == reg[regRetval] )
            {
               // symbol not found: unbound variable
               raiseError(ERR_SEMANTIC);
               break;
            }
            if ( DEBUG && TYPE_CELL != type(reg[regRetval]) )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            reg[regRetval] = cdr(reg[regRetval]);
            returnsub();
            break;
         case sub_eval+0x2:
            // following eval of the first elem
            //
            // If it's a function, evaluate the args next, following
            // up with apply.
            //
            // If it's a special form, don't evaluate the args,
            // just pass it off to apply.
            //
            reg[regTmp1] = restore();         // restore the env
            reg[regTmp0] = restore();         // restore the arg exprs
            reg[regTmp2] = reg[regRetval];    // value of the operator
            tmp0 = type(reg[regTmp2]);
            if ( TYPE_SUBP == tmp0 || 
                 TYPE_CELL == tmp0 && IS_PROCEDURE == car(reg[regTmp2]) )
            {
               // procedure: evaluate the args and then apply op to
               // args values
               // 
               store(reg[regTmp2]);           // store value of the operator
               reg[regArg0] = reg[regTmp0];
               reg[regArg1] = reg[regTmp1];
               gosub(sub_eval_list,sub_eval+0x3);
               break;
            }
            if ( TYPE_SUBS == tmp0 || 
                 TYPE_CELL == tmp0 && IS_SPECIAL_FORM == car(reg[regTmp2]) )
            {
               // special: apply op directly to args exprs
               //
               reg[regArg0] = reg[regTmp2];
               reg[regArg1] = reg[regTmp0];
               gosub(sub_apply,blk_tail_call);
               break;
            }
            // We get here, for instance, evaluating the expr (1).
            logrec("non-operator in sub_eval: ",tmp0);
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
            // NOTE: This almost sub_map e.g. (map eval list), except
            // except sub_eval is binary and sub_map works with unary
            // functions.
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
            logrec("sub_eval_look_frame SYM ",reg[regArg0]);
            if ( NIL == reg[regArg1] )
            {
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            store(reg[regArg0]);
            store(reg[regArg1]);
            reg[regArg1] = car(car(reg[regArg1]));
            logrec("sub_eval_look_frame CMP ",reg[regArg1]);
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
            //if ( verb ) log("arg0: " + pp(reg[regArg0]));
            //if ( verb ) log("arg1: " + pp(reg[regArg1]));
            if ( reg[regArg0] == reg[regArg1] )
            {
               //if ( verb ) log("identical");
               reg[regRetval] = TRUE;
               returnsub();
               break;
            }
            if ( type(reg[regArg0]) != type(reg[regArg1]) )
            {
               //if ( verb ) log("different types");
               reg[regRetval] = FALSE;
               returnsub();
               break;
            }
            if ( type(reg[regArg0]) != TYPE_CELL )
            {
               //if ( verb ) log("not cells");
               reg[regRetval] = FALSE;
               returnsub();
               break;
            }
            //if ( verb ) log("checking car");
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
               //if ( verb ) log("car mismatch");
               returnsub();
               break;
            }
            //if ( verb ) log("checking cdr");
            reg[regArg0] = cdr(reg[regArg0]);
            reg[regArg1] = cdr(reg[regArg1]);
            gosub(sub_equal_p,blk_tail_call);
            break;

         case sub_let:
            // Does a rewrite:
            //
            //   (define (rewrite expr)
            //     (let ((params (map car  (cadr expr)))
            //           (values (map cadr (cadr expr)))
            //           (body   (caddr expr)))
            //       (cons (list 'lambda params body) values)))
            //
            // or perhaps better reflecting what I will do with the
            // stack here:
            //
            //   (define (rewrite expr)
            //     (let ((locals (cadr expr))
            //           (body   (caddr expr)))
            //       (let ((params (map car locals)))
            //         (let ((values (map cadr locals)))
            //           (cons (list 'lambda params body) values)))))
            //
            // Of course, note that the 'let is already stripped from
            // the input by the time we get here, so this is really:
            //
            //   (define (rewrite expr)
            //     (let ((locals (car expr))
            //           (body   (cadr expr)))
            //       (let ((params (map car locals)))
            //         (let ((values (map cadr locals)))
            //           (cons (list 'lambda params body) values)))))
            //
            // Wow, but that is fiendishly tail-recursive!
            //
            // Not shown in this pseudo-code, is of course the
            // evaluation of that rewritten expression.
            //
            logrec("REWRITE INPUT:  ",reg[regArg0]);
            reg[regTmp0] = car(reg[regArg0]); // regTmp0 is locals
            reg[regTmp2] = cdr(reg[regArg0]);
            if ( TYPE_CELL != type(reg[regTmp2]) ) 
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regTmp1] = reg[regTmp2];      // regTmp1 is body
            logrec("REWRITE BODY A: ",reg[regTmp1]);
            store(reg[regTmp0]);
            store(reg[regTmp1]);
            reg[regArg0] = sub_car;
            reg[regArg1] = reg[regTmp0];
            gosub(sub_map,sub_let+0x1);
            break;
         case sub_let+0x1:
            // Note: Acknowledged, there is some wasteful stack manips
            // here, and a peek operation would be welcome, too.
            reg[regTmp2] = reg[regRetval];    // regTmp2 is params
            reg[regTmp1] = restore();         // restore body
            logrec("REWRITE BODY B: ",reg[regTmp1]);
            reg[regTmp0] = restore();         // restore locals
            store(reg[regTmp0]);
            store(reg[regTmp1]);
            store(reg[regTmp2]);
            reg[regArg0] = sub_cadr;
            reg[regArg1] = reg[regTmp0];
            gosub(sub_map,sub_let+0x2);
            break;
         case sub_let+0x2:
            reg[regTmp3] = reg[regRetval];    // regTmp3 is values
            reg[regTmp2] = restore();         // restore params
            reg[regTmp1] = restore();         // restore body
            logrec("REWRITE BODY C: ",reg[regTmp1]);
            reg[regTmp0] = restore();         // restore locals
            reg[regTmp4] = reg[regTmp1];
            reg[regTmp5] = cons( reg[regTmp2], reg[regTmp4] );
            reg[regTmp6] = cons( sub_lambda,   reg[regTmp5] );
            reg[regTmp7] = cons( reg[regTmp6], reg[regTmp3] );
            logrec("REWRITE OUTPUT: ",reg[regTmp7]);
            reg[regArg0] = reg[regTmp7];
            reg[regArg1] = reg[regEnv];
            gosub(sub_eval,blk_tail_call);
            break;

         case sub_map:
            // Applies the operator in reg[regArg0] to each element of
            // the list in reg[regArg1], and returns a list of the
            // results in order.
            //
            // The surplus cons() before we call sub_apply is perhaps
            // regrettable, but the rather than squeeze more
            // complexity into the sub_apply family, I choose here to
            // work around the variadicity checking and globbing in
            // sub_appy.
            //
            // I would prefer it if this sub_map worked with
            // either/both of builtins and user-defineds, and this way
            // it does.
            //
            if ( NIL == reg[regArg1] )
            {
               reg[regRetval] = NIL;
               returnsub();
               break;
            }
            reg[regTmp0] = car(reg[regArg1]); // head
            reg[regTmp1] = cdr(reg[regArg1]); // rest
            store(reg[regArg0]);
            store(reg[regTmp1]);
            reg[regArg0] = reg[regArg0];
            reg[regArg1] = cons(reg[regTmp0],NIL);
            gosub(sub_apply,sub_map+0x1);
            break;
         case sub_map+0x1:
            reg[regArg1] = restore();  // restore rest of operands
            reg[regArg0] = restore();  // restore operator
            store(reg[regRetval]);     // feed blk_tail_call_m_cons
            gosub(sub_map,blk_tail_call_m_cons);
            break;

         case sub_begin:
            // Evaluates all its args, returning the result of the
            // last.  If no args, returns UNSPECIFIED.
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = UNSPECIFIED;
               returnsub();
               break;
            }
            reg[regTmp0] = car(reg[regArg0]);
            reg[regTmp1] = cdr(reg[regArg0]);
            reg[regArg0] = reg[regTmp0];
            reg[regArg1] = reg[regEnv];
            reg[regTmp2] = UNSPECIFIED;
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
            // implicit (begin) statement.  If no args, returns
            // UNSPECIFIED.
            //
            // Where the body of a clause is empty, returns the value
            // of the test e.g.:
            //
            //   (cond (#f) (#t))   ==> 1
            //   (cond (3)  (#t 1)) ==> 3
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = UNSPECIFIED;
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
            // Returns UNSPECIFIED if no match is found.
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
            // I am vacillating about whether (case) belongs in the
            // microcode layer.  It is weird, sort of un-Schemey in
            // its semantics, and it requires the nontrivial "else"
            // syntactic support to be useful - but then again it
            // offers a lookup-table semantics that could potentially
            // be implemented in constant time in the number of
            // alternative paths. Neither (cond) nor an (if) chain
            // can offer this.
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
               reg[regRetval] = UNSPECIFIED;
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
            logrec("body:        ",reg[regTmp3]);
            if ( TYPE_CELL != type(reg[regTmp3]) )
            {
               // empty bodies are not cool in (case)
               raiseError(ERR_SEMANTIC);
               break;
            }
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
            reg[regArg0] = UNSPECIFIED;
            reg[regArg1] = UNSPECIFIED;
            reg[regArg2] = UNSPECIFIED;
            //
            // Note tricky dependency on reg order here.  At first
            // this creeped me out, but it works good, and I got
            // excited when I realized it was the first use of
            // register-index-as-variable, and reflected that
            // homoiconicity and "self-awareness" is part of the
            // strength of a LISP system, and I shouldn't let it tweak
            // me out at the lowest levels.
            //
            tmp1         = regArg0;
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
               reg[tmp1]    = car(reg[regTmp0]);
               reg[regTmp0] = cdr(reg[regTmp0]);
               log("pop arg: " + pp(reg[regArg0]));
               tmp1++;
               // fall through
            case A2:
               if ( NIL == reg[regTmp0] )
               {
                  log("too few args");
                  raiseError(ERR_SEMANTIC);
                  break;
               }
               reg[tmp1]    = car(reg[regTmp0]);
               reg[regTmp0] = cdr(reg[regTmp0]);
               log("pop arg: " + pp(reg[regArg0]));
               tmp1++;
            case A1:
               if ( NIL == reg[regTmp0] )
               {
                  log("too few args");
                  raiseError(ERR_SEMANTIC);
                  break;
               }
               reg[tmp1]    = car(reg[regTmp0]);
               reg[regTmp0] = cdr(reg[regTmp0]);
               log("pop arg: " + pp(reg[regArg0]));
               tmp1++;
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
            reg[regArg0] = car(cdr(reg[regArg0]));
            reg[regArg1] = reg[regArg1];
            gosub(sub_zip,sub_apply_user+0x1);
            break;
         case sub_apply_user+0x1:
            reg[regArg0] = restore();                        // restore op
            reg[regTmp0] = reg[regRetval];                   // args frame
            reg[regTmp1] = car(cdr(cdr(reg[regArg0])));      // op body
            reg[regTmp3] = car(cdr(cdr(cdr(reg[regArg0])))); // op lexical env
            reg[regTmp2] = cons(reg[regTmp0],reg[regEnv]);   // apply env
            logrec("sub_apply_user BODY   ",reg[regTmp1]);
            logrec("sub_apply_user FRAME  ",reg[regTmp0]);
            //logrec("sub_apply_user CUR ENV",reg[regEnv]);
            //logrec("sub_apply_user LEX ENV",reg[regTmp3]);
            if ( true ) 
            {
               // going w/ lexical frame
               reg[regTmp2] = cons(reg[regTmp0],reg[regTmp3]);
            }
            logrec("sub_apply_user ENV    ",reg[regTmp2]);
            reg[regArg0] = reg[regTmp1];
            reg[regArg1] = reg[regTmp2];
            //
            // At first glance, this env manip feels like it should
            // be the job of sub_eval. After all, sub_eval gets an
            // environment arg, and sub_apply does not.
            //
            // After deeper soul searching, this is not true.  We
            // certainly would not want (eval) to push/pop the env
            // on *every* call, but only sub_apply_user and sub_let
            // know what the new frames are, and only
            // sub_apply_user knows where to find the lexical scope
            // of a procedure or special form.
            //
            log("LEXICAL ENV PREPUSH:  " + pp(reg[regEnv]));
            store(reg[regEnv]);
            reg[regEnv] = reg[regTmp2];
            log("LEXICAL ENV POSTPUSH: " + pp(reg[regEnv]));
            gosub(sub_begin, sub_apply_user+0x2);
            break;
         case sub_apply_user+0x2:
            // I am so sad that pushing that env above means we cannot
            // be tail recursive.  At least this that is not true on
            // every sub_eval.
            //
            log("LEXICAL ENV PREPOP:  " + pp(reg[regEnv]));
            reg[regEnv] = restore();
            log("LEXICAL ENV POSTPOP: " + pp(reg[regEnv]));
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
            reg[regTmp0] = reg[regArg0];
            if ( verb ) log("printing: " + pp(reg[regTmp0]));
            switch (type(reg[regTmp0]))
            {
            case TYPE_SENTINEL:
               switch (reg[regTmp0])
               {
               case UNSPECIFIED:
                  reg[regRetval] = UNSPECIFIED;
                  returnsub();
                  break;
               case NIL:
                  gosub(sub_print_list,blk_tail_call);
                  break;
               case TRUE:
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'#'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'t'));
                  reg[regRetval] = UNSPECIFIED;
                  returnsub();
                  break;
               case FALSE:
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'#'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'f'));
                  reg[regRetval] = UNSPECIFIED;
                  returnsub();
                  break;
               default:
                  log("bogus sentinel: " + pp(reg[regTmp0]));
                  raiseError(ERR_INTERNAL);
                  break;
               }
               break;
            case TYPE_CELL:
               reg[regTmp1] = car(reg[regTmp0]);
               reg[regTmp2] = cdr(reg[regTmp0]);
               switch (reg[regTmp1])
               {
               case IS_STRING:
                  reg[regArg0] = reg[regTmp2];
                  gosub(sub_print_string,blk_tail_call);
                  break;
               case IS_SYMBOL:
                  reg[regArg0] = reg[regTmp2];
                  gosub(sub_print_chars,blk_tail_call);
                  break;
               case IS_PROCEDURE:
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
                  reg[regRetval] = UNSPECIFIED;
                  returnsub();
                  break;
               default:
                  reg[regArg0] = reg[regTmp0];
                  gosub(sub_print_list,blk_tail_call);
                  break;
               }
               break;
            case TYPE_CHAR:
               queuePushBack(reg[regOut],code(TYPE_CHAR,'#'));
               queuePushBack(reg[regOut],code(TYPE_CHAR,'\\'));
               switch (value(reg[regTmp0]))
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
                  queuePushBack(reg[regOut],reg[regTmp0]);
                  break;
               }
               reg[regRetval] = UNSPECIFIED;
               returnsub();
               break;
            case TYPE_FIXINT:
               // We trick out the sign extension of our 28-bit
               // twos-complement FIXINTs to match Java's 32 bits
               // before proceeding.
               reg[regTmp1] = 
                  (value(reg[regTmp0]) << (32-SHIFT_TYPE)) >> (32-SHIFT_TYPE);
               if ( reg[regTmp1] < 0 )
               {
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'-'));
                  reg[regTmp1] = -reg[regTmp1];
               }
               if ( reg[regTmp1] == 0 )
               {
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'0'));
                  returnsub();
                  break;
               }
               int factor = 1000000000; // big enough for 2**32 and less
               while ( factor > 0 && 0 == reg[regTmp1]/factor )
               {
                  factor /= 10;
               }
               while ( factor > 0 )
               {
                  final int digit  = reg[regTmp1]/factor;
                  reg[regTmp1]    -= digit * factor;                     
                  factor          /= 10;
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'0'+digit));
               }
               reg[regRetval] = UNSPECIFIED;
               returnsub();
               break;
            case TYPE_SUBP:
            case TYPE_SUBS:
               // TODO: some decisions to be made here about how these
               // really print, but that kind of depends on how I land
               // about how they lex.
               //
               // In the mean time, this is sufficient to meet spec.
               //
               queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
               queuePushBack(reg[regOut],code(TYPE_CHAR,'p'));
               queuePushBack(reg[regOut],code(TYPE_CHAR,'?'));
               reg[regRetval] = UNSPECIFIED;
               returnsub();
               break;
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
            reg[regRetval] = UNSPECIFIED;
            returnsub();
            break;

         case sub_print_chars:
            // Prints the list in reg[regArg0], whose elements are
            // expected to all be TYPE_CHAR, to reg[regOut].
            //
            if ( NIL == reg[regArg0] )
            {
               reg[regRetval] = UNSPECIFIED;
               returnsub();
               break;
            }
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               if ( verb ) log("bogus non-cell: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            reg[regTmp1] = car(reg[regArg0]);
            reg[regTmp2] = cdr(reg[regArg0]);
            if ( TYPE_CHAR != type(reg[regTmp1]) )
            {
               if ( verb ) log("bogus: " + pp(reg[regTmp1]));
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePushBack(reg[regOut],reg[regTmp1]);
            reg[regArg0] = reg[regTmp2];
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
            reg[regRetval] = UNSPECIFIED;
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
               reg[regRetval] = UNSPECIFIED;
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
            reg[regTmp0]     = value(reg[regArg0]);
            reg[regTmp1]     = value(reg[regArg1]);
            reg[regTmp0]   <<= (32-SHIFT_TYPE);
            reg[regTmp0]   >>= (32-SHIFT_TYPE);
            reg[regTmp1]   <<= (32-SHIFT_TYPE);
            reg[regTmp1]   >>= (32-SHIFT_TYPE);
            reg[regTmp3]     = 0;
            reg[regTmp3]    += reg[regTmp0];
            reg[regTmp3]    += reg[regTmp1];
            reg[regRetval]   = code(TYPE_FIXINT,reg[regTmp3]);
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
            reg[regTmp0]     = value(reg[regArg0]);
            reg[regTmp1]     = value(reg[regArg1]);
            reg[regTmp2]     = value(reg[regArg2]);
            reg[regTmp0]   <<= (32-SHIFT_TYPE);
            reg[regTmp0]   >>= (32-SHIFT_TYPE);
            reg[regTmp1]   <<= (32-SHIFT_TYPE);
            reg[regTmp1]   >>= (32-SHIFT_TYPE);
            reg[regTmp2]   <<= (32-SHIFT_TYPE);
            reg[regTmp2]   >>= (32-SHIFT_TYPE);
            reg[regTmp3]     = 0;
            reg[regTmp3]    += reg[regTmp0];
            reg[regTmp3]    += reg[regTmp1];
            reg[regTmp3]    += reg[regTmp2];
            reg[regRetval]   = code(TYPE_FIXINT,reg[regTmp3]);
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
            reg[regTmp0]     = value(reg[regArg0]);
            reg[regTmp1]     = value(reg[regArg1]);
            reg[regTmp0]   <<= (32-SHIFT_TYPE);
            reg[regTmp0]   >>= (32-SHIFT_TYPE);
            reg[regTmp1]   <<= (32-SHIFT_TYPE);
            reg[regTmp1]   >>= (32-SHIFT_TYPE);
            reg[regTmp3]     = 1;
            reg[regTmp3]    *= reg[regTmp0];
            reg[regTmp3]    *= reg[regTmp1];
            reg[regRetval]   = code(TYPE_FIXINT,reg[regTmp3]);
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
            reg[regTmp0]     = value(reg[regArg0]);
            reg[regTmp1]     = value(reg[regArg1]);
            reg[regTmp0]   <<= (32-SHIFT_TYPE);
            reg[regTmp0]   >>= (32-SHIFT_TYPE);
            reg[regTmp1]   <<= (32-SHIFT_TYPE);
            reg[regTmp1]   >>= (32-SHIFT_TYPE);
            reg[regTmp3]     = 0;
            reg[regTmp3]    += reg[regTmp0];
            reg[regTmp3]    -= reg[regTmp1];
            reg[regRetval]   = code(TYPE_FIXINT,reg[regTmp3]);
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
            reg[regTmp0]     = value(reg[regArg0]);
            reg[regTmp1]     = value(reg[regArg1]);
            reg[regTmp0]   <<= (32-SHIFT_TYPE);
            reg[regTmp0]   >>= (32-SHIFT_TYPE);
            reg[regTmp1]   <<= (32-SHIFT_TYPE);
            reg[regTmp1]   >>= (32-SHIFT_TYPE);
            reg[regRetval]   = (reg[regTmp0] < reg[regTmp1]) ? TRUE : FALSE;
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

         case sub_cadr:
            log("cadr: " + pp(reg[regArg0]));
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg[regRetval] = car(cdr(reg[regArg0]));
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
            // symbol arg which just defines a variable:
            //
            //   (define x 1)
            //
            // and the one with a list arg which is sugar:
            //
            //   (define (x) 1) equivalent to (define x (lambda () 1))
            //
            // sub_define is variadic.  The first form must have
            // exactly two args.  The second form must have at least
            // two args, which form the body of the method being
            // defined within an implicit (begin) block.
            //
            logrec("args: ",reg[regArg0]);
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);  // variadic with at least 2 args
               break;
            }
            reg[regTmp0] = car(reg[regArg0]);
            reg[regTmp1] = cdr(reg[regArg0]);
            logrec("head: ",reg[regTmp0]);
            logrec("rest: ",reg[regTmp1]);
            if ( TYPE_CELL != type(reg[regTmp0]) )
            {
               raiseError(ERR_SEMANTIC);  // first is symbol or arg list
               break;
            }
            if ( TYPE_CELL != type(reg[regTmp1]) )
            {
               raiseError(ERR_SEMANTIC);  // variadic with at least 2 args
               break;
            }
            if ( IS_SYMBOL == car(reg[regTmp0]) )
            {
               if ( NIL != cdr(reg[regTmp1]) )
               {
                  raiseError(ERR_SEMANTIC); // simple form takes exactly 2 args
                  break;
               }
               // reg[regTmp0] is already the symbol, how we want it
               // for this case.
               reg[regTmp1] = car(reg[regTmp1]);
            }
            else
            {
               // Note: by leveraging sub_lambda in this way, putting
               // the literal value sub_lambda at the head of a
               // constructed expression which is en route to
               // sub_eval, we force the hand on other design
               // decisions about the direct (eval)ability and
               // (apply)ability of sub_foo in general.
               //
               // We do the same in sub_read_atom with sub_quote, so
               // clearly I'm getting comfortable with this decision.
               //
               reg[regTmp2] = car(reg[regTmp0]); // actual symbol
               reg[regTmp3] = cdr(reg[regTmp0]); // actual arg list
               reg[regTmp0] = reg[regTmp2];      // regTmp0 good, regTmp2 free
               logrec("proc symbol: ",reg[regTmp0]);
               logrec("proc args:   ",reg[regTmp3]);
               logrec("proc body:   ",reg[regTmp1]);
               reg[regTmp2] = cons(reg[regTmp3],reg[regTmp1]);
               logrec("partial:     ",reg[regTmp2]);
               reg[regTmp1] = cons(sub_lambda,reg[regTmp2]);
               logrec("proc lambda: ",reg[regTmp1]);
            }
            // By here, reg[regTmp0] should be the the symbol,
            // reg[regTmp1] the expr whose value we will bind to the
            // symbol.
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
            reg[regRetval] = UNSPECIFIED;
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
            // sub_lambda is variadic and must have at least two args.
            // The first must be a list, possibly empty.  The
            // remaining args are the body of the new procedure, in an
            // implicit (begin) block.
            //
            logrec("lambda args: ",reg[regArg0]);
            if ( TYPE_CELL != type(reg[regArg0]) )
            {
               raiseError(ERR_SEMANTIC);  // must have at least 2 args
               break;
            }
            reg[regTmp0] = car(reg[regArg0]);
            logrec("proc args:   ",reg[regTmp0]);
            if ( NIL != reg[regTmp0] && TYPE_CELL != type(reg[regTmp0])  )
            {
               raiseError(ERR_SEMANTIC);  // must have at least 2 args
               break;
            }
            reg[regTmp1] = cdr(reg[regArg0]);
            logrec("proc body:   ",reg[regTmp1]);
            if ( TYPE_CELL != type(reg[regTmp1]) )
            {
               raiseError(ERR_SEMANTIC);  // must have at least 2 args
               break;
            }
            reg[regRetval] = cons(reg[regEnv], NIL);
            reg[regRetval] = cons(reg[regTmp1],reg[regRetval]);
            reg[regRetval] = cons(reg[regTmp0],reg[regRetval]);
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

   private static final int TYPE_FIXINT   = 0x20000000;
   private static final int TYPE_CELL     = 0x30000000;
   private static final int TYPE_CHAR     = 0x40000000;
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
   // Also, I'm using randomish values for the differentiators among
   // TYPE_SENTINEL.
   //
   // Since each of these has only a finite, definite number of valid
   // values, using junk there is a good error-detection mechanism
   // which validates that my checks are precise and I'm not doing any
   // silly arithmetic or confusing, say, NIL with the Java value 0.
   //
   // Even though one can easily imagine lower-level implementations
   // where it would be more efficient to use 0, 1, 2, etc. I choose
   // not to to encourage this code to fail hard and fast.

   // TODO: distinct from EOF, do we need an NO_INPUT to indicate when
   // no input is ready, but the port isn't closed?
   //
   // Should explore ports and how we do input() and output() here,
   // get down to a lower level.

   private static final int NIL                 = TYPE_SENTINEL | 39;

   private static final int EOF                 = TYPE_SENTINEL | 97;
   private static final int UNSPECIFIED         = TYPE_SENTINEL | 65;
   private static final int IS_SYMBOL           = TYPE_SENTINEL | 79;
   private static final int IS_STRING           = TYPE_SENTINEL | 32;
   private static final int IS_PROCEDURE        = TYPE_SENTINEL | 83;
   private static final int IS_SPECIAL_FORM     = TYPE_SENTINEL | 54;

   private static final int TRUE                = TYPE_SENTINEL | 37;
   private static final int FALSE               = TYPE_SENTINEL | 91;

   private static final int ERR_OOM             = TYPE_SENTINEL | 42;
   private static final int ERR_INTERNAL        = TYPE_SENTINEL | 18;
   private static final int ERR_LEXICAL         = TYPE_SENTINEL | 11;
   private static final int ERR_SEMANTIC        = TYPE_SENTINEL |  7;
   private static final int ERR_NOT_IMPL        = TYPE_SENTINEL | 87;

   private static final int regFreeCellList     =   0; // unused cells

   private static final int regStack            =   1; // the runtime stack
   private static final int regPc               =   2; // opcode to return to

   private static final int regError            =   3; // NIL or an ERR_foo
   private static final int regErrorPc          =   4; // reg[regPc] of err
   private static final int regErrorStack       =   5; // reg[regStack] of err

   private static final int regEnv              =   6; // list of env frames

   private static final int regIn               =   7; // input char queue
   private static final int regOut              =   8; // output char queue

   private static final int regRetval           =   9; // return value

   private static final int regArg0             =  10; // argument
   private static final int regArg1             =  11; // argument
   private static final int regArg2             =  12; // argument

   private static final int regTmp0             =  20; // temporary
   private static final int regTmp1             =  21; // temporary
   private static final int regTmp2             =  22; // temporary
   private static final int regTmp3             =  23; // temporary
   private static final int regTmp4             =  24; // temporary
   private static final int regTmp5             =  25; // temporary
   private static final int regTmp6             =  26; // temporary
   private static final int regTmp7             =  27; // temporary
   private static final int regTmp8             =  28; // temporary
   private static final int regTmp9             =  29; // temporary

   private static final int numRegisters        =  32;          // in slots
   private static final int heapSize            =   3 * 1024;   // in cells

   //  16 kcells:  0.5 sec
   //  32 kcells:  0.6 sec
   //  64 kcells:  1.0 sec
   // 128 kcells:  4.2 sec  *** big nonlinearity up
   // 256 kcells: 10.6 sec  *** small nonlinearity up
   // 512 kcells: 11.5 sec  *** small nonlinearity down

   private final int[] reg     = new int[numRegisters];
   private final int[] heap    = new int[2*heapSize];
   private int         heapTop        = 0; // in slots

   public        int numCycles           = 0; // PROFILE only
   public        int numCons             = 0; // PROFILE only
   public        int maxHeapTop          = 0; // PROFILE only
   public        int numInput            = 0; // PROFILE only
   public        int numOutput           = 0; // PROFILE only
   public static int universalNumCycles  = 0; // PROFILE only
   public static int universalNumCons    = 0; // PROFILE only
   public static int universalMaxHeapTop = 0; // PROFILE only
   public static int universalNumInput   = 0; // PROFILE only
   public static int universalNumOutput  = 0; // PROFILE only

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
   private static final int sub_let              = TYPE_SUBS | AX |  0x6200;
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
   private static final int sub_car              = TYPE_SUBP | A1 |  0x7210;
   private static final int sub_cdr              = TYPE_SUBP | A1 |  0x7220;
   private static final int sub_cadr             = TYPE_SUBP | A1 |  0x7230;
   private static final int sub_list             = TYPE_SUBP | AX |  0x7240;

   private static final int sub_if               = TYPE_SUBS | A3 |  0x7600;
   private static final int sub_quote            = TYPE_SUBS | A1 |  0x7700;
   private static final int sub_define           = TYPE_SUBS | AX |  0x7800;
   private static final int sub_lambda           = TYPE_SUBS | AX |  0x7900;

   private static final int sub_map              = TYPE_SUBP | A2 |  0x8000;

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
         return UNSPECIFIED;
      }
      if ( DEBUG && NIL == reg[regStack] )
      {
         if ( verb ) log("restore(): stack underflow");
         raiseError(ERR_INTERNAL);
         return UNSPECIFIED;
      }
      if ( DEBUG && TYPE_CELL != type(reg[regStack]) )
      {
         if ( verb ) log("restore(): corrupt stack");
         raiseError(ERR_INTERNAL);
         return UNSPECIFIED;
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
            // TODO: protect against corrupt cyclic stack
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
    */
   private int cons ( final int car, final int cdr )
   {
      if ( PROFILE )
      {
         numCons++;
         universalNumCons++;
      }
      int cell = reg[regFreeCellList];
      if ( NIL == cell )
      {
         if ( heapTop >= heap.length )
         {
            raiseError(ERR_OOM);
            return UNSPECIFIED;
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
            // Notice that how half the space in the free cell list,
            // the car()s, is unused.  Is this an opportunity for
            // something?
            heap[heapTop+0]      = UNSPECIFIED;
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
            if ( heapTop > universalMaxHeapTop )
            {
               universalMaxHeapTop = heapTop;
            }
         }
      }
      final int t          = type(cell);
      if ( DEBUG && TYPE_CELL != t )
      {
         raiseError(ERR_INTERNAL);
         return UNSPECIFIED;
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
         return UNSPECIFIED;
      }
      return heap[(value(cell) << 1) + 0];
   }
   private int cdr ( final int cell )
   {
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return UNSPECIFIED;
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

   private int queueCreate ()
   {
      final int queue = cons(NIL,NIL);
      if ( false ) log("  queueCreate(): returning " + pp(queue));
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
         case IS_PROCEDURE:
         case IS_SPECIAL_FORM:
            log(tag + pp(first));
            tag += " ";
            logrec(tag,car(cdr(c))); // just show the arg list, env might cycle
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
      case UNSPECIFIED:          return "UNSPECIFIED";
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
      case TYPE_FIXINT:   buf.append("fixint");   break;
      case TYPE_CELL:     buf.append("cell");     break;
      case TYPE_CHAR:     buf.append("char");     break;
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
         case sub_cadr:             buf.append("sub_cadr");             break;
         case sub_list:             buf.append("sub_list");             break;
         case sub_if:               buf.append("sub_if");               break;
         case sub_quote:            buf.append("sub_quote");            break;
         case sub_define:           buf.append("sub_define");           break;
         case sub_lambda:           buf.append("sub_lambda");           break;
         case sub_map:              buf.append("sub_map");              break;
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
