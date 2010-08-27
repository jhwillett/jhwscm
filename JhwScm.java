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
      log("JhwScm()");
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
      if ( null == input )
      {
         log("input(): null");
         return BAD_ARG;
      }
      log("input(): \"" + input + "\"");
      // TODO: this method is horribly inefficient :(
      //
      // Should reconsider our goal of leaving the input buffer
      // unchanged, or should do a more clever cache-the-tail-cell to
      // recover from failure in O(1).  In any case, we shouldn't have
      // to wall all the input twice here, once into the temp queue
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
         log("  pushed: " + c);
      }
      if ( NIL == reg[regIn] )
      {
         log("  bogus");
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
      if ( null == output )
      {
         return BAD_ARG;
      }
      if ( NIL == reg[regOut] )
      {
         raiseError(ERR_INTERNAL);
         return INTERNAL_ERROR;
      }
      while ( FALSE == queueIsEmpty(reg[regOut]) )
      {
         final int  f = queuePopFront(reg[regOut]);
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
      }
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
         switch ( reg[regPc] )
         {
         case sub_rep:
            // Reads the next sexpr from reg[regIn], evaluates it, and
            // prints the result in reg[regOut].
            //
            // Top-level entry point for the interactive interpreter.
            //
            log("sub_rep:");
            if ( TRUE == queueIsEmpty(reg[regIn]) )
            {
               log("  eof: done");
               return SUCCESS;
            }
            gosub(sub_read,blk_rep_after_read);
            break;
         case blk_rep_after_read:
            log("blk_rep_after_read:");
            reg[regArg0] = reg[regRetval];
            reg[regArg1] = reg[regGlobalEnv];
            gosub(sub_eval,blk_rep_after_eval);
            break;
         case blk_rep_after_eval:
            log("blk_rep_after_eval:");
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
            gosub(sub_print,blk_rep_after_print);
            break;
         case blk_rep_after_print:
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
            log("blk_rep_after_print:");
            jump(sub_rep);
            break;

         case sub_read:
            // Parses the next sexpr from reg[regIn], and
            // leaves the results in reg[regRetval].
            //
            // Top-level entry point for the parser.
            //
            log("sub_read:");
            c = queuePeekFront(reg[regIn]);
            t = type(c);
            v = value(c);
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
               jump(sub_read); // or we could self-tail-recurse here
               break;
            case '(':
               gosub(sub_read_list,blk_re_return);
               break;
            case '\'':
               gosub(sub_read,blk_read_quote);
               break;
            case '\"':
               raiseError(ERR_NOT_IMPL);
               break;
            default:
               gosub(sub_read_token,blk_re_return);
               break;
            }
            break;
         case blk_read_quote:
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
            gosub(sub_read,blk_read_list_mid);
            raiseError(ERR_NOT_IMPL);
            break;
         case blk_read_list_mid:
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
            if ( '-' == v )
            {
               // The minus sign is special.  We need to look ahead
               // *again* before we can decide whether it is part of a
               // symbol or part of a number.
               queuePopFront(reg[regIn]);
               c1 = queuePeekFront(reg[regIn]);
               t1 = type(c);
               v1 = value(c);
               if ( DEBUG && TYPE_CHAR != t1 )
               {
                  raiseError(ERR_INTERNAL);
                  break;
               }
               if ( '0' <= v1 || v1 >= '9' )
               {
                  gosub(sub_read_num,blk_read_token_neg);
                  break;
               }
               else
               {
                  queuePushBack(reg[regIn],c1);
                  gosub(sub_read_symbol,blk_re_return);
                  break;
               }
            }
            if ( '0' <= v1 || v1 <= '9' )
            {
               gosub(sub_read_num,blk_re_return);
               break;
            }
            if ( '#' == v1 )
            {
               gosub(sub_read_boolean,blk_re_return);
               break;
            }
            raiseError(ERR_NOT_IMPL);
            break;

         case sub_read_num:
            // Parses the next number from reg[regIn].
            //
            log("sub_read_num:");
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
            log("sub_read_num_loop:");
            if ( TRUE == queueIsEmpty(reg[regIn]) )
            {
               log("  eof, returning: " + pp(reg[regArg0]));
               reg[regRetval] = reg[regArg0];
               returnsub();
               break;
            }
            c0 = queuePopFront(reg[regIn]);
            t0 = type(c0);
            v0 = value(c0);
            if ( TYPE_CHAR != t0 )
            {
               log("  non-char in arg: " + pp(c0));
               raiseError(ERR_LEX);
               break;
            }
            if ( v0 < '0' || v0 > '9' )
            {
               log("  non-digit in arg: " + pp(c0));
               raiseError(ERR_LEX);
               break;
            }
            c1 = reg[regArg0];
            t1 = type(c1);
            v1 = value(c1);
            if ( TYPE_FIXINT != t1 )
            {
               log("  non-fixint in arg: " + pp(c1));
               raiseError(ERR_LEX);
               break;
            }
            tmp = 10*v1 + (v0-'0');
            log("  first char: " + (char)v0);
            log("  old accum:  " +       v1);
            log("  new accum:  " +       tmp);
            reg[regArg0] = code(TYPE_FIXINT,tmp);
            gosub(sub_read_num_loop,blk_re_return);
            break;

         case sub_eval:
            // Evaluates the expr in reg[regArg0] in the env in
            // reg[regArg1], and leaves the results in reg[regRetval].
            //
            // TODO: implement properly.
            //
            log("sub_eval:");
            if ( true )
            {
               // Treats all exprs as self-evaluating.
               //
               // Handy, b/c we can pass all the self-evaluating unit
               // tests and know something about sub_rep, sub_read
               // and sub_print.
               // 
               reg[regRetval] = reg[regArg0];
               returnsub();
            }
            else
            {
               raiseError(ERR_NOT_IMPL);
            }
            break;

         case sub_print:
            // Prints the expr in reg[regArg0] to reg[regOut].
            //
            log("sub_print:");
            log("  reg[regArg0]: " + pp(reg[regArg0]));
            t = type(reg[regArg0]);
            v = value(reg[regArg0]);
            switch (t)
            {
            case TYPE_FIXINT:
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
               break;
            default:
               raiseError(ERR_INTERNAL);
               break;
            }
            returnsub();
            break;

         case blk_re_return:
            // Just returns whatever retval left behind by the
            // subroutine which continued to here.
            //
            log("blk_re_return: " + pp(reg[regRetval]));
            returnsub();
            break;

         case blk_error:
            // TODO: print stack trace? ;)
            //
            // TODO: return various externally visible codes based on
            // the internal code in reg[regError]
            log("blk_error:");
            log("  reg[regError]      " + reg[regError]);
            log("  reg[regErrorPc]    " + reg[regErrorPc]);
            log("  reg[regErrorStack] " + reg[regErrorStack]);
            return FAILURE;

         default:
            log("bogus opcode: " + reg[regPc]);
            raiseError(ERR_INTERNAL);
            break;
         }
      }

      return SUCCESS;
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

   private static final int TYPE_NIL     = 0x10000000;
   private static final int TYPE_FIXINT  = 0x20000000;
   private static final int TYPE_CELL    = 0x30000000;
   private static final int TYPE_CHAR    = 0x40000000;
   private static final int TYPE_SUB     = 0x50000000;
   private static final int TYPE_BLK     = 0x60000000;
   private static final int TYPE_ERR     = 0x70000000;
   private static final int TYPE_BOOL    = 0x80000000;

   // In many of these constants, I would prefer to initialize them as
   // code(TYPE_FOO,n) rather than TYPE_FOO|n, for consistency and to
   // maintain the abstraction barrier.
   //
   // Unfortunately, even though code() is static, idempotent, and a
   // matter of simple arithmetic, javac does not let me use the
   // resultant names in switch statements.  In C, I'd just make
   // code() a macro and be done with it.

   private static final int NIL                 = TYPE_NIL  | 0;

   private static final int ERR_OOM             = TYPE_ERR  | 0;
   private static final int ERR_INTERNAL        = TYPE_ERR  | 1;
   private static final int ERR_LEX             = TYPE_ERR  | 2;
   private static final int ERR_NOT_IMPL        = TYPE_ERR  | 3;

   private static final int TRUE                = TYPE_BOOL | 37;
   private static final int FALSE               = TYPE_BOOL | 91;

   private static final int regFreeCellList     =  0; // unused cells
   private static final int regStack            =  1; // the runtime stack
   private static final int regGlobalEnv        =  2; // environment frames
   private static final int regIn               =  3; // input char queue
   private static final int regOut              =  4; // output char queue

   private static final int regArg0             =  5; // argument
   private static final int regArg1             =  6; // argument
   private static final int regRetval           =  7; // return value

   private static final int regOpNext           =  8; // next opcode to run
   private static final int regOpContinuation   =  9; // opcode to return to

   private static final int regPc               = 10; // opcode to return to

   private static final int regError            = 11; // NIL or a TYPE_ERR
   private static final int regErrorPc          = 12; // reg[regPc] of err
   private static final int regErrorStack       = 13; // reg[regStack] of err

   private static final int sub_rep             = TYPE_SUB |   10;
   private static final int blk_rep_after_eval  = TYPE_BLK |   11;
   private static final int blk_rep_after_read  = TYPE_BLK |   12;
   private static final int blk_rep_after_print = TYPE_BLK |   13;

   private static final int sub_read            = TYPE_SUB |  100;
   private static final int blk_read_quote      = TYPE_BLK |  101;

   private static final int sub_read_list       = TYPE_SUB |  110;
   private static final int blk_read_list_mid   = TYPE_BLK |  111;

   private static final int sub_read_token      = TYPE_SUB |  120;
   private static final int blk_read_token_neg  = TYPE_SUB |  121;

   private static final int sub_read_num        = TYPE_SUB |  130;
   private static final int sub_read_num_loop   = TYPE_SUB |  131;

   private static final int sub_read_boolean    = TYPE_SUB |  140;

   private static final int sub_read_symbol     = TYPE_SUB |  150;

   private static final int sub_eval            = TYPE_SUB |  200;

   private static final int sub_print           = TYPE_SUB |  300;


   private static final int blk_re_return       = TYPE_BLK | 1000;
   private static final int blk_error           = TYPE_BLK | 1001;


   private void jump ( final int nextOp )
   {
      log("  jump()");
      if ( DEBUG )
      {
         final int t = type(nextOp);
         if ( TYPE_SUB != t && TYPE_BLK != t )
         {
            raiseError(ERR_INTERNAL);
            return;
         }
      }
      reg[regPc] = nextOp;
   }

   private void gosub ( final int nextOp, final int continuationOp )
   {
      if ( verbose ) log("  gosub()");
      if ( verbose ) log("    old stack: " + reg[regStack]);
      if ( DEBUG )
      {
         final int nt = type(nextOp);
         if ( TYPE_SUB != nt )
         {
            raiseError(ERR_INTERNAL);
            return;
         }
         final int ct = type(nextOp);
         if ( TYPE_SUB != ct && TYPE_BLK != ct )
         {
            raiseError(ERR_INTERNAL);
            return;
         }
      }
      final int newStack = cons(continuationOp,reg[regStack]);
      if ( NIL == newStack )
      {
         raiseError(ERR_OOM);
         return;
      }
      reg[regStack]  = newStack;
      reg[regPc]     = nextOp;
      if ( verbose ) log("    new stack: " + reg[regStack]);
      if ( DEBUG ) subDepth++;
   }

   private void returnsub ()
   {
      if ( verbose ) log("  returnsub()");
      if ( verbose ) log("    old stack: " + reg[regStack]);
      if ( DEBUG && TYPE_CELL != type(reg[regStack]) )
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      final int head = car(reg[regStack]);
      final int rest = cdr(reg[regStack]);
      // TODO: recycle reg[regStack]? (at least, if we haven't ended
      // up in an error state or are otherwise "holding" old stacks)
      reg[regPc]     = head;
      reg[regStack]  = rest;
      if ( verbose ) log("    new stack: " + reg[regStack]);
      if ( DEBUG ) subDepth--;
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
      log("  raiseError(): " + err);
      if ( DEBUG && TYPE_ERR != type(err) )
      {
         // TODO: Bad call to raiseError()? Are we out of tricks?
      }
      if ( NIL == reg[regError] ) 
      {
         reg[regError]      = err;
         reg[regErrorPc]    = reg[regPc];
         reg[regErrorStack] = reg[regStack];
      }
      reg[regPc] = blk_error;
      if ( DEBUG )
      {
         new Throwable("raiseError()").printStackTrace();
         for ( int c = reg[regErrorStack]; NIL != c; c = cdr(c) )
         {
            // TODO: hopefully the stack isn't corrupt....
            log("    stack: " + pp(car(c)));
         }
      }
   }

   private final int[] heap = new int[1024];
   private final int[] reg  = new int[16];

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

   // returns NIL on failure, else a new empty queue
   private int queueCreate ()
   {
      return cons(NIL,NIL);
   }

   // returns TRUE or FALSE
   private int queueIsEmpty ( final int queue )
   {
      if ( verbose ) log("queueIsEmpty(): " + queue);
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verbose ) log("  bogus A");
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
      final int head = car(queue);
      final int tail = cdr(queue);
      if ( DEBUG && ( (NIL == head) != (NIL == tail) ) ) 
      {
         if ( verbose ) 
         {
            log("  bogus B: " + (NIL == head) + " " + (NIL == tail));
         }
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
      if ( NIL == head )
      {
         if ( verbose ) log("  empty");
         return TRUE;
      }
      if ( DEBUG && TYPE_CELL != type(head) )
      {
         if ( verbose ) log("  bogus C");
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
      if ( DEBUG && TYPE_CELL != type(tail) )
      {
         if ( verbose ) log("  bogus D");
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
      if ( verbose ) log("  nonempty");
      return FALSE;
   }

   private void queuePushBack ( final int queue, final int value )
   {
      final int queue_t = type(queue);
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         raiseError(ERR_INTERNAL);
         return;
      }

      final int new_cell = cons(value,NIL);
      if ( NIL == new_cell )
      {
         return;
      }

      // INVARIANT: head and tail are both NIL (e.g. empty) or they
      // are both cells!
      final int head = car(queue);
      final int tail = cdr(queue);

      if ( NIL == head || NIL == tail )
      {
         if ( NIL != head || NIL != tail )
         {
            raiseError(ERR_INTERNAL); // corrupt queue
            return;
         }
         setcar(queue,new_cell);
         setcdr(queue,new_cell);
         return;
      }

      if ( (TYPE_CELL != type(head)) || (TYPE_CELL != type(tail)) )
      {
         raiseError(ERR_INTERNAL); // corrupt queue
         return;
      }

      setcdr(tail,new_cell);
      setcdr(queue,new_cell);
   }

   private void queueSpliceBack ( final int queue, final int list )
   {
      if ( verbose ) log("queueSpliceBack()");
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      if ( NIL == type(list) ) 
      {
         return; // empty list: nothing to do
      }
      if ( DEBUG && TYPE_CELL != type(list) ) 
      {
         raiseError(ERR_INTERNAL);
         return;
      }

      // INVARIANT: head and tail are both NIL (e.g. empty) or they
      // are both cells!
      final int head = car(queue);
      final int tail = cdr(queue);

      if ( NIL == head || NIL == tail )
      {
         if ( verbose ) log("  empty");
         if ( NIL != head || NIL != tail )
         {
            raiseError(ERR_INTERNAL); // corrupt queue
            return;
         }
         setcar(queue,list);
         setcdr(queue,list);
      }
      else
      {
         if ( (TYPE_CELL != type(head)) || (TYPE_CELL != type(tail)) )
         {
            raiseError(ERR_INTERNAL); // corrupt queue
            return;
         }
         if ( verbose ) log("  non empty");
         setcdr(tail,list);
      }

      int t = queue;
      while ( NIL != cdr(t) )
      {
         if ( verbose ) log("  advance tail");
         t = cdr(t);
      }
      if ( verbose ) log("  tail to " + t);
      setcdr(queue,t);
      if ( verbose ) log("  queue: " + queue);
      if ( verbose ) log("  list:  " + list);
      if ( verbose ) log("  head:  " + car(queue));
      if ( verbose ) log("  tail:  " + cdr(queue));
      if ( verbose ) log("  NIL:   " + NIL);
   }

   private int queuePopFront ( final int queue )
   {
      final boolean verbose = false;
      if ( verbose ) log("  queuePopFront(): " + reg[regOut]);
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verbose ) log("    not a queue");
         raiseError(ERR_INTERNAL);
         return NIL;
      }
      final int head = car(queue);
      if ( NIL == head )
      {
         if ( verbose ) log("    empty queue");
         return NIL;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         if ( verbose ) log("    corrupt queue");
         raiseError(ERR_INTERNAL); // corrupt queue
         return NIL;
      }
      final int value = car(head);
      setcar(queue,cdr(head));
      if ( NIL == car(queue) )
      {
         setcdr(queue,NIL);
      }
      if ( verbose ) log("    popped: " + pp(value));
      return value;
   }

   private int queuePeekFront ( final int queue )
   {
      final boolean verbose = true;
      if ( verbose ) log("queuePeekFront(): " + reg[regOut]);
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         if ( verbose ) log("  not a queue");
         raiseError(ERR_INTERNAL);
         return NIL;
      }
      final int head = car(queue);
      if ( NIL == head )
      {
         if ( verbose ) log("  empty queue");
         return NIL;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         if ( verbose ) log("  corrupt queue");
         raiseError(ERR_INTERNAL); // corrupt queue
         return NIL;
      }
      final int value = car(head);
      if ( verbose ) log("  peeked: " + pp(value));
      return car(head);
   }

   private void queuePushFront ( final int queue, final int value )
   {
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      final int head = car(queue);
      final int tmp  = cons(value,head);
      if ( NIL == tmp )
      {
         return; // avoid further damage
      }
      setcar(queue,tmp);
      if ( NIL == head )
      {
         // queue w/ 1 entry, head needs to equal tail
         setcdr(queue,tmp);
      }
   }

   // subDepth is ONLY used for debug cosmetics: it is not
   // "sanctioned" piece of VM state.
   //
   private int subDepth = 0;
   private void log ( final Object msg )
   {
      for (int i = 0; i < subDepth; ++i)
      {
         System.out.print("  ");
      }
      System.out.println(msg);
   }

   private static String pp ( final int code )
   {
      switch (code)
      {
      case sub_rep:             return "sub_rep";
      case blk_rep_after_eval:  return "blk_rep_after_eval";
      case blk_rep_after_read:  return "blk_rep_after_read";
      case blk_rep_after_print: return "blk_rep_after_print";
      case sub_read:            return "sub_read";
      case blk_read_quote:      return "blk_read_quote";
      case sub_read_list:       return "sub_read_list";
      case blk_read_list_mid:   return "blk_read_list_mid";
      case sub_read_token:      return "sub_read_token";
      case blk_read_token_neg:  return "blk_read_token_neg";
      case sub_read_num:        return "sub_read_num";
      case sub_read_boolean:    return "sub_read_boolean";
      case sub_read_symbol:     return "sub_read_symbol";
      case sub_eval:            return "sub_eval";
      case sub_print:           return "sub_print";
      case blk_re_return:       return "blk_re_return";
      case blk_error:           return "blk_error";
      case NIL:                 return "NIL";
      case TRUE:                return "TRUE";
      case FALSE:               return "FALSE";
      case ERR_OOM:             return "ERR_OOM";
      case ERR_INTERNAL:        return "ERR_INTERNAL";
      case ERR_LEX:             return "ERR_LEX";
      case ERR_NOT_IMPL:        return "ERR_NOT_IMPL";
      }
      final int t = type(code);
      final int v = value(code);
      final StringBuilder buf = new StringBuilder();
      switch (t)
      {
      case TYPE_NIL:    buf.append("nil");  break;
      case TYPE_FIXINT: buf.append("int");  break;
      case TYPE_CELL:   buf.append("cel");  break;
      case TYPE_CHAR:   buf.append("chr");  break;
      case TYPE_SUB:    buf.append("sub");  break;
      case TYPE_BLK:    buf.append("blk");  break;
      case TYPE_ERR:    buf.append("err");  break;
      case TYPE_BOOL:   buf.append("boo");  break;
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
         buf.append('\''); 
         if ( ' ' <= v && v < '~' )
         {
            buf.append((char)v); 
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
         buf.append('\''); 
         break;
      case TYPE_BOOL:   
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
      default:          
         buf.append(v);       
         break;
      }
      return buf.toString();
   }
}
