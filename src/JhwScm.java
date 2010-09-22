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
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class JhwScm implements Firmware
{
   public static final boolean DEFER_HEAP_INIT           = true;
   public static final boolean PROPERLY_TAIL_RECURSIVE   = true;
   public static final boolean CLEVER_TAIL_CALL_MOD_CONS = true;
   public static final boolean CLEVER_STACK_RECYCLING    = true;

   public static class Stats
   {
      public int numCons = 0;
   }

   public static final Stats global = new Stats();
   public        final Stats local  = new Stats();

   private final boolean DO_EVAL;
   private final boolean PROFILE;
   private final boolean VERBOSE;
   private final boolean DEBUG;  // check things which should never happen

   public Machine mach = null; // TODO: hacky non-state member should be param

   private int javaDepth = 0; // debug logging only
   private int scmDepth  = 0; // debug logging only, icky b/c lasts over calls


   ////////////////////////////////////////////////////////////////////
   //
   // client control points
   //
   ////////////////////////////////////////////////////////////////////

   public JhwScm ( final boolean DO_EVAL, 
                   final boolean PROFILE, 
                   final boolean VERBOSE, 
                   final boolean DEBUG )
   {
      this.DO_EVAL = DO_EVAL;
      this.PROFILE = PROFILE;
      this.VERBOSE = VERBOSE;
      this.DEBUG   = DEBUG;
   }

   /**
    * Called before step(), not allowed to fail.
    *
    * The firmware should initialize the Machine to a base state.
    */
   public void boot ( final Machine mach )
   {
      if ( DEBUG ) javaDepth = 0;
      log("boot: ");
      if ( DEBUG ) javaDepth = 1;

      final Mem reg = mach.reg;
      reg.set(regPc,sub_init);
   }

   /**
    * Drives a single step of computation.  
    * 
    * @returns ERROR_COMPLETE, ERROR_INCOMPLETE, or one of the
    * ERROR_foo codes defined here.
    */
   public int step ( final Machine mach )
   {
      this.mach = mach; // TODO: hacky non-state member should be param

      final Mem reg = mach.reg;

      if ( DEBUG ) javaDepth = 0;
      log("step: ",pp(reg.get(regPc)));
      if ( DEBUG ) javaDepth = 1;

      // Temp variables: note, any block can overwrite any of these.
      // Any data which must survive a block transition should be
      // saved in registers and on the stack instead.
      //
      // TODO: render these as registers!
      //
      int tmp0 = 0;
      int tmp1 = 0;

      switch ( reg.get(regPc) )
      {
      case sub_init:
         // Initializes the machine and the environment, then
         // transfers to the top-level loop, sub_top.
         //
         for ( int i = 0; i < reg.length(); i++ )
         {
            reg.set(i,UNSPECIFIED);
         }
         reg.set(regStack,     NIL);
         reg.set(regError,     NIL);
         reg.set(regFreeCells, NIL);
         reg.set(regHeapTop,   code(TYPE_FIXINT,0));
         reg.set(regIn,        code(TYPE_IOBUF,0));
         reg.set(regOut,       code(TYPE_IOBUF,1));
         reg.set(regEnv,       cons(NIL,NIL));
         reg.set(regArg0,      code(TYPE_FIXINT,primitives_start));
         gosub(sub_prebind, sub_init+0x1);
         break;
      case sub_init+0x1:
         gosub(sub_top, blk_halt);
         break;
         
      case sub_prebind:
         // Initializes the primitivemost environment. 
         //
         // Expects regArg0 to be a fixint specifying an entry in the
         // const table.
         // 
         // Return value is UNSPECIFIED.
         // 
         tmp0 = value_fixint(reg.get(regArg0));
         if ( tmp0 >= primitives_end ) 
         {
            reg.set(regRetval,UNSPECIFIED);
            returnsub();
            break;
         }
         store(regArg0);     // store const id
         gosub(sub_const_symbol,sub_prebind+0x1);
         break;
      case sub_prebind+0x1:
         restore(regArg0);   // restore const id
         store(regArg0);     // store const id INEFFICIENT
         store(regRetval);   // store symbol
         gosub(sub_const_val,sub_prebind+0x2);
         break;
      case sub_prebind+0x2:
         restore(regTmp0);   // restore symbol
         restore(regArg0);   // restore const id INEFFICIENT
         {
            // TODO: get rid of vars!
            final int bind  = cons(reg.get(regTmp0),reg.get(regRetval));
            final int frame = car(reg.get(regEnv));
            final int newfr = cons(bind,frame);
            setcar(reg.get(regEnv),newfr);
         }
         tmp0 = value_fixint(reg.get(regArg0));
         tmp1 = tmp0 + 1;
         reg.set(regArg0,code(TYPE_FIXINT,tmp1));
         gosub(sub_prebind,blk_tail_call);
         break;

      case sub_const_symbol:
         // Returns a symbol corresponding to the const_str[] at offset
         // regArg0, with regArg0 expected to be a fixint.
         // 
         // Because of how the const table is implemented, we cheat
         // slightly on the "no-Java" rule.
         //
         tmp0 = value_fixint(reg.get(regArg0)); // const offset
         if ( DEBUG && (tmp0 < 0 || tmp0 >= primitives_end ))
         {
            log("bad tmp0: " + tmp0 + " of " + primitives_end);
            raiseError(ERR_INTERNAL);
            break;
         }
         reg.set(regArg1,code(TYPE_FIXINT,0));
         reg.set(regTmp0,IS_SYMBOL);
         store(regTmp0);
         gosub(sub_const_chars,blk_tail_call_m_cons);
         break;

      case sub_const_chars:
         // Returns a list of the characters from const_str
         // corresponding to:
         //
         //   const_str[reg.get(regArg0)][reg.get(regArg1) ... ]
         // 
         // ...to the end of the const. 
         //
         tmp0 = value_fixint(reg.get(regArg0)); // const offset
         tmp1 = value_fixint(reg.get(regArg1)); // string offset
         if ( DEBUG && (tmp0 < 0 || tmp0 >= primitives_end ))
         {
            log("bad tmp0: " + tmp0 + " of " + primitives_end);
            raiseError(ERR_INTERNAL);
            break;
         }
         if ( tmp1 >= const_str[tmp0].length() )
         {
            reg.set(regRetval,NIL);
            returnsub();
            break;
         }
         if ( DEBUG && tmp1 < 0 )
         {
            log("bad tmp1: " + tmp1 + " of " + const_str[tmp0].length());
            raiseError(ERR_INTERNAL);
            break;
         }
         tmp0 = code(TYPE_CHAR,const_str[tmp0].charAt(tmp1));
         tmp1 = tmp1 + 1;
         reg.set(regArg1,tmp1);
         reg.set(regTmp0,tmp0);
         store(regTmp0);
         gosub(sub_const_chars,blk_tail_call_m_cons);
         break;

      case sub_const_val:
         // Returns the const_val[] at offset regArg0, with regArg0
         // expected to be a fixint.
         // 
         tmp0 = value_fixint(reg.get(regArg0));
         if ( DEBUG && (tmp0 < 0 || tmp0 >= primitives_end ))
         {
            log("bad tmp0: " + tmp0 + " of " + primitives_end);
            raiseError(ERR_INTERNAL);
            break;
         }
         reg.set(regRetval,const_val[tmp0]);
         returnsub();
         break;

      case sub_top:
         // The top-level loop: read-eval-print if DO_EVAL, read-print
         // otherwise.
         //
         // Reads the next expr the port at regIn.  If DO_EVAL,
         // evaluates the expression in the global environment.
         // Prints the result in the port at regOut.
         //
         // Returns UNSPECIFIED after processing everything on the
         // input port.
         // 
         // (define (sub_top)
         //   (let ((expr (sub_read regIn)))
         //     (if (= EOF expr)
         //         UNSPECIFIED
         //         (begin
         //           (if DO_EVAL
         //               (sub_print (sub_eval expr (global_env)) regOut)
         //               (sub_print expr                         regOut))
         //           (sub_top)))))
         //
         reg.set(regArg0,NIL);
         gosub(sub_readv,sub_top+0x1);
         break;
      case sub_top+0x1:
         if ( EOF == reg.get(regRetval) )
         {
            reg.set(regRetval,  UNSPECIFIED);
            returnsub();
            break;
         }
         if ( DO_EVAL )
         {
            reg.set(regArg0,  reg.get(regRetval));
            reg.set(regArg1,  reg.get(regEnv));
            gosub(sub_eval,sub_top+0x2);
         }         
         else
         {
            reg.set(regArg0, reg.get(regRetval));
            reg.set(regArg1, reg.get(regOut));
            gosub(sub_print,sub_top+0x3);
         }
         break;
      case sub_top+0x2:
         reg.set(regArg0, reg.get(regRetval));
         reg.set(regArg1, reg.get(regOut));
         gosub(sub_print,sub_top+0x3);
         break;
      case sub_top+0x3:
         gosub(sub_top,blk_tail_call);
         break;

      case sub_readv:
         // Parses the next expr from an input port, and leaves the
         // results in regRetval.
         //
         // Is variadic, 0 or 1 argument: if the first arg is present,
         // it is expected to be an input port.  Otherwise regIn is
         // used.
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
         // (define (sub_readv)      (sub_read regIn))
         // (define (sub_readv port) (sub_read port))
         //
         // TODO: get more ports, and test that this mess works with
         // non-regIn input ports!
         //
         if ( NIL == reg.get(regArg0) )
         {
            log("default arg: ",pp(reg.get(regArg0)));
            reg.set(regArg0, reg.get(regIn));
         }
         else if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            log("bogus arg: ",pp(reg.get(regArg0)));
            raiseError(ERR_SEMANTIC);
            break;
         }
         else
         {
            reg.set(regTmp0, car(reg.get(regArg0)));
            reg.set(regTmp1, cdr(reg.get(regArg0)));
            if ( NIL != reg.get(regTmp1) )
            {
               // too many args
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg.set(regArg0, reg.get(regTmp0));
         }
         gosub(sub_read,blk_tail_call);
         break;

      case sub_read:
         // Parses the next expr from regArg0, and leaves the results
         // in regRetval.
         //
         // (define (sub_read port)
         //   (begin (sub_read_burn_space port)
         //          (let ((c (port_peek port)))
         //            (case c
         //              (( EOF ) EOF)
         //              (( #\) ) (err_lexical))
         //              (( #\( ) (sub_read_list port))
         //              (else    (sub_read_atom port))))))
         //
         if ( TYPE_IOBUF != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         store(regArg0);        // store port
         gosub(sub_read_burn_space,sub_read+0x1);
         break;
      case sub_read+0x1:
         restore(regArg0);    // restore port
         reg.set(regTmp0, portPeek(reg.get(regArg0)));
         if ( EOF == reg.get(regTmp0) )
         {
            reg.set(regRetval,  EOF);
            returnsub();
            break;
         }
         if ( DEBUG && TYPE_CHAR != type(reg.get(regTmp0)) )
         {
            log("non-char in input: ",pp(reg.get(regTmp0)));
            raiseError(ERR_INTERNAL);
            break;
         }
         switch (value(reg.get(regTmp0)))
         {
         case ')':
            log("mismatch close paren");
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
         // Reads the next list expr from the port at regArg0,
         // returning the result in regRetval.
         //
         // Also handles dotted lists.
         // 
         // On entry, expects the next char on the port to be
         // the opening '(' a list expression.
         // 
         // On exit, precisely the list expression will have been
         // consumed from the port, up to and including the final ')'.
         //
         // (define (sub_read_list port)
         //   (if (!= #\( (port_peek port))
         //       (err_lexical "expected open paren")
         //       (begin (port_pop port)
         //              (sub_read_list_open port))))
         //
         if ( code(TYPE_CHAR,'(') != portPeek(reg.get(regArg0)) )
         {
            raiseError(ERR_LEXICAL);
            break;
         }
         portPop(reg.get(regArg0));
         reg.set(regArg0, reg.get(regArg0));
         gosub(sub_read_list_open,blk_tail_call);
         break;

      case sub_read_list_open:
         // Reads all exprs from the port at regArg0 until a loose
         // EOF, a ')', or a '.' is encountered.
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
         // consumed from the port at reg.get(regArg0), up to and
         // including the final ')'.
         //  
         // (define (sub_read_list_open port)
         //   (burn-space port)
         //   (case (port_peek port)
         //     ((eof) (error_lexical "eof in list expr"))
         //     ((#\)  (begin (port_peek port) '()))
         //     (else
         //       (let ((next (sub_read port))
         //             (rest (sub_read_list_open port))) ; token lookahead?!
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
         store(regArg0);         // store port
         gosub(sub_read_burn_space,sub_read_list_open+0x1);
         break;
      case sub_read_list_open+0x1:    
         restore(regArg0);    // restore port
         reg.set(regTmp0, portPeek(reg.get(regArg0)));
         if ( EOF == reg.get(regTmp0) )
         {
            log("eof in list expr");
            raiseError(ERR_LEXICAL);
            break;
         }
         if ( code(TYPE_CHAR,')') == reg.get(regTmp0) )
         {
            log("matching close-paren");
            portPop(reg.get(regArg0));
            reg.set(regRetval, NIL);
            returnsub();
            break;
         }
         store(regArg0);         // store port
         gosub(sub_read,sub_read_list_open+0x2);
         break;
      case sub_read_list_open+0x2:
         restore(regArg0);       // restore port
         store(regArg0);         // store port INEFFICIENT
         store(regRetval);       // store next
         gosub(sub_read_list_open,sub_read_list_open+0x3);
         break;
      case sub_read_list_open+0x3:
         restore(regTmp0);       // restore next
         restore(regArg0);       // restore port
         reg.set(regTmp1, reg.get(regRetval)); // rest
         if ( TYPE_CELL           != type(reg.get(regTmp0))     ||
              IS_SYMBOL           != car(reg.get(regTmp0))      ||
              code(TYPE_CHAR,'.') != car(cdr(reg.get(regTmp0))) ||
              NIL                 != cdr(cdr(reg.get(regTmp0)))  )
         {
            reg.set(regRetval, cons(reg.get(regTmp0),reg.get(regTmp1)));
            returnsub();
            break;
         }
         if ( NIL == reg.get(regTmp1) )
         {
            log("dangling dot");
            raiseError(ERR_LEXICAL);
            break;
         }
         if ( NIL == cdr(reg.get(regTmp1)) )
         {
            log("happy dotted list");
            reg.set(regRetval, car(reg.get(regTmp1)));
            returnsub();
            break;
         }
         log("many after dot");
         raiseError(ERR_LEXICAL);
         break;

      case sub_read_atom:
         // Reads the next atomic expr from the port at regArg0,
         // returning the result in regRetval.
         // 
         // On entry, expects the next char from the port to be the
         // initial character of an atomic expression.
         // 
         // On exit, precisely the atomic expression will have been
         // consumed from the port.
         //
         // (define (sub_read_atom port)
         //   (let ((c (port_peek port)))
         //     (case c
         //       (( #\' ) (err_not_impl))
         //       (( #\" ) (sub_read_string port))
         //       (( #\# ) (sub_read_octo_tok port))
         //       (( #\0 #\1 #\2 #\3 #\4 #\5 #\6 #\7 #\8 #\9 ) 
         //        (sub_read_num port))
         //       (( #\- ) (begin 
         //                (port_pop port)
         //                (let ((c (port_peek port)))
         //                  (case c
         //                    (( #\0 #\1 #\2 #\3 #\4 #\5 #\6 #\7 #\8 #\9 )
         //                     (- (sub_read_num port)))
         //                    (else 
         //                     (prepend #\- 
         //                       (sub_read_symbol_body port))))))))))
         //
         reg.set(regTmp0,  portPeek(reg.get(regArg0)));
         if ( DEBUG && TYPE_CHAR != type(reg.get(regTmp0)) )
         {
            log("non-char in input: ",pp(reg.get(regTmp0)));
            raiseError(ERR_INTERNAL);
            break;
         }
         switch (value(reg.get(regTmp0)))
         {
         case '\'':
            log("quote (not belong here in sub_read_atom?)");
            portPop(reg.get(regArg0));
            gosub(sub_read,sub_read_atom+0x3);
            break;
         case '"':
            log("string literal");
            gosub(sub_read_string,blk_tail_call);
            break;
         case '#':
            log("octothorpe special");
            gosub(sub_read_octo_tok,blk_tail_call);
            break;
         case '0': case '1': case '2': case '3': case '4':
         case '5': case '6': case '7': case '8': case '9':
            log("non-negated number");
            gosub(sub_read_num,blk_tail_call);
            break;
         case '-':
            // The minus sign is special.  We need to look ahead
            // *again* before we can decide whether it is part of a
            // symbol or part of a number.
            portPop(reg.get(regArg0));
            reg.set(regTmp2,  portPeek(reg.get(regArg0)));
            if ( TYPE_CHAR == type(reg.get(regTmp2)) && 
                 '0' <= value(reg.get(regTmp2))      && 
                 '9' >= value(reg.get(regTmp2))       )
            {
               log("minus-starting-number");
               gosub(sub_read_num,sub_read_atom+0x1);
            }
            else if ( EOF == reg.get(regTmp2) )
            {
               log("lonliest minus in the world");
               reg.set(regTmp0,    cons(code(TYPE_CHAR,'-'),NIL));
               reg.set(regRetval,  cons(IS_SYMBOL,reg.get(regTmp0)));
               returnsub();
            }
            else
            {
               log("minus-starting-symbol");
               reg.set(regArg1, code(TYPE_CHAR,'-'));
               gosub(sub_read_symbol,blk_tail_call);
            }
            break;
         default:
            log("symbol");
            reg.set(regArg1, NIL);
            gosub(sub_read_symbol,blk_tail_call);
            break;
         }
         break;
      case sub_read_atom+0x1:
         if ( TYPE_FIXINT != type(reg.get(regRetval)) )
         {
            raiseError(ERR_INTERNAL);
            break;
         }
         log("negating: ",pp(reg.get(regRetval)));
         reg.set(regRetval,  code(TYPE_FIXINT,-value(reg.get(regRetval))));
         log("  to:       ",pp(reg.get(regRetval)));
         returnsub();
         break;
      case sub_read_atom+0x2:
         restore(regTmp0);
         reg.set(regTmp1,    car(reg.get(regTmp0)));
         reg.set(regRetval,  cons(IS_SYMBOL,reg.get(regTmp1)));
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
         reg.set(regTmp0,    cons(reg.get(regRetval),NIL));
         reg.set(regRetval,  cons(sub_quote,reg.get(regTmp0)));
         returnsub();
         break;

      case sub_read_num:
         // Parses the next number from the port at regArg0.
         //
         reg.set(regArg1,  code(TYPE_FIXINT,0));
         gosub(sub_read_num_loop,blk_tail_call);
         break;
      case sub_read_num_loop:
         // Parses the next number from regArg0, expecting the
         // accumulated value-so-far as a TYPE_FIXINT in regArg1.
         //
         // A helper for sub_read_num, but still a sub_ in its own
         // right.
         //
         reg.set(regTmp1,  portPeek(reg.get(regArg0)));
         if ( EOF == reg.get(regTmp1) )
         {
            log("eof: returning ",pp(reg.get(regArg1)));
            reg.set(regRetval,  reg.get(regArg1));
            returnsub();
            break;
         }
         if ( TYPE_CHAR != type(reg.get(regTmp1)) )
         {
            log("non-char in input: ",pp(reg.get(regTmp1)));
            raiseError(ERR_INTERNAL);
            break;
         }
         reg.set(regTmp2,  reg.get(regArg1));
         if ( TYPE_FIXINT != type(reg.get(regTmp2)) )
         {
            log("non-fixint in arg: ",pp(reg.get(regTmp2)));
            raiseError(ERR_LEXICAL);
            break;
         }
         switch (value(reg.get(regTmp1)))
         {
         case ' ':
         case '\t':
         case '\r':
         case '\n':
         case '(':
         case ')':
            // terminator
            reg.set(regRetval,  reg.get(regArg1));
            returnsub();
            break;
         default:
            if ( value(reg.get(regTmp1)) < '0' || 
                 value(reg.get(regTmp1)) > '9' )
            {
               log("non-digit in input: ",pp(reg.get(regTmp1)));
               raiseError(ERR_LEXICAL);
               break;
            }
            tmp0  = 10 * value(reg.get(regTmp2));
            tmp0 += value(reg.get(regTmp1));
            tmp0 -= '0';
            log("first char: ",(char)value(reg.get(regTmp1)));
            log("old accum:  ",value(reg.get(regTmp2)));
            log("new accum:  ",tmp0);
            portPop(reg.get(regArg0));
            reg.set(regArg1,  code(TYPE_FIXINT,tmp0));
            gosub(sub_read_num_loop,blk_tail_call);
            break;
         }
         break;

      case sub_read_octo_tok:
         // Parses the next octothorpe literal regArg0.
         //
         reg.set(regTmp0,  portPeek(reg.get(regArg0)));
         if ( reg.get(regTmp0) != code(TYPE_CHAR,'#') )
         {
            raiseError(ERR_INTERNAL);
            break;
         }
         portPop(reg.get(regArg0));
         reg.set(regTmp1,  portPeek(reg.get(regArg0)));
         if ( EOF == reg.get(regTmp1) )
         {
            log("eof after octothorpe");
            raiseError(ERR_LEXICAL);
            break;
         }
         if ( DEBUG && TYPE_CHAR != type(reg.get(regTmp1)) )
         {
            log("non-char in input: ",pp(reg.get(regTmp1)));
            raiseError(ERR_INTERNAL);
            break;
         }
         portPop(reg.get(regArg0));
         switch (value(reg.get(regTmp1)))
         {
         case 't':
            log("true");
            reg.set(regRetval,  TRUE);
            returnsub();
            break;
         case 'f':
            log("false");
            reg.set(regRetval,  FALSE);
            returnsub();
            break;
         case '\\':
            reg.set(regTmp2,  portPeek(reg.get(regArg0)));
            if ( EOF == reg.get(regTmp2) )
            {
               log("eof after octothorpe slash");
               raiseError(ERR_LEXICAL);
               break;
            }
            if ( DEBUG && TYPE_CHAR != type(reg.get(regTmp2)) )
            {
               log("non-char in input: ",pp(reg.get(regTmp2)));
               raiseError(ERR_INTERNAL);
               break;
            }
            log("character literal: ",pp(reg.get(regTmp2)));
            portPop(reg.get(regArg0));
            reg.set(regRetval,  reg.get(regTmp2));
            returnsub();
            // TODO: so far, we only handle the 1-char sequences...
            break;
         default:
            log("unexpected after octothorpe: ",pp(reg.get(regTmp1)));
            raiseError(ERR_LEXICAL);
            break;
         }
         break;

      case sub_read_symbol:
         // Parses the next symbol from regArg0.
         //
         // Expects that the next character in the input is known
         // to be the first character of a symbol.
         //
         // regArg1 is expected to be a 'prepend' character.  If
         // non-NIL, regArg1 is prepended to the symbol.
         //
         if ( NIL == reg.get(regArg1) )
         {
            reg.set(regTmp0,IS_SYMBOL);
            store(regTmp0);
            gosub(sub_read_symbol_body,blk_tail_call_m_cons);
         }
         else
         {
            store(regArg1);
            gosub(sub_read_symbol_body,sub_read_symbol+0x1);
         }
         break;
      case sub_read_symbol+0x1: // TODO: blk_tail_call_m_cons_m_cons ???
         restore(regArg1); // restore prepend character
         reg.set(regTmp0,   cons(reg.get(regArg1), reg.get(regRetval)));
         reg.set(regTmp1,   cons(IS_SYMBOL,        reg.get(regTmp0)));
         reg.set(regRetval, reg.get(regTmp1));
         returnsub();
         break;

      case sub_read_symbol_body:
         // Parses the next symbol from regArg0.
         //
         // A helper for sub_read_symbol, but still a sub_ in its
         // own right.
         //
         // Returns a list of characters.
         //
         // All the characters in the symbol will have been
         // consumed from the input, and the next character in the
         // input will be the character immediately following the
         // end of the symbol.
         //
         reg.set(regTmp1, portPeek(reg.get(regArg0)));
         if ( EOF == reg.get(regTmp1) )
         {
            log("eof: returning");
            reg.set(regRetval, NIL);
            returnsub();
            break;
         }
         if ( TYPE_CHAR != type(reg.get(regTmp1)) )
         {
            log("non-char in input: ",pp(reg.get(regTmp1)));
            raiseError(ERR_INTERNAL);
            break;
         }
         switch (value(reg.get(regTmp1)))
         {
         case ' ':
         case '\t':
         case '\r':
         case '\n':
         case '(':
         case ')':
         case '"':
            reg.set(regRetval,  NIL);
            returnsub();
            break;
         default:
            portPop(reg.get(regArg0));
            store(regTmp1);
            gosub(sub_read_symbol_body,blk_tail_call_m_cons);
            break;
         }
         break;

      case sub_read_string:
         // Parses the next string literal from regArg0.
         //
         // Expects that the next character in the input is known
         // to be the leading '"' of a string literal.
         //
         reg.set(regTmp0,  portPeek(reg.get(regArg0)));
         if ( code(TYPE_CHAR,'"') != reg.get(regTmp0) )
         {
            log("non-\" leading string literal: ",pp(reg.get(regTmp0)));
            raiseError(ERR_LEXICAL);
            break;
         }
         portPop(reg.get(regArg0));
         store(regArg0);      // store port
         gosub(sub_read_string_body,sub_read_string+0x1);
         break;
      case sub_read_string+0x1:
         restore(regArg0);  // restore port
         reg.set(regTmp0, portPeek(reg.get(regArg0)));
         if ( code(TYPE_CHAR,'"') != reg.get(regTmp0) )
         {
            raiseError(ERR_LEXICAL);
            break;
         }
         portPop(reg.get(regArg0));
         reg.set(regRetval, cons(IS_STRING,reg.get(regRetval)));
         returnsub();
         break;

      case sub_read_string_body:
         // Parses the next string from regArg0.
         //
         // A helper for sub_read_string, but still a sub_ in its
         // own right.
         //
         // Expects that the leading \" has already been consumed,
         // and stops on the trailing \" (which is left unconsumed
         // for balance).
         //
         // Returns a list of characters.
         //
         // All the characters in the string will have been
         // consumed from the input, and the next character in the
         // input will be the trailing \".
         //
         reg.set(regTmp1, portPeek(reg.get(regArg0)));
         if ( EOF == reg.get(regTmp1) )
         {
            log("eof in string literal");
            raiseError(ERR_LEXICAL);
            break;
         }
         if ( TYPE_CHAR != type(reg.get(regTmp1)) )
         {
            log("non-char in input: ",pp(reg.get(regTmp1)));
            raiseError(ERR_INTERNAL);
            break;
         }
         switch (value(reg.get(regTmp1)))
         {
         case '"':
            reg.set(regRetval,  NIL);
            returnsub();
            break;
         default:
            portPop(reg.get(regArg0));
            store(regTmp1);
            gosub(sub_read_string_body,blk_tail_call_m_cons);
            break;
         }
         break;

      case sub_read_burn_space:
         // Consumes any whitespace from reg.get(regArg0).
         //
         // Returns UNSPECIFIED.
         //
         reg.set(regTmp0,  portPeek(reg.get(regArg0)));
         if ( EOF == reg.get(regTmp0) )
         {
            reg.set(regRetval, UNSPECIFIED);
            returnsub();
            break;
         }
         switch (value(reg.get(regTmp0)))
         {
         case ' ':
         case '\t':
         case '\r':
         case '\n':
            portPop(reg.get(regArg0));
            gosub(sub_read_burn_space,blk_tail_call);
            break;
         default:
            reg.set(regRetval, UNSPECIFIED);
            returnsub();
            break;
         }
         break;

      case sub_eval:
         // Evaluates the expr in reg.get(regArg0) in the env in
         // reg.get(regArg1), and leaves the results in
         // reg.get(regRetval).
         //
         switch (type(reg.get(regArg0)))
         {
         case TYPE_SENTINEL:
            switch (reg.get(regArg0))
            {
            case TRUE:
            case FALSE:
               // These values are self-evaluating
               reg.set(regRetval,  reg.get(regArg0));
               returnsub();
               break;
            case NIL:
               // The empty list is not self-evaluating.
               //
               // Covers expressions like "()" and "(())"..
               raiseError(ERR_SEMANTIC);
               break;
            default:
               log("unexpected value: ",pp(reg.get(regArg0)));
               raiseError(ERR_INTERNAL);
               break;
            }
            break;
         case TYPE_CHAR:
         case TYPE_FIXINT:
         case TYPE_SUBS:    // TODO: is this a valid decision?  Off-spec?
         case TYPE_SUBP:    // TODO: is this a valid decision?  Off-spec?
            // these types are self-evaluating
            reg.set(regRetval,  reg.get(regArg0));
            returnsub();
            break;
         case TYPE_CELL:
            tmp0 = car(reg.get(regArg0));
            switch (tmp0)
            {
            case IS_STRING:
               // Strings are self-evaluating.
               reg.set(regRetval,  reg.get(regArg0));
               returnsub();
               break;
            case IS_SYMBOL:
               // Lookup the symbol in the environment.
               //
               //   reg.get(regArg0) already contains the symbol
               //   reg.get(regArg1) already contains the env
               //
               // TODO: w/ a different variant of sub_eval_look_env,
               // could this be a tail call?  And I don't just mean
               // making a new function that calls sub_eval_look_env
               // that has our same continuation sub_eval+0x1... I
               // mean something that tail-recurses all the way to the
               // success-or-symbol-not-found logic.
               gosub(sub_eval_look_env,sub_eval+0x1);
               break;
            default:
               // Evaluate the operator: the type of the result
               // will determine whether we evaluate the args prior
               // to apply.
               reg.set(regTmp0,cdr(reg.get(regArg0)));
               store(regTmp0);             // store the arg exprs
               store(regArg1);             // store the env
               reg.set(regArg0,  tmp0);             // forward the op
               reg.set(regArg1,  reg.get(regArg1)); // forward the env
               gosub(sub_eval,sub_eval+0x2);
               break;
            }
            break;
         default:
            log("unexpected type: ",pp(reg.get(regArg0)));
            raiseError(ERR_INTERNAL);
            break;
         }
         break;
      case sub_eval+0x1:
         // following symbol lookup
         if ( NIL == reg.get(regRetval) )
         {
            // symbol not found: unbound variable
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( DEBUG && TYPE_CELL != type(reg.get(regRetval)) )
         {
            raiseError(ERR_INTERNAL);
            break;
         }
         reg.set(regRetval,  cdr(reg.get(regRetval)));
         returnsub();
         break;
      case sub_eval+0x2:
         // following eval of the first elem
         //
         // If it's a function, evaluate the args next, following
         // up with apply.
         //
         // If it's a special form, don't evaluate the args, just
         // pass it off to apply.
         //
         restore(regTmp1);         // restore the env
         restore(regTmp0);         // restore the arg exprs
         reg.set(regTmp2,  reg.get(regRetval));    // value of the operator
         tmp0 = type(reg.get(regTmp2));
         if ( TYPE_SUBP == tmp0 || 
              TYPE_CELL == tmp0 && IS_PROCEDURE == car(reg.get(regTmp2)) )
         {
            // procedure: evaluate the args and then apply op to
            // args values
            // 
            store(regTmp2);           // store value of the operator
            reg.set(regArg0,  reg.get(regTmp0));
            reg.set(regArg1,  reg.get(regTmp1));
            gosub(sub_eval_list,sub_eval+0x3);
            break;
         }
         if ( TYPE_SUBS == tmp0 || 
              TYPE_CELL == tmp0 && IS_SPECIAL_FORM == car(reg.get(regTmp2)) )
         {
            // special: apply op directly to args exprs
            //
            reg.set(regArg0,  reg.get(regTmp2));
            reg.set(regArg1,  reg.get(regTmp0));
            gosub(sub_apply,blk_tail_call);
            break;
         }
         // We get here, for instance, evaluating the expr (1).
         logrec("non-operator in sub_eval: ",tmp0);
         raiseError(ERR_SEMANTIC);
         break;
      case sub_eval+0x3:
         // following eval of the args
         restore(regArg0);      // restore value of the operator
         reg.set(regArg1,  reg.get(regRetval)); // restore list of args
         gosub(sub_apply,blk_tail_call);
         break;

      case sub_eval_list:
         // Evaluates all the expressions in the list in
         // reg.get(regArg0) in the env in reg.get(regArg1), and
         // returns a list of the results.
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
         // Using something most like the second version: I get to
         // exploit blk_tail_call_m_cons again! :)
         //
         if ( NIL == reg.get(regArg0) )
         {
            reg.set(regRetval,  NIL);
            returnsub();
            break;
         }
         reg.set(regTmp0,cdr(reg.get(regArg0)));
         store(regTmp0);               // the rest of the list
         store(regArg1);               // the env
         reg.set(regArg0,  car(reg.get(regArg0)));  // the head of the list
         reg.set(regArg1,  reg.get(regArg1));       // the env
         gosub(sub_eval,sub_eval_list+0x1);
         break;
      case sub_eval_list+0x1:
         restore(regArg1);          // the env
         restore(regArg0);          // the rest of the list
         store(regRetval);             // feed blk_tail_call_m_cons
         gosub(sub_eval_list,blk_tail_call_m_cons);
         break;

      case sub_eval_look_env:
         // Looks up the symbol in reg.get(regArg0) in the env in
         // reg.get(regArg1).
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
            logrec("sub_eval_look_env SYM",reg.get(regArg0));
            log(   "sub_eval_look_env ENV ",pp(reg.get(regArg1)));
         }
         if ( NIL == reg.get(regArg1) )
         {
            log("empty env: symbol not found");
            reg.set(regRetval,   NIL);
            returnsub();
            break;
         }
         store(regArg0);
         store(regArg1);
         reg.set(regArg1,  car(reg.get(regArg1)));
         gosub(sub_eval_look_frame,sub_eval_look_env+0x1);
         break;
      case sub_eval_look_env+0x1:
         restore(regArg1);
         restore(regArg0);
         if ( NIL != reg.get(regRetval) )
         {
            log("symbol found w/ bind: ",pp(reg.get(regRetval)));
            returnsub();
            break;
         }
         reg.set(regArg1,  cdr(reg.get(regArg1)));
         gosub(sub_eval_look_env,blk_tail_call);
         break;

      case sub_eval_look_frame:
         // Looks up the symbol in reg.get(regArg0) in the env
         // frame in reg.get(regArg1).
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
         logrec("sub_eval_look_frame SYM ",reg.get(regArg0));
         if ( NIL == reg.get(regArg1) )
         {
            reg.set(regRetval,  NIL);
            returnsub();
            break;
         }
         store(regArg0);
         store(regArg1);
         reg.set(regArg1,  car(car(reg.get(regArg1))));
         logrec("sub_eval_look_frame CMP ",reg.get(regArg1));
         gosub(sub_equal_p,sub_eval_look_frame+0x1);
         break;
      case sub_eval_look_frame+0x1:
         restore(regArg1);
         restore(regArg0);
         if ( TRUE == reg.get(regRetval) )
         {
            reg.set(regRetval,  car(reg.get(regArg1)));
            returnsub();
            break;
         }
         reg.set(regArg1,  cdr(reg.get(regArg1)));
         gosub(sub_eval_look_frame,blk_tail_call);
         break;

      case sub_equal_p:
         // Compares the objects in reg.get(regArg0) and
         // reg.get(regArg1).
         //
         // Returns TRUE in reg.get(regRetval) if they are
         // equivalent, being identical or having the same shape
         // and same value everywhere, FALSE otherwise.
         //
         // Does not handle cycles gracefully - and it may not be
         // necessary that it do so if we don't expose this to
         // users and ensure that it can only be called on objects
         // (like symbols) that are known to be cycle-free.
         //
         // NOTE: this is meant to be the equal? described in R5RS.
         //
         if ( reg.get(regArg0) == reg.get(regArg1) )
         {
            log("identical");
            reg.set(regRetval,  TRUE);
            returnsub();
            break;
         }
         if ( type(reg.get(regArg0)) != type(reg.get(regArg1)) )
         {
            log("different types");
            reg.set(regRetval,  FALSE);
            returnsub();
            break;
         }
         if ( type(reg.get(regArg0)) != TYPE_CELL )
         {
            log("not cells");
            reg.set(regRetval,  FALSE);
            returnsub();
            break;
         }
         log("checking car");
         store(regArg0);
         store(regArg1);
         reg.set(regArg0,  car(reg.get(regArg0)));
         reg.set(regArg1,  car(reg.get(regArg1)));
         gosub(sub_equal_p,sub_equal_p+0x1);
         break;
      case sub_equal_p+0x1:
         restore(regArg1);
         restore(regArg0);
         if ( FALSE == reg.get(regRetval) )
         {
            log("car mismatch");
            returnsub();
            break;
         }
         log("checking cdr");
         reg.set(regArg0,  cdr(reg.get(regArg0)));
         reg.set(regArg1,  cdr(reg.get(regArg1)));
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
         logrec("REWRITE INPUT:  ",reg.get(regArg0));
         reg.set(regTmp0,  car(reg.get(regArg0))); // regTmp0 is locals
         reg.set(regTmp2,  cdr(reg.get(regArg0)));
         if ( TYPE_CELL != type(reg.get(regTmp2)) ) 
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp1,  reg.get(regTmp2));      // regTmp1 is body
         logrec("REWRITE BODY A: ",reg.get(regTmp1));
         store(regTmp0);
         store(regTmp1);
         reg.set(regArg0,  sub_car);
         reg.set(regArg1,  reg.get(regTmp0));
         gosub(sub_map1,sub_let+0x1);
         break;
      case sub_let+0x1:
         // Note: Acknowledged, there is some wasteful stack manips
         // here, and a peek operation would be welcome, too.
         reg.set(regTmp2,  reg.get(regRetval));    // regTmp2 is params
         restore(regTmp1);         // restore body
         restore(regTmp0);         // restore locals
         store(regTmp0);           // store locals INEFFICIENT
         store(regTmp1);           // store body INEFFICIENT
         store(regTmp2);
         reg.set(regArg0,  sub_cadr);
         reg.set(regArg1,  reg.get(regTmp0));
         gosub(sub_map1,sub_let+0x2);
         break;
      case sub_let+0x2:
         reg.set(regTmp3,  reg.get(regRetval));    // regTmp3 is values
         restore(regTmp2);         // restore params
         restore(regTmp1);         // restore body
         logrec("REWRITE BODY C: ",reg.get(regTmp1));
         restore(regTmp0);         // restore locals
         reg.set(regTmp4,  reg.get(regTmp1));
         reg.set(regTmp5,  cons( reg.get(regTmp2), reg.get(regTmp4) ));
         reg.set(regTmp6,  cons( sub_lambda,   reg.get(regTmp5) ));
         reg.set(regTmp7,  cons( reg.get(regTmp6), reg.get(regTmp3) ));
         logrec("REWRITE OUTPUT: ",reg.get(regTmp7));
         reg.set(regArg0,  reg.get(regTmp7));
         reg.set(regArg1,  reg.get(regEnv));
         gosub(sub_eval,blk_tail_call);
         break;

      case sub_map1:
         // Applies the unary operator in regArg0 to each element of
         // the list in regArg1, and returns a list of the results in
         // order.
         //
         // The surplus cons() before we call sub_apply is perhaps
         // regrettable, but the rather than squeeze more
         // complexity into the sub_apply family, I choose here to
         // work around the variadicity checking and globbing in
         // sub_apply.
         //
         // I would prefer it if this sub_map1 worked with either/both
         // of builtins and user-defineds, and this way it does.
         //
         // Called sub_map1 because it only handles unary operators.
         // The real Scheme "map" is variadic, but that proved out of
         // scope here in the fundamental layer.  While sub_map1 is a
         // useful utility here below, a sub_mapN would be difficult
         // to make correct, succinct and tail-recursive.
         //
         if ( NIL == reg.get(regArg1) )
         {
            reg.set(regRetval,   NIL);
            returnsub();
            break;
         }
         reg.set(regTmp0, car(reg.get(regArg1))); // head
         reg.set(regTmp1, cdr(reg.get(regArg1))); // rest
         store(regArg0);                          // store operator
         store(regTmp1);                          // store rest of operands
         reg.set(regArg0, reg.get(regArg0));
         reg.set(regArg1, cons(reg.get(regTmp0),NIL));
         gosub(sub_apply,sub_map1+0x1);
         break;
      case sub_map1+0x1:
         restore(regArg1);                        // restore rest of operands
         restore(regArg0);                        // restore operator
         store(regRetval);                        // feed blk_tail_call_m_cons
         gosub(sub_map1,blk_tail_call_m_cons);
         break;

      case sub_begin:
         // Evaluates all its args, returning the result of the
         // last.  If no args, returns UNSPECIFIED.
         //
         if ( NIL == reg.get(regArg0) )
         {
            reg.set(regRetval,  UNSPECIFIED);
            returnsub();
            break;
         }
         reg.set(regTmp0,  car(reg.get(regArg0)));
         reg.set(regTmp1,  cdr(reg.get(regArg0)));
         reg.set(regArg0,  reg.get(regTmp0));
         reg.set(regArg1,  reg.get(regEnv));
         reg.set(regTmp2,  UNSPECIFIED);
         if ( NIL == reg.get(regTmp1) )
         {
            reg.set(regTmp2,  blk_tail_call);
         }
         else
         {
            store(regTmp1);             // store rest exprs
            reg.set(regTmp2,  sub_begin+0x1);
         }
         gosub(sub_eval,reg.get(regTmp2));
         break;
      case sub_begin+0x1:
         restore(regArg0);           // restore rest exprs
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
         if ( NIL == reg.get(regArg0) )
         {
            reg.set(regRetval,  UNSPECIFIED);
            returnsub();
            break;
         }
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,  car(car(reg.get(regArg0))));  // test of first clause
         reg.set(regTmp1,  cdr(car(reg.get(regArg0))));  // body of first clause
         reg.set(regTmp2,  cdr(reg.get(regArg0)));       // rest of clauses
         store(regTmp1);        // store body of 1st clause
         store(regTmp2);        // store rest of clauses
         logrec("test",reg.get(regTmp0));
         reg.set(regArg0,  reg.get(regTmp0));
         reg.set(regArg1,  reg.get(regEnv));
         gosub(sub_eval,sub_cond+0x1);
         break;
      case sub_cond+0x1:
         restore(regTmp2);               // restore rest of clauses
         restore(regTmp1);               // restore body of 1st clause
         if ( FALSE == reg.get(regRetval) )
         {
            logrec("rest",reg.get(regTmp2));
            reg.set(regArg0,  reg.get(regTmp2));
            gosub(sub_cond,blk_tail_call);
         }
         else if ( NIL == reg.get(regTmp1) )
         {
            log("no body");
            reg.set(regRetval,  reg.get(regRetval));
            returnsub();
         }
         else
         {
            logrec("body",reg.get(regTmp1));
            reg.set(regArg0,  reg.get(regTmp1));
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
         // alternative paths. Neither (cond) nor an (if) chain can
         // offer this.
         //
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);       // missing key
            break;
         }
         reg.set(regTmp0,  car(reg.get(regArg0)));  // key
         reg.set(regTmp1,  cdr(reg.get(regArg0)));  // clauses
         if ( TYPE_CELL != type(reg.get(regTmp1)) )
         {
            raiseError(ERR_SEMANTIC);       // missing clauses
            break;
         }
         logrec("key expr:  ",reg.get(regTmp0));
         store(regTmp1);               // store clauses
         reg.set(regArg0,  reg.get(regTmp0));
         reg.set(regArg1,  reg.get(regEnv));
         gosub(sub_eval,sub_case+0x1);
         break;
      case sub_case+0x1:
         reg.set(regTmp0,  reg.get(regRetval));     // value of key
         restore(regTmp1);          // restore clauses
         logrec("key value: ",reg.get(regTmp0));
         logrec("clauses:   ",reg.get(regTmp1));
         reg.set(regArg0,  reg.get(regTmp0));
         reg.set(regArg1,  reg.get(regTmp1));
         gosub(sub_case_search,blk_tail_call);
         break;

      case sub_case_search:
         // reg.get(regArg0) is the value of the key
         // reg.get(regArg1) is the list of clauses
         logrec("key value:   ",reg.get(regArg0));
         logrec("clause list: ",reg.get(regArg1));
         if ( NIL == reg.get(regArg1) ) 
         {
            reg.set(regRetval,  UNSPECIFIED);
            returnsub();
            break;
         }
         if ( TYPE_CELL != type(reg.get(regArg1)) )
         {
            raiseError(ERR_SEMANTIC);      // bogus clause list
            break;
         }
         reg.set(regTmp0,  car(reg.get(regArg1))); // first clause
         reg.set(regTmp1,  cdr(reg.get(regArg1))); // rest clauses
         logrec("first clause:",reg.get(regTmp0));
         logrec("rest clauses:",reg.get(regTmp1));
         if ( TYPE_CELL != type(reg.get(regTmp0)) )
         {
            raiseError(ERR_SEMANTIC);      // bogus clause
            break;
         }
         reg.set(regTmp2,  car(reg.get(regTmp0))); // first clause label list
         reg.set(regTmp3,  cdr(reg.get(regTmp0))); // first clause body
         logrec("label list:  ",reg.get(regTmp2));
         store(regArg0);              // store key
         store(regTmp1);              // store rest clauses
         store(regTmp3);              // store body
         reg.set(regArg0,  reg.get(regArg0));
         reg.set(regArg1,  reg.get(regTmp2));
         gosub(sub_case_in_list_p,sub_case_search+0x1);
         break;
      case sub_case_search+0x1:
         restore(regTmp3);         // restore body
         restore(regArg1);         // restore rest clauses
         restore(regArg0);         // restore key
         logrec("key:         ",reg.get(regArg0));
         logrec("rest clauses:",reg.get(regArg1));
         logrec("matchup:     ",reg.get(regRetval));
         logrec("body:        ",reg.get(regTmp3));
         if ( TYPE_CELL != type(reg.get(regTmp3)) )
         {
            // empty bodies are not cool in (case)
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( FALSE == reg.get(regRetval) )
         {
            gosub(sub_case_search,blk_tail_call);
         }
         else
         {
            reg.set(regArg0,  reg.get(regTmp3));
            gosub(sub_begin,blk_tail_call);
         }
         break;

      case sub_case_in_list_p:
         // Returns TRUE if reg.get(regArg0) is hard-equal to any
         // of the elements in the proper list in reg.get(regArg1),
         // else FALSE.
         //
         // Only works w/ lables as per sub_case: fixints,
         // booleans, and characer literals.  Nothing else will
         // match.
         logrec("key:   ",reg.get(regArg0));
         logrec("labels:",reg.get(regArg1));
         if ( NIL == reg.get(regArg1) ) 
         {
            reg.set(regRetval,  FALSE);
            returnsub();
            break;
         }
         if ( TYPE_CELL != type(reg.get(regArg1)) ) 
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,  car(reg.get(regArg1)));  // first element
         reg.set(regTmp1,  cdr(reg.get(regArg1)));  // rest of elements
         if ( reg.get(regArg0) == reg.get(regTmp0) )
         {
            // TODO: Check type?  We would not want them to both be
            // interned strings, or would we...?
            reg.set(regRetval,   TRUE);
            returnsub();
            break;
         }
         reg.set(regArg0,  reg.get(regArg0));
         reg.set(regArg1,  reg.get(regTmp1));
         gosub(sub_case_in_list_p,blk_tail_call);
         break; 

      case sub_apply:
         // Applies the op in reg.get(regArg0) to the args in
         // reg.get(regArg1), and return the results.
         //
         switch (type(reg.get(regArg0)))
         {
         case TYPE_SUBP:
         case TYPE_SUBS:
            gosub(sub_apply_builtin,blk_tail_call);
            break;
         case TYPE_CELL:
            switch (car(reg.get(regArg0)))
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
         // Applies the sub_foo in reg.get(regArg0) to the args in
         // reg.get(regArg1), and return the results.
         //
         // get arity
         //
         // - if AX, just put the list of args in reg.get(regArg0).
         //
         // - if A<N>, assign N entries from list at
         //   reg.get(regArg1) into reg.get(regArg0.regArg<N>).
         //   Freak out if there are not exactly N args.
         //
         // Then just gosub()!
         tmp0 = reg.get(regArg0);
         final int arity = (tmp0 & MASK_ARITY) >>> SHIFT_ARITY;
         log("tmp0:  ",pp(tmp0));
         log("tmp0:  ",hex(tmp0,8));
         log("arity: ",arity);
         log("arg1:  ",pp(reg.get(regArg1)));
         reg.set(regTmp0,  reg.get(regArg1));
         reg.set(regArg0,  UNSPECIFIED);
         reg.set(regArg1,  UNSPECIFIED);
         reg.set(regArg2,  UNSPECIFIED);
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
            reg.set(regArg0,  reg.get(regTmp0));
            gosub(tmp0,blk_tail_call);
            break;
         case A3:
            if ( NIL == reg.get(regTmp0) )
            {
               log("too few args");
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg.set(tmp1,     car(reg.get(regTmp0)));
            reg.set(regTmp0,  cdr(reg.get(regTmp0)));
            log("pop arg: ",pp(reg.get(regArg0)));
            tmp1++;
            // fall through
         case A2:
            if ( NIL == reg.get(regTmp0) )
            {
               log("too few args");
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg.set(tmp1,     car(reg.get(regTmp0)));
            reg.set(regTmp0,  cdr(reg.get(regTmp0)));
            log("pop arg: ",pp(reg.get(regArg0)));
            tmp1++;
         case A1:
            if ( NIL == reg.get(regTmp0) )
            {
               log("too few args");
               raiseError(ERR_SEMANTIC);
               break;
            }
            reg.set(tmp1,     car(reg.get(regTmp0)));
            reg.set(regTmp0,  cdr(reg.get(regTmp0)));
            log("pop arg: ",pp(reg.get(regArg0)));
            tmp1++;
         case A0:
            if ( NIL != reg.get(regTmp0) )
            {
               log("too many args");
               raiseError(ERR_SEMANTIC);
               break;
            }
            log("arg0: ",pp(reg.get(regArg0)));
            log("arg1: ",pp(reg.get(regArg1)));
            log("arg2: ",pp(reg.get(regArg2)));
            gosub(tmp0,blk_tail_call);
            break;
         default:
            raiseError(ERR_INTERNAL);
            break;
         }
         break;

      case sub_apply_user:
         // Applies the user-defined procedure or special form in
         // reg.get(regArg0) to the args in reg.get(regArg1), and
         // return the results.
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
         if ( DEBUG && TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_INTERNAL);
            break;
         }
         if ( DEBUG && IS_PROCEDURE != car(reg.get(regArg0)) )
         {
            raiseError(ERR_INTERNAL);
            break;
         }
         store(regArg0);
         reg.set(regArg0,  car(cdr(reg.get(regArg0))));
         reg.set(regArg1,  reg.get(regArg1));
         gosub(sub_zip,sub_apply_user+0x1);
         break;
      case sub_apply_user+0x1:
         restore(regArg0);                        // restore op
         reg.set(regTmp0,  reg.get(regRetval));                   // args frame
         reg.set(regTmp1,  car(cdr(cdr(reg.get(regArg0)))));      // op body
         reg.set(regTmp3,  car(cdr(cdr(cdr(reg.get(regArg0)))))); // op lexical env
         reg.set(regTmp2,  cons(reg.get(regTmp0),reg.get(regEnv)));   // apply env
         logrec("sub_apply_user BODY   ",reg.get(regTmp1));
         logrec("sub_apply_user FRAME  ",reg.get(regTmp0));
         //logrec("sub_apply_user CUR ENV",reg.get(regEnv));
         //logrec("sub_apply_user LEX ENV",reg.get(regTmp3));

         // going w/ lexical frames
         reg.set(regTmp2,  cons(reg.get(regTmp0),reg.get(regTmp3)));

         logrec("sub_apply_user ENV    ",reg.get(regTmp2));
         reg.set(regArg0,  reg.get(regTmp1));
         reg.set(regArg1,  reg.get(regTmp2));
         //
         // At first glance, this env manip feels like it should be
         // the job of sub_eval. After all, sub_eval gets an
         // environment arg, and sub_apply does not.
         //
         // After deeper soul searching, this is not true.  We
         // certainly would not want (eval) to push/pop the env on
         // *every* call, but only sub_apply_user and sub_let know
         // what the new frames are, and only sub_apply_user knows
         // where to find the lexical scope of a procedure or
         // special form.
         //
         log("LEXICAL ENV PREPUSH:  ",pp(reg.get(regEnv)));
         store(regEnv);
         reg.set(regEnv,  reg.get(regTmp2));
         log("LEXICAL ENV POSTPUSH: ",pp(reg.get(regEnv)));
         gosub(sub_begin, sub_apply_user+0x2);
         break;
      case sub_apply_user+0x2:
         // I am so sad that pushing that env above means we cannot
         // be tail recursive.  At least this that is not true on
         // every sub_eval.
         //
         log("LEXICAL ENV PREPOP:  ",pp(reg.get(regEnv)));
         restore(regEnv);
         log("LEXICAL ENV POSTPOP: ",pp(reg.get(regEnv)));
         returnsub();
         break;

      case sub_zip:
         // Expects lists of equal lengths in regArg0 and regArg1.
         //
         // Returns a new list of the same length, whose elments are
         // cons() of corresponding elements from the argument lists
         // respectively e.g.:
         //
         //   (sub_zip '(1 2) '(10 20)) ===> '((1 10) (2 20)
         //
         // Note: If (when!) we have sub_map2, this is really just:
         //
         //   (sub_map2 cons listA listB)
         //
         // But, then, maybe sub_map2 would want to use sub_zip in the
         // implementation. ;)
         //
         if ( NIL == reg.get(regArg0) && NIL == reg.get(regArg1) )
         {
            reg.set(regRetval,  NIL);
            returnsub();
            break;
         }
         if ( NIL == reg.get(regArg0) || NIL == reg.get(regArg1) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,  car(reg.get(regArg0)));
         reg.set(regTmp1,  car(reg.get(regArg1)));
         reg.set(regTmp2,  cons(reg.get(regTmp0),reg.get(regTmp1)));
         reg.set(regArg0,  cdr(reg.get(regArg0)));
         reg.set(regArg1,  cdr(reg.get(regArg1)));
         store(regTmp2);
         gosub(sub_zip,blk_tail_call_m_cons);
         break;

      case sub_printv:
         // Parses the first arg to an output input port.
         //
         // Is variadic, 1 or 2 arguments: if the second arg is
         // present, is expected to be an output port.  Otherwise,
         // regIn is used.
         //
         // (define (sub_printv val)      (sub_print val regIn))
         // (define (sub_printv val port) (sub_print val port))
         //
         // TODO: get more ports, and test that this mess works with
         // other than regOut!
         //
         if ( NIL == reg.get(regArg0) )
         {
            log("too few args");
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0, car(reg.get(regArg0)));
         reg.set(regTmp1, cdr(reg.get(regArg0)));
         reg.set(regArg0, reg.get(regTmp0));
         if ( NIL == reg.get(regTmp1) )
         {
            log("defaulting to regOut");
            reg.set(regArg1, reg.get(regOut));
         }
         else if ( TYPE_CELL != type(reg.get(regTmp1)) )
         {
            log("bogus arg: ",pp(reg.get(regTmp1)));
            raiseError(ERR_SEMANTIC);
            break;
         }
         else
         {
            reg.set(regTmp2, car(reg.get(regTmp1)));
            reg.set(regTmp3, cdr(reg.get(regTmp1)));
            if ( NIL != reg.get(regTmp3) )
            {
               log("too many args");
               raiseError(ERR_SEMANTIC);
               break;
            }
            logrec("HO:",reg.get(regTmp1));
            reg.set(regArg1, reg.get(regTmp2));
         }
         gosub(sub_print,blk_tail_call);
         break;

      case sub_print:
         // Prints the expr in regArg0 to the ouput port in regArg1.
         //
         // Returns UNSPECIFIED.
         //
         if ( TYPE_IOBUF != type(reg.get(regArg1)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0, UNSPECIFIED);
         log("printing: ",pp(reg.get(regArg0)));
         switch (type(reg.get(regArg0)))
         {
         case TYPE_SENTINEL:
            switch (reg.get(regArg0))
            {
            case UNSPECIFIED:
               reg.set(regRetval, UNSPECIFIED);
               returnsub();
               break;
            case NIL:
               gosub(sub_print_list,blk_tail_call);
               break;
            case TRUE:
               reg.set(regArg0, const_true);
               reg.set(regArg2, code(TYPE_FIXINT,0));
               gosub(sub_print_const,blk_tail_call);
               break;
            case FALSE:
               reg.set(regArg0, const_false);
               reg.set(regArg2, code(TYPE_FIXINT,0));
               gosub(sub_print_const,blk_tail_call);
               break;
            default:
               log("bogus sentinel: ",pp(reg.get(regArg0)));
               raiseError(ERR_INTERNAL);
               break;
            }
            break;
         case TYPE_CHAR:
            gosub(sub_print_char,blk_tail_call);
            break;
         case TYPE_FIXINT:
            gosub(sub_print_fixint,blk_tail_call);
            break;
         case TYPE_SUBP:
         case TYPE_SUBS:
            // TODO: some decisions to be made here about how these
            // really print, but that kind of depends on how I land
            // about how they lex.
            //
            // In the mean time, this is sufficient to meet spec.
            //
            reg.set(regArg0, const_huhPhuh);
            reg.set(regArg2, code(TYPE_FIXINT,0));
            gosub(sub_print_const,blk_tail_call);
            break;
         case TYPE_CELL:
            reg.set(regTmp1, car(reg.get(regArg0)));
            reg.set(regTmp2, cdr(reg.get(regArg0)));
            switch (reg.get(regTmp1))
            {
            case IS_STRING:
               reg.set(regArg0, reg.get(regTmp2));
               gosub(sub_print_string,blk_tail_call);
               break;
            case IS_SYMBOL:
               reg.set(regArg0, reg.get(regTmp2));
               gosub(sub_print_chars,blk_tail_call);
               break;
            case IS_PROCEDURE:
               reg.set(regArg0, const_huh3);
               reg.set(regArg2, code(TYPE_FIXINT,0));
               gosub(sub_print_const,blk_tail_call);
               break;
            default:
               reg.set(regArg0, reg.get(regArg0));
               gosub(sub_print_list,blk_tail_call);
               break;
            }
            break;
         default:
            raiseError(ERR_INTERNAL);
            break;
         }
         break;
            
      case sub_print_string:
         // Prints the list in regArg0, whose elements are expected to
         // all be TYPE_CHAR, to the output port in regArg1 in
         // double-quotes.
         //
         store(regArg1);         // store port
         portPush(regArg1,code(TYPE_CHAR,'"'));
         gosub(sub_print_chars,sub_print_string+0x1);
         break;
      case sub_print_string+0x1:
         restore(regArg1);      // restore port
         portPush(regArg1,code(TYPE_CHAR,'"'));
         reg.set(regRetval,  UNSPECIFIED);
         returnsub();
         break;

      case sub_print_chars:
         // Prints the list in regArg0, whose elements are expected to
         // all be TYPE_CHAR, to the output port in regArg1.
         //
         if ( NIL == reg.get(regArg0) )
         {
            reg.set(regRetval,  UNSPECIFIED);
            returnsub();
            break;
         }
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            log("bogus non-cell: ",pp(reg.get(regArg0)));
            raiseError(ERR_INTERNAL);
            break;
         }
         reg.set(regTmp1,  car(reg.get(regArg0)));
         reg.set(regTmp2,  cdr(reg.get(regArg0)));
         if ( TYPE_CHAR != type(reg.get(regTmp1)) )
         {
            log("bogus: ",pp(reg.get(regTmp1)));
            raiseError(ERR_INTERNAL);
            break;
         }
         portPush(regArg1,reg.get(regTmp1));
         reg.set(regArg0,  reg.get(regTmp2));
         gosub(sub_print_chars,blk_tail_call);
         break;

      case sub_print_const:
         // Prints the chars in the const_str[] at regArg0 to the
         // output port in regArg1 beginning at offset regArg2.
         //
         // Returns UNSPECIFIED.
         //
         tmp0 = value_fixint(reg.get(regArg0)); // const offset
         tmp1 = value_fixint(reg.get(regArg2)); // string offset
         log("tmp0: " + tmp0);
         log("tmp1: " + tmp1);
         if ( DEBUG && (tmp0 < 0 || tmp0 >= const_str.length ))
         {
            log("bad tmp0: " + tmp0 + " of " + const_str.length);
            raiseError(ERR_INTERNAL);
            break;
         }
         if ( tmp1 >= const_str[tmp0].length() )
         {
            reg.set(regRetval,UNSPECIFIED);
            returnsub();
            break;
         }
         if ( DEBUG && tmp1 < 0 )
         {
            log("bad tmp1: " + tmp1 + " of " + const_str[tmp0].length());
            raiseError(ERR_INTERNAL);
            break;
         }
         tmp0 = code(TYPE_CHAR,const_str[tmp0].charAt(tmp1));
         tmp1 = tmp1 + 1;
         portPush(regArg1,tmp0);
         reg.set(regArg2, code(TYPE_FIXINT,tmp1));
         gosub(sub_print_const,blk_tail_call);
         break;

      case sub_print_char:
         // Prints the char at regArg0 to the ouput port at regArg1.
         //
         // Returns UNSPECIFIED.
         //
         store(regArg0);                            // store char
         store(regArg1);                            // store port
         reg.set(regArg0, const_prechar);           // "#\\"
         reg.set(regArg2, code(TYPE_FIXINT,0));
         gosub(sub_print_const,sub_print_char+0x1);
         break;
      case sub_print_char+0x1:
         restore(regArg1);                          // restore port
         restore(regArg0);                          // restore char
         switch (value(reg.get(regArg0)))
         {
         case ' ':
            reg.set(regArg0, const_space);
            reg.set(regArg2, code(TYPE_FIXINT,0));
            gosub(sub_print_const,blk_tail_call);
            break;
         case '\n':
            reg.set(regArg0, const_newline);
            reg.set(regArg2, code(TYPE_FIXINT,0));
            gosub(sub_print_const,blk_tail_call);
            break;
         default:
            portPush(regArg1,reg.get(regArg0));
            reg.set(regRetval, UNSPECIFIED);
            returnsub();
            break;
         }
         break;

      case sub_print_fixint:
         // Prints the fixint at regArg0 to the ouput port at regArg1.
         //
         // Returns UNSPECIFIED.
         //
         reg.set(regTmp1,  value_fixint(reg.get(regArg0)));
         if ( reg.get(regTmp1) < 0 )
         {
            portPush(regArg1,code(TYPE_CHAR,'-'));
            reg.set(regTmp1,  -reg.get(regTmp1));
         }
         if ( reg.get(regTmp1) == 0 )
         {
            portPush(regArg1,code(TYPE_CHAR,'0'));
            reg.set(regRetval,  UNSPECIFIED);
            returnsub();
            break;
         }
         int factor = 1000000000; // big enough up to 2**32, we have 2**28
         while ( factor > 0 && 0 == reg.get(regTmp1)/factor )
         {
            factor /= 10;
         }
         while ( factor > 0 )
         {
            final int digit  = reg.get(regTmp1)/factor;
            reg.set(regTmp1,   reg.get(regTmp1) - digit * factor);
            factor          /= 10;
            portPush(regArg1,code(TYPE_CHAR,'0'+digit));
         }
         reg.set(regRetval,  UNSPECIFIED);
         returnsub();
         break;

      case sub_print_list:
         // Prints the list (NIL or a cell) in regArg0 to the output
         // port in regArg1, in parens.
         //
         store(regArg1);         // store port
         reg.set(regArg0,  reg.get(regArg0));
         reg.set(regArg2,  TRUE);
         portPush(regArg1,code(TYPE_CHAR,'('));
         gosub(sub_print_list_elems,sub_print_list+0x1);
         break;
      case sub_print_list+0x1:
         restore(regArg1);      // restore port
         portPush(regArg1,code(TYPE_CHAR,')'));
         reg.set(regRetval,  UNSPECIFIED);
         returnsub();
         break;

      case sub_print_list_elems:
         // Prints the elements in the list (NIL or a cell) in regArg0
         // to the ouput port in regArg1 with a space between each.
         //
         // Furthermore, regArg2 should be TRUE if regArg0 is the
         // first item in the list, FALSE otherwise.
         //
         // Returns UNSPECIFIED.
         //
         if ( NIL == reg.get(regArg0) )
         {
            reg.set(regRetval,  UNSPECIFIED);
            returnsub();
            break;
         }
         if ( FALSE == reg.get(regArg2) )
         {
            portPush(regArg1,code(TYPE_CHAR,' '));
         }
         store(regArg0);
         store(regArg1);
         reg.set(regTmp0,  car(reg.get(regArg0)));
         reg.set(regTmp1,  cdr(reg.get(regArg0)));
         if ( NIL       != reg.get(regTmp1)       &&
              TYPE_CELL != type(reg.get(regTmp1))  )
         {
            log("dotted list");
            reg.set(regArg0,  reg.get(regTmp0));
            gosub(sub_print,sub_print_list_elems+0x2);
         }
         else
         {
            log("regular list so far");
            reg.set(regArg0,  reg.get(regTmp0));
            gosub(sub_print,sub_print_list_elems+0x1);
         }
         break;
      case sub_print_list_elems+0x1:
         restore(regArg1);
         restore(regTmp0);
         reg.set(regArg0,  cdr(reg.get(regTmp0)));
         reg.set(regArg2,  FALSE);
         gosub(sub_print_list_elems,blk_tail_call);
         break;
      case sub_print_list_elems+0x2:
         restore(regArg1);
         restore(regTmp0);
         reg.set(regArg0,  cdr(reg.get(regTmp0)));
         portPush(regArg1,code(TYPE_CHAR,' '));
         portPush(regArg1,code(TYPE_CHAR,'.'));
         portPush(regArg1,code(TYPE_CHAR,' '));
         gosub(sub_print,blk_tail_call);
         break;

      case sub_add:
         if ( TYPE_FIXINT != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( TYPE_FIXINT != type(reg.get(regArg1)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,    value_fixint(reg.get(regArg0)));
         reg.set(regTmp1,    value_fixint(reg.get(regArg1)));
         reg.set(regTmp2,    reg.get(regTmp0) + reg.get(regTmp1));
         reg.set(regRetval,  code(TYPE_FIXINT,reg.get(regTmp2)));
         returnsub();
         break;

      case sub_add0:
         reg.set(regRetval,  code(TYPE_FIXINT,0));
         returnsub();
         break;

      case sub_add1:
         if ( TYPE_FIXINT != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regRetval,  reg.get(regArg0));
         returnsub();
         break;

      case sub_add3:
         if ( TYPE_FIXINT != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( TYPE_FIXINT != type(reg.get(regArg1)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( TYPE_FIXINT != type(reg.get(regArg2)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,   value_fixint(reg.get(regArg0)));
         reg.set(regTmp1,   value_fixint(reg.get(regArg1)));
         reg.set(regTmp2,   value_fixint(reg.get(regArg2)));
         reg.set(regTmp3,   reg.get(regTmp0)+reg.get(regTmp1)+reg.get(regTmp2));
         reg.set(regRetval, code(TYPE_FIXINT,reg.get(regTmp3)));
         returnsub();
         break;

      case sub_mul:
         if ( TYPE_FIXINT != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( TYPE_FIXINT != type(reg.get(regArg1)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,    value_fixint(reg.get(regArg0)));
         reg.set(regTmp1,    value_fixint(reg.get(regArg1)));
         reg.set(regTmp2,    reg.get(regTmp0) * reg.get(regTmp1));
         reg.set(regRetval,  code(TYPE_FIXINT,reg.get(regTmp2)));
         returnsub();
         break;

      case sub_sub:
         if ( TYPE_FIXINT != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( TYPE_FIXINT != type(reg.get(regArg1)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,    value_fixint(reg.get(regArg0)));
         reg.set(regTmp1,    value_fixint(reg.get(regArg1)));
         reg.set(regTmp2,    reg.get(regTmp0) - reg.get(regTmp1));
         reg.set(regRetval,  code(TYPE_FIXINT,reg.get(regTmp2)));
         returnsub();
         break;

      case sub_lt_p:
         if ( TYPE_FIXINT != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         if ( TYPE_FIXINT != type(reg.get(regArg1)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regTmp0,  value_fixint(reg.get(regArg0)));
         reg.set(regTmp1,  value_fixint(reg.get(regArg1)));
         if ( reg.get(regTmp0) < reg.get(regTmp1) )
         {
            reg.set(regRetval,TRUE);
         }
         else
         {
            reg.set(regRetval,FALSE);
         }
         returnsub();
         break;

      case sub_cons:
         log("cons: ",pp(reg.get(regArg0)));
         log("cons: ",pp(reg.get(regArg1)));
         reg.set(regRetval,  cons(reg.get(regArg0),reg.get(regArg1)));
         returnsub();
         break;

      case sub_car:
         log("car: ",pp(reg.get(regArg0)));
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regRetval,  car(reg.get(regArg0)));
         returnsub();
         break;

      case sub_cdr:
         log("cdr: ",pp(reg.get(regArg0)));
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regRetval,  cdr(reg.get(regArg0)));
         returnsub();
         break;

      case sub_cadr:
         log("cadr: ",pp(reg.get(regArg0)));
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);
            break;
         }
         reg.set(regRetval,  car(cdr(reg.get(regArg0))));
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
         reg.set(regRetval,  reg.get(regArg0));
         returnsub();
         break;

      case sub_if:
         log("arg0: ",pp(reg.get(regArg0)));
         log("arg1: ",pp(reg.get(regArg1)));
         log("arg2: ",pp(reg.get(regArg2)));
         store(regArg1);
         store(regArg2);
         reg.set(regArg0,  reg.get(regArg0));
         reg.set(regArg1,  reg.get(regEnv));
         gosub(sub_eval,sub_if+0x1);
         break;
      case sub_if+0x1:
         restore(regArg2);
         restore(regArg1);
         if ( FALSE != reg.get(regRetval) )
         {
            reg.set(regArg0,  reg.get(regArg1));
         }            
         else
         {
            reg.set(regArg0,  reg.get(regArg2));
         }
         reg.set(regArg1,  reg.get(regEnv));
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
         logrec("args: ",reg.get(regArg0));
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);  // variadic with at least 2 args
            break;
         }
         reg.set(regTmp0,  car(reg.get(regArg0)));
         reg.set(regTmp1,  cdr(reg.get(regArg0)));
         logrec("head: ",reg.get(regTmp0));
         logrec("rest: ",reg.get(regTmp1));
         if ( TYPE_CELL != type(reg.get(regTmp0)) )
         {
            raiseError(ERR_SEMANTIC);  // first is symbol or arg list
            break;
         }
         if ( TYPE_CELL != type(reg.get(regTmp1)) )
         {
            raiseError(ERR_SEMANTIC);  // variadic with at least 2 args
            break;
         }
         if ( IS_SYMBOL == car(reg.get(regTmp0)) )
         {
            if ( NIL != cdr(reg.get(regTmp1)) )
            {
               raiseError(ERR_SEMANTIC); // simple form takes exactly 2 args
               break;
            }
            // reg.get(regTmp0) is already the symbol, how we want
            // it for this case.
            reg.set(regTmp1,  car(reg.get(regTmp1)));
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
            reg.set(regTmp2,  car(reg.get(regTmp0))); // actual symbol
            reg.set(regTmp3,  cdr(reg.get(regTmp0))); // actual arg list
            reg.set(regTmp0,  reg.get(regTmp2));      // regTmp0 good, regTmp2 free
            logrec("proc symbol: ",reg.get(regTmp0));
            logrec("proc args:   ",reg.get(regTmp3));
            logrec("proc body:   ",reg.get(regTmp1));
            reg.set(regTmp2,  cons(reg.get(regTmp3),reg.get(regTmp1)));
            logrec("partial:     ",reg.get(regTmp2));
            reg.set(regTmp1,  cons(sub_lambda,reg.get(regTmp2)));
            logrec("proc lambda: ",reg.get(regTmp1));
         }
         // By here, reg.get(regTmp0) should be the the symbol,
         // reg.get(regTmp1) the expr whose value we will bind to
         // the symbol.
         logrec("DEFINE SYMBOL: ",reg.get(regTmp0));
         logrec("DEFINE BODY:   ",reg.get(regTmp1));
         store(regTmp0);                       // store the symbol
         reg.set(regArg0,  reg.get(regTmp1));  // eval the body
         reg.set(regArg1,  reg.get(regEnv));   // we need an env arg here!
         gosub(sub_eval,sub_define+0x1);
         break;
      case sub_define+0x1:
         restore(regTmp0);            // restore the symbol
         store(regTmp0);              // store the symbol INEFFICIENT
         store(regRetval);            // store the body's value
         reg.set(regArg0, reg.get(regTmp0));     // lookup the binding
         reg.set(regArg1, car(reg.get(regEnv))); // we need an env arg here!
         gosub(sub_eval_look_frame,sub_define+0x2);
         break;
      case sub_define+0x2:
         restore(regTmp1);         // restore the body's value
         restore(regTmp0);         // restore the symbol
         if ( NIL == reg.get(regRetval) )
         {
            // create a new binding               // we need an env arg here!
            reg.set(regTmp1,  cons(reg.get(regTmp0),reg.get(regTmp1)));
            reg.set(regTmp2,  cons(reg.get(regTmp1),car(reg.get(regEnv))));
            setcar(reg.get(regEnv),reg.get(regTmp2));
            log("define new binding");
               
         }
         else
         {
            // change the existing binding
            setcdr(reg.get(regRetval),reg.get(regTmp1));
            log("define old binding");
         }
         //logrec("define B",reg.get(regEnv));
         reg.set(regRetval,  UNSPECIFIED);
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
         logrec("lambda args: ",reg.get(regArg0));
         if ( TYPE_CELL != type(reg.get(regArg0)) )
         {
            raiseError(ERR_SEMANTIC);  // must have at least 2 args
            break;
         }
         reg.set(regTmp0,  car(reg.get(regArg0)));
         logrec("proc args:   ",reg.get(regTmp0));
         if ( NIL != reg.get(regTmp0) && TYPE_CELL != type(reg.get(regTmp0))  )
         {
            raiseError(ERR_SEMANTIC);  // must have at least 2 args
            break;
         }
         reg.set(regTmp1,  cdr(reg.get(regArg0)));
         logrec("proc body:   ",reg.get(regTmp1));
         if ( TYPE_CELL != type(reg.get(regTmp1)) )
         {
            raiseError(ERR_SEMANTIC);  // must have at least 2 args
            break;
         }
         reg.set(regRetval,  cons(reg.get(regEnv), NIL));
         reg.set(regRetval,  cons(reg.get(regTmp1),reg.get(regRetval)));
         reg.set(regRetval,  cons(reg.get(regTmp0),reg.get(regRetval)));
         reg.set(regRetval,  cons(IS_PROCEDURE,reg.get(regRetval)));
         returnsub();
         break;

      case blk_tail_call:
         // Just returns whatever retval left behind by the
         // subroutine which continued to here.
         //
         returnsub();
         break;

      case blk_tail_call_m_cons:
         // Returns the cons of the top value on the stack with
         // regRetval.
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
            reg.set(regTmp0,    reg.get(regStack));
            reg.set(regStack,   cdr(reg.get(regStack)));
            setcdr(reg.get(regTmp0),reg.get(regRetval));
            reg.set(regRetval,  reg.get(regTmp0));
         }
         else
         {
            restore(regTmp0);
            reg.set(regTmp1,    reg.get(regRetval));
            reg.set(regRetval,  cons(reg.get(regTmp0),reg.get(regTmp1)));
         }
         returnsub();
         break;

      case blk_error:
         return internal2external(reg.get(regError));

      case blk_halt:
         // NOTE: we gosub then return, unlike elsewhere where we
         // gosub then break.  
         //
         // We want to goto sub_top to be ready for any subsequent
         // inputs, but also we want to return control to the caller
         // because there is nothing more for us to do without more
         // input.
         //
         gosub(sub_top,blk_halt);
         return ERROR_COMPLETE;

      default:
         log("bogus op: ",pp(reg.get(regPc)));
         raiseError(ERR_INTERNAL);
         break;
      }

      return ERROR_INCOMPLETE;
   }

   ////////////////////////////////////////////////////////////////////
   //
   // encoding slots
   //
   ////////////////////////////////////////////////////////////////////

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

   private static int type ( final int code )
   {
      return MASK_TYPE  & code;
   }
   private static int value ( final int code )
   {
      return MASK_VALUE & code;
   }
   private static int value_fixint ( final int code )
   {
      return ((MASK_VALUE & code) << (32-SHIFT_TYPE)) >> (32-SHIFT_TYPE);
   }
   private static int code ( final int type, final int value )
   {
      return (MASK_TYPE & type) | (MASK_VALUE & value);
   }

   private static final int TYPE_IOBUF    = 0x10000000;
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
   // No: my thinking now is we need to be able to suspend processing
   // when there is no io but the ports are unclosed.

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

   private static final int regFreeCells        =   0; // unused cells

   private static final int regStack            =   1; // the runtime stack
   private static final int regPc               =   2; // opcode to return to

   private static final int regError            =   3; // NIL or an ERR_foo
   private static final int regErrorPc          =   4; // regPc at err
   private static final int regErrorStack       =   5; // regStack at err

   private static final int regEnv              =   6; // list of env frames

   private static final int regIn               =   7; // input port
   private static final int regOut              =   8; // output port

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

   private static final int regHeapTop          =  31; // alloc support: int

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

   private static final int sub_init             = TYPE_SUBP | A0 |  0x1000;
   private static final int sub_prebind          = TYPE_SUBP | A1 |  0x1100;
   private static final int sub_top              = TYPE_SUBP | A0 |  0x1200;

   private static final int sub_readv            = TYPE_SUBP | AX |  0x2000;
   private static final int sub_read             = TYPE_SUBP | AX |  0x2010;
   private static final int sub_read_list        = TYPE_SUBP | A1 |  0x2100;
   private static final int sub_read_list_open   = TYPE_SUBP | A1 |  0x2110;
   private static final int sub_read_atom        = TYPE_SUBP | A1 |  0x2200;
   private static final int sub_read_num         = TYPE_SUBP | A1 |  0x2300;
   private static final int sub_read_num_loop    = TYPE_SUBP | A2 |  0x2310;
   private static final int sub_read_octo_tok    = TYPE_SUBP | A1 |  0x2400;
   private static final int sub_read_symbol      = TYPE_SUBP | A1 |  0x2500;
   private static final int sub_read_string      = TYPE_SUBP | A1 |  0x2600;
   private static final int sub_read_symbol_body = TYPE_SUBP | A1 |  0x2700;
   private static final int sub_read_string_body = TYPE_SUBP | A1 |  0x2800;
   private static final int sub_read_burn_space  = TYPE_SUBP | A1 |  0x2900;

   private static final int sub_eval             = TYPE_SUBS | A2 |  0x3000;
   private static final int sub_eval_look_env    = TYPE_SUBS | A2 |  0x3100;
   private static final int sub_eval_look_frame  = TYPE_SUBS | A2 |  0x3110;
   private static final int sub_eval_list        = TYPE_SUBS | A2 |  0x3200;

   private static final int sub_apply            = TYPE_SUBP | A2 |  0x4000;
   private static final int sub_apply_builtin    = TYPE_SUBP | A2 |  0x4100;
   private static final int sub_apply_user       = TYPE_SUBP | A2 |  0x4200;

   private static final int sub_printv           = TYPE_SUBP | AX |  0x5000;
   private static final int sub_print            = TYPE_SUBP | A2 |  0x5010;
   private static final int sub_print_list       = TYPE_SUBP | A2 |  0x5100;
   private static final int sub_print_list_elems = TYPE_SUBP | A2 |  0x5200;
   private static final int sub_print_string     = TYPE_SUBP | A2 |  0x5300;
   private static final int sub_print_chars      = TYPE_SUBP | A2 |  0x5400;
   private static final int sub_print_const      = TYPE_SUBP | A3 |  0x5500;
   private static final int sub_print_char       = TYPE_SUBP | A2 |  0x5600;
   private static final int sub_print_fixint     = TYPE_SUBP | A2 |  0x5700;

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

   private static final int sub_map1             = TYPE_SUBP | A2 |  0x8000;

   private static final int sub_const_symbol     = TYPE_SUBP | A1 |  0x9000;
   private static final int sub_const_chars      = TYPE_SUBP | A1 |  0x9100;
   private static final int sub_const_val        = TYPE_SUBP | A1 |  0x9200;

   private static final int blk_tail_call        = TYPE_SUBP | A0 | 0x10001;
   private static final int blk_tail_call_m_cons = TYPE_SUBP | A0 | 0x10002;
   private static final int blk_error            = TYPE_SUBP | A0 | 0x10003;
   private static final int blk_halt             = TYPE_SUBP | A0 | 0x10004;

   // A few tables of named constants would really help with
   // implementing a clean bootstrap.
   //
   // We let Java construct it for us at class-load time: but some
   // day, we may need a linker. ;)
   //
   // Note, constTableSize is the size we allocate the arrays in Java,
   // but not necessarily the number of valid records.
   //
   private static final int      constTableSize = 50;
   private static final String[] const_str      = new String[constTableSize];
   private static final int[]    const_val      = new int[constTableSize];
   private static final int      primitives_start;
   private static final int      primitives_end;
   private static final int      const_true;
   private static final int      const_false;
   private static final int      const_newline;
   private static final int      const_prechar;
   private static final int      const_space;
   private static final int      const_huh3;
   private static final int      const_huhPhuh;
   static 
   {
      int i = 0;
      primitives_start = i;
      const_val[i] = sub_add;     const_str[i++] = "+";
      const_val[i] = sub_mul;     const_str[i++] = "*";
      const_val[i] = sub_sub;     const_str[i++] = "-";
      const_val[i] = sub_lt_p;    const_str[i++] = "<";
      const_val[i] = sub_add0;    const_str[i++] = "+0";
      const_val[i] = sub_add1;    const_str[i++] = "+1";
      const_val[i] = sub_add3;    const_str[i++] = "+3";
      const_val[i] = sub_cons;    const_str[i++] = "cons";
      const_val[i] = sub_car;     const_str[i++] = "car";
      const_val[i] = sub_cdr;     const_str[i++] = "cdr";
      const_val[i] = sub_list;    const_str[i++] = "list";
      const_val[i] = sub_if;      const_str[i++] = "if";
      const_val[i] = sub_quote;   const_str[i++] = "quote";
      const_val[i] = sub_define;  const_str[i++] = "define";
      const_val[i] = sub_lambda;  const_str[i++] = "lambda";
      const_val[i] = sub_equal_p; const_str[i++] = "equal?";
      const_val[i] = sub_let;     const_str[i++] = "let";
      const_val[i] = sub_begin;   const_str[i++] = "begin";
      const_val[i] = sub_cond;    const_str[i++] = "cond";
      const_val[i] = sub_case;    const_str[i++] = "case";
      const_val[i] = sub_readv;   const_str[i++] = "read";
      const_val[i] = sub_printv;  const_str[i++] = "display";
      const_val[i] = sub_map1;    const_str[i++] = "map1";
      primitives_end = i;

      const_true    = code(TYPE_FIXINT,i);
      const_val[i]  = TRUE;                 const_str[i++] = "#t";

      const_false   = code(TYPE_FIXINT,i);
      const_val[i]  = FALSE;                const_str[i++] = "#f";

      const_newline = code(TYPE_FIXINT,i);
      const_val[i]  = UNSPECIFIED;          const_str[i++] = "newline";

      const_prechar = code(TYPE_FIXINT,i);
      const_val[i]  = UNSPECIFIED;          const_str[i++] = "#\\";

      const_space   = code(TYPE_FIXINT,i);
      const_val[i]  = UNSPECIFIED;          const_str[i++] = "space";

      const_huh3    = code(TYPE_FIXINT,i);
      const_val[i]  = UNSPECIFIED;          const_str[i++] = "???";

      const_huhPhuh = code(TYPE_FIXINT,i);
      const_val[i]  = UNSPECIFIED;          const_str[i++] = "?p?";
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
      final Mem reg  = mach.reg;
      final Mem heap = mach.heap;
      if ( PROFILE )
      {
         local.numCons++;
         global.numCons++;
      }
      int cell = reg.get(regFreeCells);
      if ( NIL == cell )
      {
         int heapTop = value_fixint(reg.get(regHeapTop));
         if ( heapTop >= heap.length() )
         {
            raiseError(ERR_OOM);
            return UNSPECIFIED;
         }
         final int top;
         if ( DEFER_HEAP_INIT )
         {
            // reg.get(regHeapTop) in slots, 2* for cells, only init a
            // piece of it this pass.
            top = reg.get(regHeapTop) + 2*256;
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
            top = heap.length();
         }
         final int lim = (top < heap.length()) ? top : heap.length();
         for ( ; heapTop < lim; heapTop += 2 )
         {
            // Notice that how half the space in the free cell list,
            // the car()s, is unused.  Is this an opportunity for
            // something?
            heap.set(heapTop + 0, UNSPECIFIED);
            heap.set(heapTop + 1, reg.get(regFreeCells));
            reg.set(regFreeCells,  code(TYPE_CELL,(heapTop >>> 1)));
         }
         reg.set(regHeapTop, code(TYPE_FIXINT,heapTop));
         cell = reg.get(regFreeCells);
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
      reg.set(regFreeCells,  heap.get(dr));
      heap.set(ar, car);
      heap.set(dr, cdr);
      return cell;
   }

   private int car ( final int cell )
   {
      final Mem heap = mach.heap;
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return UNSPECIFIED;
      }
      return heap.get((value(cell) << 1) + 0);
   }

   private int cdr ( final int cell )
   {
      final Mem heap = mach.heap;
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return UNSPECIFIED;
      }
      return heap.get((value(cell) << 1) + 1);
   }

   private void setcar ( final int cell, final int value )
   {
      final Mem heap = mach.heap;
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      heap.set( (value(cell) << 1) + 0,  value );
   }

   private void setcdr ( final int cell, final int value )
   {
      final Mem heap = mach.heap;
      if ( DEBUG && TYPE_CELL != type(cell) )
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      heap.set( (value(cell) << 1) + 1,  value );
   }

   ////////////////////////////////////////////////////////////////////
   //
   // encoding ports
   //
   ////////////////////////////////////////////////////////////////////

   /**
    * Pushes value, which must be a TYPE_CHAR, onto the back of the
    * output port specified in reg.get(regPort).
    * 
    * Leaves the port unchanged in the event of any error.
    */
   private void portPush ( final int regPort, final int value )
   {
      final boolean verb = false;
      final int port = mach.reg.get(regPort);
      if ( DEBUG && TYPE_IOBUF != type(port) ) 
      {
         log("  portPush(): non-iobuf ",pp(port));
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( DEBUG && TYPE_CHAR != type(value) ) 
      {
         log("  portPush(): non-char ",pp(value));
         raiseError(ERR_INTERNAL);
         return;
      }
      final IOBuffer[] iobufs = mach.iobufs;
      if ( DEBUG && ( 0 > value(port) || value(port) >= iobufs.length ) )
      {
         log("  portPush(): non-iobuf ",pp(port));
         raiseError(ERR_INTERNAL);
         return;
      }
      final IOBuffer iobuf = iobufs[value(port)];
      log("  portPush(): iobuf: ",iobuf);
      //
      // TODO: for now, an iobuf is EOF if empty, but later when we
      // add close() it'll be EOF when null, and an empty buffer
      // means we need to suspend processing.
      //
      // So we check for both conditions here.  Later on, it'll be
      // the isFull() clause which changes, not the null clause.
      //
      if ( null == iobuf )
      {
         log("  portPush(): closed");
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( iobuf.isFull() )
      {
         // TODO: suspend
         log("  portPush(): full");
         raiseError(ERR_NOT_IMPL);
         return;
      }
      log("  portPush(): value:  ",pp(value));
      final int decode = value(value);
      log("  portPush(): decode: ",decode);
      iobuf.push((byte)decode);
      return;
   }

   /**
    * Removes the object at the front of the port (in which case the
    * port is mutated to remove the object).
    */
   private void portPop ( final int port )
   {
      final boolean verb = false;
      if ( DEBUG && TYPE_IOBUF != type(port) ) 
      {
         log("  portPop(): non-iobuf ",pp(port));
         raiseError(ERR_INTERNAL);
         return;
      }
      final IOBuffer[] iobufs = mach.iobufs;
      if ( DEBUG && ( 0 > value(port) || value(port) >= iobufs.length ) )
      {
         log("  portPop(): non-iobuf ",pp(port));
         raiseError(ERR_INTERNAL);
         return;
      }
      final IOBuffer iobuf = iobufs[value(port)];
      //
      // TODO: for now, an iobuf is EOF if empty, but later when we
      // add close() it'll be EOF when null, and an empty buffer
      // means we need to suspend processing.
      //
      // So we check for both conditions here.  Later on, it'll be
      // the isEmpty() clause which changes, not the null clause.
      //
      if ( null == iobuf )
      {
         log("  portPop(): closed");
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( iobuf.isEmpty() )
      {
         // TODO: suspend
         log("  portPop(): empty");
         raiseError(ERR_INTERNAL);
         return;
      }
      log("  portPop(): popping");
      iobuf.pop();
      return;
   }

   /**
    * @returns the object at the front of the port, or EOF if empty
    */
   private int portPeek ( final int port )
   {
      final boolean verb = false;
      if ( DEBUG && TYPE_IOBUF != type(port) ) 
      {
         log("  portPeek(): non-iobuf ",pp(port));
         raiseError(ERR_INTERNAL);
         return EOF;
      }
      final IOBuffer[] iobufs = mach.iobufs;
      if ( DEBUG && ( 0 > value(port) || value(port) >= iobufs.length ) )
      {
         log("  portPeek(): non-iobuf ",pp(port));
         raiseError(ERR_INTERNAL);
         return EOF;
      }
      final IOBuffer iobuf = iobufs[value(port)];
      log("  portPeek(): iobuf ",iobuf);
      //
      // TODO: for now, an iobuf is EOF if empty, but later when we
      // add close() it'll be EOF when null, and an empty buffer
      // means we need to suspend processing.
      //
      // So we check for both conditions here.  Later on, it'll be
      // the isEmpty() clause which changes, not the null clause.
      //
      if ( null == iobuf )
      {
         log("  portPeek(): closed");
         return EOF;
      }
      if ( iobuf.isEmpty() )
      {
         // TODO: suspend
         log("  portPeek(): empty");
         return EOF;
      }
      final int value = iobuf.peek();
      log("  portPeek(): value: ",value);
      log("  portPeek(): value: ",(char)value);
      final int code  = code(TYPE_CHAR,value);
      log("  portPeek(): code:  ",pp(code));
      return code;
   }
      

   ////////////////////////////////////////////////////////////////////
   //
   // encoding the runtime stack
   //
   ////////////////////////////////////////////////////////////////////

   /** 
    * Pushes the value in the register regId onto the top of the stack
    * at regStack.
    */
   private void store ( final int regId )
   {
      final Mem reg = mach.reg;
      if ( NIL != reg.get(regError) )
      {
         log("store(): flow suspended for error");
         return;
      }
      final int value = reg.get(regId);
      final int cell  = cons(value,reg.get(regStack));
      if ( NIL == cell )
      {
         // error already raised in cons()
         log("store(): oom");
         return;
      }
      log("stored:   ",pp(value));
      reg.set(regStack,  cell);
   }

   /** 
    * Pops the top value on the stack at regStack and stores it in the
    * register regId.
    */
   private void restore ( final int regId )
   {
      final Mem reg = mach.reg;
      if ( NIL != reg.get(regError) )
      {
         log("restore(): flow suspended for error");
         return;
      }
      if ( DEBUG && NIL == reg.get(regStack) )
      {
         log("restore(): stack underflow");
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( DEBUG && TYPE_CELL != type(reg.get(regStack)) )
      {
         log("restore(): corrupt stack");
         raiseError(ERR_INTERNAL);
         return;
      }
      final int cell  = reg.get(regStack);
      final int value = car(cell);
      reg.set(regId,    car(cell));
      reg.set(regStack, cdr(cell));
      log("restored: ",pp(value));
      if ( CLEVER_STACK_RECYCLING && NIL == reg.get(regError) )
      {
         // Recycle stack cell which is unreachable from user code.
         //
         // That assertion is only true and this is only a valid
         // optimization if we haven't stashed the continuation
         // someplace.  
         //
         // This optimization may not be sustainable in the
         // medium-term.
         setcdr(cell, reg.get(regFreeCells));
         reg.set(regFreeCells, cell);
      }
   }

   ////////////////////////////////////////////////////////////////////
   //
   // encoding subroutine discipline and error trapping
   //
   ////////////////////////////////////////////////////////////////////

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
      final Mem reg = mach.reg;
      final boolean verb = false;
      //log("  gosub()");
      //log("    old stack: ",reg.get(regStack));
      if ( DEBUG )
      {
         final int tn = type(nextOp);
         if ( TYPE_SUBP != tn && TYPE_SUBS != tn )
         {
            //log("    non-op: ",pp(nextOp));
            raiseError(ERR_INTERNAL);
            return;
         }
         if ( 0 != ( MASK_BLOCKID & nextOp ) )
         {
            //log("    non-sub: ",pp(nextOp));
            raiseError(ERR_INTERNAL);
            return;
         }
         final int tc = type(continuationOp);
         if ( TYPE_SUBP != tc && TYPE_SUBS != tc )
         {
            //log("    non-op: ",pp(continuationOp));
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
            //log("    full-sub: ",pp(continuationOp));
            raiseError(ERR_INTERNAL);
            return;
         }
      }
      if ( NIL != reg.get(regError) )
      {
         //log("    flow suspended for error: ",pp(reg.get(regError)));
         return;
      }
      if ( PROPERLY_TAIL_RECURSIVE && blk_tail_call == continuationOp )
      {
         // Tail recursion is so cool.
      }
      else
      {
         reg.set(regTmp0,continuationOp);
         store(regTmp0);
         if ( NIL != reg.get(regError) )
         {
            // error already reported in store()
            return;
         }
         if ( DEBUG ) scmDepth++;
      }
      reg.set(regPc,  nextOp);
   }

   private void returnsub ()
   {
      final Mem reg = mach.reg;
      if ( DEBUG )
      {
         scmDepth--;
         if ( 0 > scmDepth )
         {
            throw new RuntimeException("scmDepth underflow: " + scmDepth);
         }
      }
      restore(regPc);
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
      final Mem reg = mach.reg;
      log("raiseError():");
      log("  err:   ",pp(err));
      log("  pc:    ",pp(reg.get(regPc)));
      log("  stack: ",pp(reg.get(regStack)));
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
            log("  java:  ",elm);
         }
         for ( int c = reg.get(regStack); NIL != c; c = cdr(c) )
         {
            // TODO: protect against corrupt cyclic stack
            log("  scm:   ",pp(car(c)));
         }
      }
      if ( NIL == reg.get(regError) ) 
      {
         log("  first: documenting");
         reg.set(regError,       err);
         reg.set(regErrorPc,     reg.get(regPc));
         reg.set(regErrorStack,  reg.get(regStack));
      }
      else
      {
         log("  late:  supressing");
      }
      reg.set(regPc,     blk_error);
      reg.set(regStack,  NIL);
      if ( true && err == ERR_INTERNAL )
      {
         throw new RuntimeException("detonate on ERR_INTERNAL");
      }
      if ( true && err == ERR_NOT_IMPL )
      {
         throw new RuntimeException("detonate on ERR_NOT_IMPL");
      }
   }

   /**
    * Restores the continuation to where it was at time of last error,
    * and clears the VM's error state.
    */
   private void resumeErrorContinuation ()
   {
      final Mem reg = mach.reg;
      reg.set(regError,  NIL);
      reg.set(regPc,     reg.get(regErrorPc));
      reg.set(regStack,  reg.get(regErrorStack));
   }

   /**
    * Translating internally visible error codes into externally
    * visible ones.
    * 
    * TODO: why two sets of error codes?  I don't know yet, but I
    * think there is a reason.
    */
   private static int internal2external ( final int err )
   {
      switch ( err )
      {
      case ERR_OOM:       return ERROR_OUT_OF_MEMORY;
      case ERR_LEXICAL:   return ERROR_FAILURE_LEXICAL;
      case ERR_SEMANTIC:  return ERROR_FAILURE_SEMANTIC;
      case ERR_NOT_IMPL:  return ERROR_UNIMPLEMENTED;
      default:            return ERROR_INTERNAL_ERROR;
      }
   }

   ////////////////////////////////////////////////////////////////////
   //
   // logging and debug utilities
   //
   ////////////////////////////////////////////////////////////////////

   // scmDepth and javaDepth are ONLY used for debug: they are *not*
   // sanctioned VM state.
   //
   private void log ( final Object... msgs )
   {
      if ( !VERBOSE ) return;
      final int lim = (scmDepth + javaDepth);
      for (int i = 0; i < lim; ++i)
      {
         System.out.print("  ");
      }
      for ( final Object msg : msgs )
      {
         System.out.print(msg);
      }
      System.out.println();
   }

   private void logrec ( String tag, int c )
   {
      if ( !VERBOSE ) return;
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
            log(tag,pp(first)," ",buf);
            break;
         case IS_PROCEDURE:
         case IS_SPECIAL_FORM:
            log(tag,pp(first));
            tag += " ";
            logrec(tag,car(cdr(c))); // just show the arg list, env might cycle
            break;
         default:
            log(tag,pp(c));
            tag += " ";
            logrec(tag,car(c));
            logrec(tag,cdr(c));
            break;
         }
      }
      else
      {
         log(tag,pp(c));
      }
   }

   private String pp ( final int code )
   {
      if ( !VERBOSE ) return "";
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
      case blk_halt:             return "blk_halt";
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
      case TYPE_IOBUF:    buf.append("iobuf"); break;
      case TYPE_SUBP:
      case TYPE_SUBS:
         switch (code & ~MASK_BLOCKID)
         {
         case sub_init:             buf.append("sub_init");             break;
         case sub_prebind:          buf.append("sub_prebind");          break;
         case sub_top:              buf.append("sub_top");              break;
         case sub_readv:            buf.append("sub_readv");            break;
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
         case sub_printv:           buf.append("sub_printv");           break;
         case sub_print:            buf.append("sub_print");            break;
         case sub_print_list:       buf.append("sub_print_list");       break;
         case sub_print_list_elems: buf.append("sub_print_list_elems"); break;
         case sub_print_string:     buf.append("sub_print_string");     break;
         case sub_print_chars:      buf.append("sub_print_chars");      break;
         case sub_print_const:      buf.append("sub_print_const");      break;
         case sub_print_char:       buf.append("sub_print_char");       break;
         case sub_print_fixint:     buf.append("sub_print_fixint");     break;
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
         case sub_map1:             buf.append("sub_map1");             break;
         case sub_const_symbol:     buf.append("sub_const_symbol");     break;
         case sub_const_chars:      buf.append("sub_const_chars");      break;
         case sub_const_val:        buf.append("sub_const_val");        break;
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
         if ( true ) throw new RuntimeException("WFT A");
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
}
