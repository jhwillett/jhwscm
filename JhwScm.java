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
   public static final boolean DEBUG          = true;

   public static final boolean verbose        = false;

   public static final int     SUCCESS        = 0;
   public static final int     INCOMPLETE     = 1000;
   public static final int     BAD_ARG        = 2000; // often + specifier
   public static final int     OUT_OF_MEMORY  = 3000; // often + specifier
   public static final int     FAILURE        = 4000; // often + specifier
   public static final int     UNIMPLEMENTED  = 5000; // often + specifier
   public static final int     INTERNAL_ERROR = 6000; // often + specifier

   public JhwScm ()
   {
      log("JhwScm.JhwScm()");
      for ( int i = 0; i < reg.length; i++ )
      {
         reg[i] = NIL;
      }

      reg[regFreeCellList] = NIL;
      for ( int i = 0; i < heap.length; i += 2 )
      {
         // TODO: is the car of free list, e.g. heap[i+0], superfluous here?
         heap[i+0]            = NIL;
         heap[i+1]            = reg[regFreeCellList];
         reg[regFreeCellList] = code(TYPE_CELL,(i >>> 1));
      }

      reg[regPc] = sub_rep;

      reg[regIn]  = queueCreate();
      reg[regOut] = queueCreate();
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
      if ( DEBUG ) javaDepth = 0;
      if ( null == input )
      {
         log("input():  null arg");
         return BAD_ARG;
      }
      log("input():  \"" + input + "\"");
      // TODO: this method is horribly inefficient :(
      //
      // Should reconsider our goal of leaving the input buffer
      // unchanged, or should do a more clever cache-the-tail-cell to
      // recover from failure in O(1).  In any case, we shouldn't have
      // to walk all the input twice here, once into the temp queue
      // and once in queueSpliceBack().
      //
      final int tmpQueue = queueCreate();
      if ( NIL == tmpQueue )
      {
         return OUT_OF_MEMORY + 1;
      }
      for ( int i = 0; i < input.length(); ++i )
      {
         final char c    = input.charAt(i);
         final int  code = code(TYPE_CHAR,c);
         queuePushBack(tmpQueue,code);
      }
      if ( NIL == reg[regIn] )
      {
         raiseError(ERR_INTERNAL);
         return INTERNAL_ERROR;
      }
      queueSpliceBack(reg[regIn],car(tmpQueue));
      // TODO: could recycle the cell at tmpQueue here.
      //
      // TODO: check did we get in an error state before reporting
      // success?
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
      if ( DEBUG ) javaDepth = 0;
      if ( null == output )
      {
         log("output(): null arg");
         return BAD_ARG;
      }
      if ( NIL == reg[regOut] )
      {
         raiseError(ERR_INTERNAL);
         return INTERNAL_ERROR;
      }
      for ( int f = 0; EOF != ( f = queuePeekFront(reg[regOut]) ); /*below*/ )
      {
         final int  v = value(f);
         final char c = (char)(MASK_VALUE & v);
         // TODO: change signature so we don't need this guard here?
         // 
         // TODO: make this all-or-nothing, like input()?
         try
         {
            output.append(c);
         }
         catch ( Throwable e )
         {
            return FAILURE;
         }
         queuePopFront(reg[regOut]);
      }
      log("output(): \"" + output + "\"");
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
      if ( DEBUG ) javaDepth = 0;
      log("drive():");
      if ( DEBUG ) javaDepth = 1;
      log("numSteps: " + numSteps);

      if ( numSteps < -1 )
      {
         return BAD_ARG;
      }

      // Temp variables: note, any block can overwrite any of these.
      // Any data which must survive a block transition should be
      // saved in registers and on the stack instead.
      //
      int c    = 0;
      int t    = 0;
      int v    = 0;
      int c0   = 0;
      int t0   = 0;
      int v0   = 0;
      int c1   = 0;
      int t1   = 0;
      int v1   = 0;
      int tmp  = 0;
      int tmp1 = 0;
      int tmp2 = 0;
      int err  = SUCCESS;

      for ( int step = 0; -1 == numSteps || step < numSteps; ++step )
      {
         log("step: " + pp(reg[regPc]));
         if ( DEBUG ) javaDepth = 2;
         switch ( reg[regPc] )
         {
         case sub_rep:
            // Reads the next sexpr from reg[regIn], evaluates it, and
            // prints the result in reg[regOut].
            //
            // Top-level entry point for the interactive interpreter.
            //
            gosub(sub_read,sub_rep+0x1);
            break;
         case sub_rep+0x1:
            if ( EOF == reg[regRetval] )
            {
               return SUCCESS;
            }
            reg[regArg0] = reg[regRetval];
            reg[regArg1] = reg[regGlobalEnv];
            gosub(sub_eval,sub_rep+0x2);
            break;
         case sub_rep+0x2:
            reg[regArg0] = reg[regRetval];
            //
            // Note: we could probably tighten up rep by just jumping
            // back to sub_rep on return from sub_print.
            //
            // But that would count as a specialized form of
            // tail-recursion - not even regular tail recursion, and
            // it might have funny stack-depth-tracking consequences.
            //
            // Going with the extra blk for now to take it easy on the
            // subtlety.
            //
            gosub(sub_print,sub_rep+0x3);
            break;
         case sub_rep+0x3:
            //
            // Note: two choices here: we could do regular tail
            // recursion, or implement as a loop with jump.  Both
            // logically equivalent, but the tail recursion is much
            // less efficient unless we have efficient tail recursion
            // :) - but it would be an interesting experiment to see
            // if we could eliminate the jump() operator altogether in
            // that case.
            //
            // Also, the tail recursion decision would expose certain
            // uncomfortable questions about what the return value of
            // (print) is.  So for now, we just jump().
            //
            jump(sub_rep);
            break;

         case sub_read:
            // Parses the next sexpr from reg[regIn], and
            // leaves the results in reg[regRetval].
            //
            // Top-level entry point for the parser.
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
            c = queuePeekFront(reg[regIn]);
            t = type(c);
            v = value(c);
            if ( EOF == c )
            {
               reg[regRetval] = EOF;
               returnsub();
               break;
            }
            if ( DEBUG && TYPE_CHAR != t )
            {
               log("non-char in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (v)
            {
            case ' ':
            case '\t':
            case '\r':
            case '\n':
               queuePopFront(reg[regIn]);
               jump(sub_read); // or we could self-tail-recurse here
               break;
            case '(':
               gosub(sub_read_list,blk_re_return);
               break;
            case '\'':
               gosub(sub_read,sub_read+0x1);
               break;
            case '\"':
               raiseError(ERR_NOT_IMPL);
               break;
            default:
               gosub(sub_read_token,blk_re_return);
               break;
            }
            break;
         case sub_read+0x1:
            // TODO: return a quotation of reg[regRetval] e.g. something like:
            //
            // reg[regRetval] = cons(reg[regRetval],NIL);
            // reg[regRetval] = cons(sub_quote,     NIL);
            // returnsub();
            //
            raiseError(ERR_NOT_IMPL);
            break;

         case sub_read_list:
            // Parses the next list of sexprs from reg[regIn], and
            // leaves the results in reg[regRetval].
            //
            // On entry, expects the next char on input to be the open
            // paren '(' which begins the list.
            //
            c = queuePeekFront(reg[regIn]);
            t = type(c);
            v = value(c);
            if ( DEBUG && TYPE_CHAR != t )
            {
               log("non-char in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            if ( DEBUG && '(' != v )
            {
               log("non-paren in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePopFront(reg[regIn]);
            gosub(sub_read,sub_read_list+0x1);
            break;
         case sub_read_list+0x1:
            c = queuePeekFront(reg[regIn]);
            t = type(c);
            v = value(c);
            if ( DEBUG && TYPE_CHAR != t )
            {
               log("non-char in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            if ( DEBUG && '(' != v )
            {
               log("non-paren in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            raiseError(ERR_NOT_IMPL);
            break;

         case sub_read_token:
            c = queuePeekFront(reg[regIn]);
            t = type(c);
            v = value(c);
            if ( DEBUG && TYPE_CHAR != t )
            {
               log("non-char in input: " + pp(c));
               raiseError(ERR_INTERNAL);
               break;
            }
            if ( '0' <= v && v <= '9' )
            {
               log("  non-negated number");
               gosub(sub_read_num,blk_re_return);
               break;
            }
            if ( '#' == v )
            {
               log("  octothorpe special");
               gosub(sub_read_boolean,blk_re_return);
               break;
            }
            if ( '-' == v )
            {
               log("  minus special case");
               // The minus sign is special.  We need to look ahead
               // *again* before we can decide whether it is part of a
               // symbol or part of a number.
               queuePopFront(reg[regIn]);
               c1 = queuePeekFront(reg[regIn]);
               t1 = type(c1);
               v1 = value(c1);
               if ( DEBUG && TYPE_CHAR != t1 )
               {
                  raiseError(ERR_INTERNAL);
                  break;
               }
               if ( '0' <= v1 && v1 <= '9' )
               {
                  log("    minus-in-negative");
                  gosub(sub_read_num,sub_read_token+0x1);
                  break;
               }
               else
               {
                  log("    minus-in-symbol");
                  queuePushBack(reg[regIn],c1);
                  gosub(sub_read_symbol,blk_re_return);
                  break;
               }
            }
            log("    symbol");
            gosub(sub_read_symbol,blk_re_return);
            break;
         case sub_read_token+0x1:
            c = reg[regRetval];
            t = type(c);
            v = value(c);
            if ( TYPE_FIXINT != t )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            log("  negating: " + pp(c));
            v = -v;
            reg[regRetval] = code(TYPE_FIXINT,v);
            log("  to:       " + pp(reg[regRetval]));
            returnsub();
            break;

         case sub_read_num:
            // Parses the next number from reg[regIn].
            //
            reg[regArg0] = code(TYPE_FIXINT,0);
            gosub(sub_read_num_loop,blk_re_return);
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
            t0 = type(c0);
            v0 = value(c0);
            if ( EOF == c0 )
            {
               log("  eof: returning " + pp(reg[regArg0]));
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            }
            if ( TYPE_CHAR != t0 )
            {
               log("  non-char in input: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            c1 = reg[regArg0];
            t1 = type(c1);
            v1 = value(c1);
            if ( TYPE_FIXINT != t1 )
            {
               log("  non-fixint in arg: " + pp(c1));
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
               log("  terminator: " + pp(c0) + " return " + pp(reg[regArg0]));
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            default:
               if ( v0 < '0' || v0 > '9' )
               {
                  log("  non-digit in input: " + pp(c0));
                  raiseError(ERR_LEXICAL);
                  break;
               }
               tmp = 10*v1 + (v0-'0');
               log("  first char: " + (char)v0);
               log("  old accum:  " +       v1);
               log("  new accum:  " +       tmp);
               queuePopFront(reg[regIn]);
               reg[regArg0] = code(TYPE_FIXINT,tmp);
               gosub(sub_read_num_loop,blk_re_return);
               break;
            }
            break;

         case sub_read_boolean:
            // Parses the next boolean literal reg[regIn].
            //
            c = queuePeekFront(reg[regIn]);
            if ( c != code(TYPE_CHAR,'#') )
            {
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePopFront(reg[regIn]);
            c0 = queuePeekFront(reg[regIn]);
            t0 = type(c0);
            v0 = value(c0);
            if ( TRUE == c0 )
            {
               log("  eof after octothorpe");
               raiseError(ERR_LEXICAL);
               break;
            }
            queuePopFront(reg[regIn]);
            if ( TYPE_CHAR != t0 )
            {
               log("  non-char in input: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            switch (v0)
            {
            case 't':
               reg[regRetval] = TRUE;
               returnsub();
               break;
            case 'f':
               reg[regRetval] = FALSE;
               returnsub();
               break;
            default:
               log("  unexpected after octothorpe: " + pp(c0));
               raiseError(ERR_LEXICAL);
               break;
            }
            break;


         case sub_read_symbol:
            // Parses the next symbol from reg[regIn].
            //
            reg[regArg0] = queueCreate();
            store(reg[regArg0]);
            gosub(sub_read_symbol_loop,sub_read_symbol+0x1);
            break;
         case sub_read_symbol+0x1:
            reg[regTmp0]   = restore();
            reg[regRetval] = cons(IS_SYMBOL,car(reg[regTmp0]));
            returnsub();
            break;

         case sub_read_symbol_loop:
            // Parses the next symbol from reg[regIn], expecting the
            // accumulated value-so-far as a queue in reg[regArg0].
            //
            // A helper for sub_read_sym, but still a sub_ in its own
            // right.
            //
            if ( DEBUG && TYPE_CELL != type(reg[regArg0]) )
            {
               log("  non-queue in arg: " + pp(reg[regArg0]));
               raiseError(ERR_INTERNAL);
               break;
            }
            c0 = queuePeekFront(reg[regIn]);
            t0 = type(c0);
            v0 = value(c0);
            if ( EOF == c0 )
            {
               reg[regRetval] = car(reg[regArg0]);
               log("  eof: returning " + pp(reg[regRetval]));
               returnsub();
               break;
            }
            if ( TYPE_CHAR != t0 )
            {
               log("  non-char in input: " + pp(c0));
               raiseError(ERR_INTERNAL);
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
               reg[regRetval] = car(reg[regArg0]);
               log("  eot, returning: " + pp(reg[regRetval]));
               returnsub();
               break;
            default:
               queuePushBack(reg[regArg0],c0);
               queuePopFront(reg[regIn]);
               log("  pushing: " + pp(c0));
               gosub(sub_read_num_loop,blk_re_return);
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
               // these types are self-evaluating
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            case TYPE_CELL:
               // TODO: this is a lot of friggin' aliasing, with
               // little point to it.
               //
               // I know I'm trying to *be* the compiler, but that
               // doesn't mean I need to be clever.
               reg[regTmp0] = car(reg[regArg0]);
               reg[regTmp1] = cdr(reg[regArg0]);
               log("  h: " + pp(reg[regTmp0]));
               log("  t: " + pp(reg[regTmp1]));
               switch (reg[regTmp0])
               {
               case IS_STRING:
                  // strings are self-evaluating
                  reg[regRetval] = reg[regArg0];
                  returnsub();
                  break;
               case IS_SYMBOL:
                  log("  going to sub_eval_lookup");
                  reg[regArg0] = reg[regArg0]; // forward the symbol
                  reg[regArg1] = reg[regArg1]; // forward the env
                  gosub(sub_eval_lookup,blk_re_return);
                  break;
               default:
                  store(reg[regTmp1]);         // store the args
                  store(reg[regArg1]);         // store the env
                  reg[regArg0] = reg[regTmp0]; // forward the op
                  reg[regArg1] = reg[regArg1]; // forward the env
                  gosub(sub_eval,sub_eval+0x1);
                  break;
               }
               break;
            default:
               log("  wtf: " + pp(reg[regArg1]));
               raiseError(ERR_INTERNAL);
               break;
            }
            break;
         case sub_eval+0x01: // following eval of the first elem
            reg[regArg1] = restore(); // restore the env
            reg[regTmp0] = restore(); // restore the rest of the expr
/*
            switch (type(reg[regRetval]))
            {
            case TYPE_SUB:
            case TYPE_FUNC:
               // we need to evaluate the arguments then apply
               store(reg[regRetval]);       // store the eval of the first elem
               reg[regArg0] = reg[regTmp0]; // forward the rest of the expr
               reg[regArg1] = reg[regArg1]; // forward the env
               gosub(sub_eval_list,sub_eval+0x02);
               break;
            case TYPE_SPECIAL:
               reg[regArg2] = reg[regArg1];   // forward the env
               reg[regArg1] = reg[regTmp0];   // forward the unevaluated args
               reg[regArg0] = reg[regRetval]; // forward the op
               gosub(sub_apply,blk_re_return);
               break;
            default:
               raiseError(ERR_INTERNAL);
               break;
            }
*/
            break;
         case sub_eval+0x02: // following eval of the rest elems
            reg[regArg0] = restore();      // restore the eval of the first
            reg[regArg1] = reg[regRetval]; // retrieve the eval of the rest
            gosub(sub_apply,blk_re_return);
            break;

         case sub_eval_list:
            // Evaluates all the expressions in the list in
            // reg[regArg0] in the env in reg[regArg1].
            //
            raiseError(ERR_NOT_IMPL);
            break;

         case sub_eval_lookup:
            // Looks up the symbol in reg[regArg0] in the env in
            // reg[regArg1].
            //
            raiseError(ERR_NOT_IMPL);
            break;

         case sub_apply:
            // Applies the op in reg[regArg0] to the args in
            // reg[regArg1].
            //
            raiseError(ERR_NOT_IMPL);
            break;

         case sub_print:
            // Prints the expr in reg[regArg0] to reg[regOut].
            //
            c = reg[regArg0];
            t = type(c);
            v = value(c);
            log("  printing: " + pp(c));
            switch (t)
            {
            case TYPE_NIL:
               gosub(sub_print_list,blk_re_return);
               break;
            case TYPE_CELL:
               // TODO: check for TYPE_SENTINEL in car(c)
               c0 = car(c);
               c1 = cdr(c);
               switch (c0)
               {
               case IS_STRING:
                  log("  IS_STRING: " + pp(c) + " " + pp(c0) + " " + pp(c1));
                  reg[regArg0] = c1;
                  gosub(sub_print_string,blk_re_return);
                  break;
               case IS_SYMBOL:
                  log("  IS_SYMBOL: " + pp(c) + " " + pp(c0) + " " + pp(c1));
                  reg[regArg0] = c1;
                  gosub(sub_print_chars,blk_re_return);
                  break;
               default:
                  log("  WHATEVER:  " + pp(c) + " " + pp(c0) + " " + pp(c1));
                  reg[regArg0] = c;
                  gosub(sub_print_list,blk_re_return);
                  break;
               }
               break;
            case TYPE_CHAR:
               queuePushBack(reg[regOut],code(TYPE_CHAR,'#'));
               queuePushBack(reg[regOut],code(TYPE_CHAR,'\\'));
               switch (v)
               {
               case ' ':
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'s'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'p'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'a'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'c'));
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'e'));
                  break;
               default:
                  queuePushBack(reg[regOut],v);
                  break;
               }
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
               v = (v << (32-SHIFT_TYPE)) >> (32-SHIFT_TYPE);
               if ( true )
               {
                  // TODO: this is a huge cop-out, implement it right
                  final String str = "" + v;
                  for ( tmp = 0; tmp < str.length(); ++tmp )
                  {
                     queuePushBack(reg[regOut],code(TYPE_CHAR,str.charAt(tmp)));
                  }
                  returnsub();
                  break;
               }
               if ( 0 == v )
               {
                  queuePushBack(reg[regOut],code(TYPE_CHAR,'0'));
               }
               else
               {
                  if ( v < 0 )
                  {
                     queuePushBack(reg[regOut],code(TYPE_CHAR,'-'));
                     v *= -1;
                  }
                  while ( v > 0 )
                  {
                     tmp1 = v;
                     tmp2 = 1;
                     while ( tmp1/10 > 0 )
                     {
                        tmp1 /= 10;
                        tmp2 *= 10;
                     }
                     queuePushBack(reg[regOut],code(TYPE_CHAR,'0'+tmp1));
                     v -= tmp1*tmp2;
                  }
               }
               returnsub();
               break;
            case TYPE_SUB:
            case TYPE_ERROR:
               raiseError(ERR_NOT_IMPL);
               break;
            case TYPE_SENTINEL:
               // TYPE_SENTINEL is used by sub_print, true, but should
               // not come up in a top-level argument to sub_print.
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
               raiseError(ERR_INTERNAL);
               break;
            }
            c0 = car(c);
            c1 = cdr(c);
            if ( TYPE_CHAR != type(c0) )
            {
               log("  bogus: " + pp(c0));
               raiseError(ERR_INTERNAL);
               break;
            }
            queuePushBack(reg[regOut],c0);
            reg[regArg0] = c1;
            jump(sub_print_chars); // TODO: jump() is icky!
            break;

         case sub_print_list:
            // Prints the list (NIL or a cell) in reg[regArg0] to
            // reg[regOut] in parens.
            //
            // TODO: UNTESTED
            queuePushBack(reg[regOut],code(TYPE_CHAR,'('));
            gosub(sub_print_list_elems,sub_print_list+0x1);
            break;
         case sub_print_list+0x1:
            //
            // TODO: UNTESTED
            queuePushBack(reg[regOut],code(TYPE_CHAR,')'));
            returnsub();
            break;

         case sub_print_list_elems:
            // Prints the elements in the list (NIL or a cell) in
            // reg[regArg0] to reg[regOut] with a space between each.
            //
            raiseError(ERR_NOT_IMPL);
            break;

         case blk_re_return:
            // Just returns whatever retval left behind by the
            // subroutine which continued to here.
            //
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
            case ERR_LEXICAL:   return FAILURE;
            case ERR_NOT_IMPL:  return UNIMPLEMENTED;
            default:            
               log("  unknown error code: " + pp(reg[regError]));
               return INTERNAL_ERROR;
            }

         default:
            log("  bogus: " + pp(reg[regPc]));
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
   private static final int TYPE_SUB      = 0x50000000;
   private static final int TYPE_ERROR    = 0x60000000;
   private static final int TYPE_BOOLEAN  = 0x70000000;
   private static final int TYPE_SENTINEL = 0x80000000;

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
   private static final int IS_SYMBOL           = TYPE_SENTINEL | 79;
   private static final int IS_STRING           = TYPE_SENTINEL | 32;

   private static final int TRUE                = TYPE_BOOLEAN  | 37;
   private static final int FALSE               = TYPE_BOOLEAN  | 91;

   private static final int ERR_OOM             = TYPE_ERROR    | 42;
   private static final int ERR_INTERNAL        = TYPE_ERROR    | 18;
   private static final int ERR_LEXICAL         = TYPE_ERROR    | 11;
   private static final int ERR_SEMANTIC        = TYPE_ERROR    |  7;
   private static final int ERR_NOT_IMPL        = TYPE_ERROR    | 87;

   private static final int regFreeCellList     =  0; // unused cells

   private static final int regStack            =  1; // the runtime stack
   private static final int regPc               =  2; // opcode to return to

   private static final int regError            =  3; // NIL or a TYPE_ERROR
   private static final int regErrorPc          =  4; // reg[regPc] of err
   private static final int regErrorStack       =  5; // reg[regStack] of err

   private static final int regIn               =  6; // input char queue
   private static final int regOut              =  7; // output char queue

   private static final int regArg0             =  8; // argument
   private static final int regArg1             =  9; // argument
   private static final int reg__Unused         = 10; // 
   private static final int regTmp0             = 11; // temporary
   private static final int regTmp1             = 12; // temporary
   private static final int regRetval           = 13; // return value

   private static final int regGlobalEnv        = 14; // list of env frames

   private static final int numRegisters        = 16;  // in slots
   private static final int heapSize            = 512; // in cells

   private final int[] heap = new int[2*heapSize];
   private final int[] reg  = new int[numRegisters];

   // With opcodes, proper subroutines entry points (entry points
   // which can be expected to follow stack discipline and balance)
   // get names, and must be a multiple of 0x10.
   //
   // Helper opcodes do not get a name: they use their parent's name
   // plus 0x0..0xF.
   //
   // An exception to the naming policy is blk_re_return and
   // blk_error.  These are not proper subroutines, but instead they
   // are utility blocks used from many places.

   private static final int MASK_BLOCKID         =                0xF;

   private static final int sub_rep              = TYPE_SUB |  0x1000;

   private static final int sub_read             = TYPE_SUB |  0x2000;
   private static final int sub_read_list        = TYPE_SUB |  0x2100;
   private static final int sub_read_token       = TYPE_SUB |  0x2200;
   private static final int sub_read_num         = TYPE_SUB |  0x2300;
   private static final int sub_read_num_loop    = TYPE_SUB |  0x2310;
   private static final int sub_read_boolean     = TYPE_SUB |  0x2400;
   private static final int sub_read_symbol      = TYPE_SUB |  0x2500;
   private static final int sub_read_symbol_loop = TYPE_SUB |  0x2600;

   private static final int sub_eval             = TYPE_SUB |  0x3000;
   private static final int sub_eval_lookup      = TYPE_SUB |  0x3100;
   private static final int sub_eval_list        = TYPE_SUB |  0x3200;

   private static final int sub_apply            = TYPE_SUB |  0x4000;

   private static final int sub_print            = TYPE_SUB |  0x5000;
   private static final int sub_print_list       = TYPE_SUB |  0x5100;
   private static final int sub_print_list_elems = TYPE_SUB |  0x5200;
   private static final int sub_print_string     = TYPE_SUB |  0x5300;
   private static final int sub_print_chars      = TYPE_SUB |  0x5400;

   private static final int blk_re_return        = TYPE_SUB | 0x10001;
   private static final int blk_error            = TYPE_SUB | 0x10002;

   private void jump ( final int nextOp )
   {
      if ( DEBUG )
      {
         final int t = type(nextOp);
         if ( TYPE_SUB != t )
         {
            raiseError(ERR_INTERNAL);
            return;
         }
      }
      if ( NIL != reg[regError] )
      {
         if ( verbose ) log("    flow suspended for error: " + reg[regError]);
         return;
      }
      reg[regPc] = nextOp;
   }

   private void gosub ( final int nextOp, final int continuationOp )
   {
      final boolean verbose = false;
      if ( verbose ) log("  gosub()");
      if ( verbose ) log("    old stack: " + reg[regStack]);
      if ( DEBUG )
      {
         if ( TYPE_SUB != type(nextOp) )
         {
            if ( verbose ) log("    non-op: " + pp(nextOp) + " w/ type " + type(nextOp));
            raiseError(ERR_INTERNAL);
            return;
         }
         if ( 0 != ( MASK_BLOCKID & nextOp ) )
         {
            if ( verbose ) log("    non-sub: " + pp(nextOp) + " " + ( MASK_BLOCKID & nextOp ));
            raiseError(ERR_INTERNAL);
            return;
         }
         if ( TYPE_SUB != type(continuationOp) )
         {
            if ( verbose ) log("    non-op: " + pp(continuationOp));
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
            if ( verbose ) log("    full-sub: " + pp(continuationOp));
            raiseError(ERR_INTERNAL);
            return;
         }
      }
      if ( NIL != reg[regError] )
      {
         if ( verbose ) log("    flow suspended for error: " + reg[regError]);
         return;
      }
      store(continuationOp);
      if ( NIL != reg[regError] )
      {
         // error already reported in store()
         return;
      }
      reg[regPc] = nextOp;
      if ( DEBUG ) scmDepth++;
   }

   private void returnsub ()
   {
      if ( DEBUG ) scmDepth--;
      final int c = restore();
      final int t = type(c);
      if ( TYPE_SUB != t )
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      reg[regPc] = c;
   }

   private void store ( final int value )
   {
      final boolean verbose = true;
      if ( NIL != reg[regError] )
      {
         if ( verbose ) log("store(): flow suspended for error");
         return;
      }
      final int cell = cons(value,reg[regStack]);
      if ( NIL == cell )
      {
         // error already raised in cons()
         if ( verbose ) log("store(): oom");
         return;
      }
      if ( verbose ) log("stored:   " + pp(value));
      reg[regStack] = cell;
   }

   private int restore ()
   {
      final boolean verbose = true;
      if ( NIL != reg[regError] )
      {
         if ( verbose ) log("restore(): flow suspended for error");
         return NIL; // TODO: don't like this use of NIL
      }
      if ( DEBUG && NIL == reg[regStack] )
      {
         if ( verbose ) log("restore(): stack underflow");
         raiseError(ERR_INTERNAL);
         return NIL; // TODO: don't like this use of NIL
      }
      if ( DEBUG && TYPE_CELL != type(reg[regStack]) )
      {
         if ( verbose ) log("restore(): corrupt stack");
         raiseError(ERR_INTERNAL);
         return NIL; // TODO: don't like this use of NIL
      }
      final int cell = reg[regStack];
      final int head = car(cell);
      final int rest = cdr(cell);
      // TODO: Recycle cell, at least, if we haven't ended up in an
      // error state or are otherwise "holding" old stacks?
      reg[regStack]  = rest;
      if ( verbose ) log("restored: " + pp(head));
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
      final boolean verbose = true;
      if ( verbose )
      {
         log("  raiseError():");
      }
      if ( DEBUG && TYPE_ERROR != type(err) )
      {
         // TODO: Bad call to raiseError()? Are we out of tricks?
      }
      if ( verbose )
      {
         log("    err:   " + pp(err));
         log("    pc:    " + pp(reg[regPc]));
         log("    stack: " + pp(reg[regStack]));
      }
      if ( verbose )
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
            log("    java:  " + elm);
         }
         for ( int c = reg[regStack]; NIL != c; c = cdr(c) )
         {
            // TODO: hopefully the stack isn't corrupt....
            log("    scm:   " + pp(car(c)));
         }
      }
      if ( NIL == reg[regError] ) 
      {
         if ( verbose )
         {
            log("    primary: documenting");
         }
         reg[regError]      = err;
         reg[regErrorPc]    = reg[regPc];
         reg[regErrorStack] = reg[regStack];
      }
      else
      {
         if ( verbose )
         {
            log("    secondary: supressing");
         }
      }
      reg[regPc]    = blk_error;
      reg[regStack] = NIL;
   }

   /**
    * Checks that the VM is internally consistent, that all internal
    * invariants are still true.
    *
    * @returns SUCCESS on success, else (FAILURE+code)
    */
   public int selfTest ()
   {
      if ( verbose ) log("selfTest()");

      // consistency check
      final int t = 0x12345678 & TYPE_FIXINT;
      final int v = 0x12345678 & MASK_VALUE;
      final int c = code(t,v);
      if ( t != type(c) )
      {
         return FAILURE + 1;
      }
      if ( v != value(c) )
      {
         return FAILURE + 2;
      }

      final int numFree      = listLength(reg[regFreeCellList]);
      final int numStack     = listLength(reg[regStack]);
      final int numGlobalEnv = listLength(reg[regGlobalEnv]);
      if ( verbose )
      {
         log("  numFree:      " + numFree);
         log("  numStack:     " + numStack);
         log("  numGlobalEnv: " + numGlobalEnv);
      }

      // if this is a just-created selfTest(), we should see i = heap.length/2

      // Now a test which burns a free cell.
      //
      // TODO: find a way to make this a non-mutating test?
      //
      final int i0    = code(TYPE_FIXINT,0x01234567);
      final int i1    = code(TYPE_FIXINT,0x07654321);
      final int i2    = code(TYPE_FIXINT,0x01514926);
      final int cell0 = cons(i0,i1); 
      if ( (NIL == cell0) != (numFree <= 0) )
      {
         return FAILURE + 5;
      }
      if ( NIL != cell0 )
      {
         if ( i0 != car(cell0) )
         {
            return FAILURE + 10;
         }
         if ( i1 != cdr(cell0) )
         {
            return FAILURE + 20;
         }
         final int cell1 = cons(i2,cell0); 
         if ( (NIL == cell1) != (numFree <= 1) )
         {
            return FAILURE + 6;
         }
         if ( NIL != cell1 )
         {
            if ( i2 != car(cell1) )
            {
               return FAILURE + 30;
            }
            if ( cell0 != cdr(cell1) )
            {
               return FAILURE + 40;
            }
            if ( i0 != car(cdr(cell1)) )
            {
               return FAILURE + 50;
            }
            if ( i1 != cdr(cdr(cell1)) )
            {
               return FAILURE + 60;
            }
            if ( listLength(reg[regFreeCellList]) != numFree-2 )
            {
               return FAILURE + 70;
            }
         }
      }

      final int newNumFree = listLength(reg[regFreeCellList]);
      if ( verbose )
      {
         log("  newNumFree: " + newNumFree);
      }

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
      final int cell       = reg[regFreeCellList];
      if ( NIL == cell )
      {
         raiseError(ERR_OOM);
         return NIL;
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
      final boolean verbose = false;
      final int queue = cons(NIL,NIL);
      if ( verbose ) log("  queueCreate(): returning " + pp(queue));
      return queue;
   }

   /**
    * Pushes value onto the back of the queue.
    */
   private void queuePushBack ( final int queue, final int value )
   {
      final boolean verbose = false;
      final int queue_t = type(queue);
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verbose ) log("  queuePushBack(): non-queue " + pp(queue));
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( DEBUG && EOF == value ) 
      {
         // EOF cannot go in queues, lest it confuse the return value
         // channel in one of the peeks or pops.
         if ( verbose ) log("  queuePushBack(): EOF");
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( DEBUG && TYPE_CHAR != type(value) ) 
      {
         // OK, this is BS: I haven't decided for queues to be only of
         // characters.  But so far I'm only using them as such ...
         if ( verbose ) log("  queuePushBack(): non-char " + pp(value));
         raiseError(ERR_INTERNAL);
         return;
      }

      final int new_cell = cons(value,NIL);
      if ( NIL == new_cell )
      {
         if ( verbose ) log("  queuePushBack(): oom");
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
            if ( verbose ) log("  queuePushBack(): bad " + pp(h) + " " + pp(t));
            raiseError(ERR_INTERNAL); // corrupt queue
            return;
         }
         if ( verbose ) log("  queuePushBack(): pushing to empty " + pp(value));
         setcar(queue,new_cell);
         setcdr(queue,new_cell);
         return;
      }

      if ( (TYPE_CELL != type(h)) || (TYPE_CELL != type(t)) )
      {
         if ( verbose ) log("  queuePushBack(): bad " + pp(h) + " " + pp(t));
         raiseError(ERR_INTERNAL); // corrupt queue
         return;
      }

      if ( verbose ) log("  queuePushBack(): pushing to nonempty " + pp(value));
      setcdr(t,    new_cell);
      setcdr(queue,new_cell);
   }

   /**
    * Splices the list onto the back of the queue: splices, not
    * copies.  the cells in list become cells in the queue.
    *
    * TODO: DEPRECATED
    */
   private void queueSpliceBack ( final int queue, final int list )
   {
      final boolean verbose = false;
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verbose ) log("  queueSpliceBack(): non-queue " + pp(queue));
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( TYPE_NIL == type(list) ) 
      {
         if ( verbose ) log("  queueSpliceBack(): empty list");
         return; // empty list: nothing to do
      }
      if ( DEBUG && TYPE_CELL != type(list) ) 
      {
         if ( verbose ) log("  queueSpliceBack(): non-list " + pp(list));
         raiseError(ERR_INTERNAL);
         return;
      }

      // INVARIANT: head and tail are both NIL (e.g. empty) or they
      // are both cells!
      final int h = car(queue);
      final int t = cdr(queue);

      if ( NIL == h || NIL == t )
      {
         if ( verbose ) log("  empty");
         if ( NIL != h || NIL != t )
         {
            if ( verbose ) log("  queueSpliceBack(): X " + pp(h) + " " + pp(t));
            raiseError(ERR_INTERNAL); // corrupt queue
            return;
         }
         setcar(queue,list);
         setcdr(queue,list);
      }
      else
      {
         if ( (TYPE_CELL != type(h)) || (TYPE_CELL != type(t)) )
         {
            if ( verbose ) log("  queueSpliceBack(): Y " + pp(h) + " " + pp(t));
            raiseError(ERR_INTERNAL); // corrupt queue
            return;
         }
         setcdr(t,list);
      }

      int tmp = queue;
      while ( NIL != cdr(tmp) )
      {
         if ( verbose ) log("  queueSpliceBack(): advance tail");
         tmp = cdr(tmp);
      }
      if ( verbose ) log("  tail to: " + pp(tmp));
      setcdr(queue,tmp);
      if ( verbose ) log("  queue:   " + pp(queue));
      if ( verbose ) log("  list:    " + pp(list));
      if ( verbose ) log("  head:    " + pp(car(queue)));
      if ( verbose ) log("  tail:    " + pp(cdr(queue)));
   }

   /**
    * @returns the object at the front of the queue (in which case the
    * queue is mutated to remove the object), or EOF if empty
    */
   private int queuePopFront ( final int queue )
   {
      final boolean verbose = false;
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verbose ) log("  queuePopFront(): non-queue " + pp(queue));
         raiseError(ERR_INTERNAL);
         return EOF;
      }
      final int head = car(queue);
      if ( NIL == head )
      {
         if ( verbose ) log("  queuePopFront(): empty " + pp(queue));
         return EOF;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         if ( verbose ) log("  queuePopFront(): corrupt queue " + pp(head));
         raiseError(ERR_INTERNAL); // corrupt queue
         return EOF;
      }
      final int value = car(head);
      setcar(queue,cdr(head));
      if ( NIL == car(queue) )
      {
         setcdr(queue,NIL);
      }
      if ( verbose ) log("  queuePopFront(): popped " + pp(value));
      return value;
   }

   /**
    * @returns the object at the front of the queue, or EOF if empty
    */
   private int queuePeekFront ( final int queue )
   {
      final boolean verbose = false;
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verbose ) log("  queuePeekFront(): non-queue " + pp(queue));
         raiseError(ERR_INTERNAL);
         return EOF;
      }
      final int head = car(queue);
      if ( NIL == head )
      {
         if ( verbose ) log("  queuePeekFront(): empty " + pp(queue));
         return EOF;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         if ( verbose ) log("  queuePeekFront(): corrupt queue " + pp(head));
         raiseError(ERR_INTERNAL); // corrupt queue
         return EOF;
      }
      final int value = car(head);
      if ( verbose ) log("  queuePeekFront(): peeked " + pp(value));
      return value;
   }

   // scmDepth and javaDepth are ONLY used for debug cosmetics: they
   // are *not* sanctioned VM state.
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

   // TODO: permeable abstraction barrier
   public static boolean SILENT = false;

   private static String pp ( final int code )
   {
      switch (code)
      {
      case NIL:                 return "NIL";
      case EOF:                 return "EOF";
      case IS_STRING:           return "IS_STRING";
      case IS_SYMBOL:           return "IS_SYMBOL";
      case TRUE:                return "TRUE";
      case FALSE:               return "FALSE";
      case ERR_OOM:             return "ERR_OOM";
      case ERR_INTERNAL:        return "ERR_INTERNAL";
      case ERR_LEXICAL:         return "ERR_LEXICAL";
      case ERR_SEMANTIC:        return "ERR_SEMANTIC";
      case ERR_NOT_IMPL:        return "ERR_NOT_IMPL";
      case blk_re_return:       return "blk_re_return";
      case blk_error:           return "blk_error";
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
      case TYPE_SUB:      
         switch (code & ~MASK_BLOCKID)
         {
         case sub_rep:              buf.append("sub_rep");              break;
         case sub_read:             buf.append("sub_read");             break;
         case sub_read_list:        buf.append("sub_read_list");        break;
         case sub_read_token:       buf.append("sub_read_token");       break;
         case sub_read_num:         buf.append("sub_read_num");         break;
         case sub_read_num_loop:    buf.append("sub_read_num_loop");    break;
         case sub_read_boolean:     buf.append("sub_read_boolean");     break;
         case sub_read_symbol:      buf.append("sub_read_symbol");      break;
         case sub_read_symbol_loop: buf.append("sub_read_symbol_loop"); break;
         case sub_eval:             buf.append("sub_eval");             break;
         case sub_eval_list:        buf.append("sub_eval_list");        break;
         case sub_eval_lookup:      buf.append("sub_eval_lookup");      break;
         case sub_apply:            buf.append("sub_apply");            break;
         case sub_print:            buf.append("sub_print");            break;
         case sub_print_list:       buf.append("sub_print_list");       break;
         case sub_print_list_elems: buf.append("sub_print_list_elems"); break;
         case sub_print_string:     buf.append("sub_print_string");     break;
         case sub_print_chars:      buf.append("sub_print_chars");      break;
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
      case TYPE_SUB:   
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
}