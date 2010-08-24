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

   public static final int     SUCCESS        = 0;
   public static final int     INCOMPLETE     = 1000;
   public static final int     BAD_ARG        = 2000; // often + specifier
   public static final int     OUT_OF_MEMORY  = 3000; // often + specifier
   public static final int     FAILURE        = 4000; // often + specifier
   public static final int     UNIMPLEMENTED  = 5000; // often + specifier
   public static final int     INTERNAL_ERROR = 6000; // often + specifier

   public JhwScm ()
   {
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
      log("input():  \"" + input + "\"");
      if ( null == input )
      {
         return BAD_ARG;
      }
      // TODO: this method is horribly inefficient :(
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
      if ( NIL == reg[regInputQueue] )
      {
         reg[regInputQueue] = queueCreate();
         if ( NIL == reg[regInputQueue] )
         {
            return OUT_OF_MEMORY + 1;
         }
      }
      queueSpliceBack(reg[regInputQueue],car(tmpQueue));
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
      if ( NIL == reg[regOutputQueue] )
      {
         return SUCCESS;
      }
      while ( FALSE == queueIsEmpty(reg[regOutputQueue]) )
      {
         final int  f = queuePopFront(reg[regOutputQueue]);
         final int  v = value(f);
         final char c = (char)(MASK_VALUE & v);
         // TODO: change signature so we don't need this guard here?
         // 
         // TODO: make this all-or-nothing, like input()?
         try
         {
            Thread.sleep(100);
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
      int code = 0;
      int t    = 0;
      int v    = 0;
      int err  = SUCCESS;

      for ( int step = 0; -1 == numSteps || step < numSteps; ++step )
      {
         if ( DEBUG )
         {
            t = type(reg[regPc]);
            if ( TYPE_SUB != t && TYPE_BLK != t )
            {
               return INTERNAL_ERROR;
            }
         }
         switch ( reg[regPc] )
         {
         case sub_rep:
            // The top level read-eval-print loop:
            //
            //   (while (has-some-input) (print (eval (read) global-env)))
            //
            log("sub_rep:");
            if ( TRUE == queueIsEmpty(reg[regInputQueue]) )
            {
               return SUCCESS;
            }
            log("queue not empty");
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
            // Note: we could probably tighten up rep by just going
            // right to sub_rep on return from sub_print.
            //
            // Going with the extra blk for now to take it easy on the
            // subtlety.
            gosub(sub_print,sub_rep);
            break;
         case blk_rep_after_print:
            log("blk_rep_after_print:");
            jump(sub_rep);
            break;

         case sub_read:
            // Parses the next sexpr from reg[regInputQueue], and
            // leaves the results in reg[regRetval].
            //
            log("sub_read:");
            code = queuePopFront(reg[regInputQueue]);
            t    = type(code);
            v    = value(code);
            if ( TYPE_CHAR != t )
            {
               log("non-char in input: " + code + " " + t + " " + (int)v);
               return FAILURE;
            }
            if ( '0' <= v && v <= '9' )
            {
               reg[regArg0] = code;
               // TODO: could tail-recurse here
               gosub(sub_read_number,blk_re_return);
               break;
            }
            else
            {
               return UNIMPLEMENTED + 1;
            }

         case sub_read_number:
            // Parses the next number from reg[regInputQueue], given
            // that the first digit/char is in reg[regArg0].
            //
            log("sub_read_number:");
            code = reg[regArg0];
            t    = type(code);
            v    = value(code);
            if ( TYPE_CHAR != t )
            {
               log("non-char in arg: " + code + " " + t + " " + (char)v);
               return FAILURE;
            }
            log("  first char: " + (char)v);
            return UNIMPLEMENTED + 1;

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
               break;
            }
            else
            {
               // Treats all exprs as evaluating to NIL.
               reg[regRetval] = NIL;
               returnsub();
               return UNIMPLEMENTED + 2;
            }

         case sub_print:
            // Prints the expr in reg[regArg0] to reg[regOutputQueue].
            //
            log("sub_print:");
            reg[regRetval] = NIL;
            returnsub();
            return UNIMPLEMENTED + 3;

         case blk_re_return:
            // Just returns whatever return value left behind by the
            // subroutine which continued to here.
            //
            log("blk_re_return:");
            returnsub();
            break;

         case blk_error:
            // TODO: print stack trace? ;)
            //
            log("blk_error:");
            return FAILURE;

         default:
            log("bogus opcode: " + reg[regPc]);
            return FAILURE;
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
   
   private static final int MASK_TYPE    = 0xF0000000;
   private static final int MASK_VALUE   = ~MASK_TYPE;

   private static final int TYPE_NIL     = 0x10000000;
   private static final int TYPE_FIXINT  = 0x20000000;
   private static final int TYPE_CELL    = 0x30000000;
   private static final int TYPE_CHAR    = 0x40000000;
   private static final int TYPE_SUB     = 0x50000000;
   private static final int TYPE_BLK     = 0x60000000;
   private static final int TYPE_ERR     = 0x70000000;
   private static final int TYPE_BOOL    = 0x80000000;

   private static final int NIL          = code(TYPE_NIL,0);

   private static final int ERR_OOM      = code(TYPE_ERR,0);
   private static final int ERR_INTERNAL = code(TYPE_NIL,0);

   private static final int TRUE         = code(TYPE_BOOL,1);
   private static final int FALSE        = code(TYPE_BOOL,0);

   private static final int regFreeCellList   =  0; // unused cells
   private static final int regStack          =  1; // the runtime stack
   private static final int regGlobalEnv      =  2; // environment frames
   private static final int regInputQueue     =  3; // pending input chars
   private static final int regOutputQueue    =  4; // pending output chars

   private static final int regArg0           =  5; // argument
   private static final int regArg1           =  6; // argument
   private static final int regRetval         =  7; // return value

   private static final int regOpNext         =  8; // next opcode to run
   private static final int regOpContinuation =  9; // opcode to return to

   private static final int regPc             = 10; // opcode to return to

   private static final int regError          = 11; // NIL or a TYPE_ERR
   private static final int regErrorPc        = 12; // reg[regPc] of err
   private static final int regErrorStack     = 13; // reg[regStack] of err

   private static final int sub_rep             = TYPE_SUB |  10;
   private static final int blk_rep_after_eval  = TYPE_BLK |  11;
   private static final int blk_rep_after_read  = TYPE_BLK |  12;
   private static final int blk_rep_after_print = TYPE_BLK |  13;

   private static final int sub_read            = TYPE_SUB |  20;
   private static final int sub_read_number     = TYPE_SUB |  22;

   private static final int sub_eval            = TYPE_SUB |  30;

   private static final int sub_print           = TYPE_SUB |  40;

   private static final int blk_re_return       = TYPE_SUB | 100;

   private static final int blk_error           = TYPE_SUB | 101;


   private void jump ( final int nextOp )
   {
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
      reg[regStack] = newStack;
      reg[regPc]    = nextOp;
   }

   private void returnsub ()
   {
      if ( DEBUG && TYPE_CELL != reg[regStack] )
      {
         raiseError(ERR_INTERNAL);
         return;
      }
      final int continuationOp = car(reg[regStack]);
      // TODO: recycle regStack?
      reg[regStack] = cdr(reg[regStack]);
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
      reg[regPc]         = blk_error;
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
      final boolean verbose = false;
      if ( verbose )
      {
         log("JhwScm.selfTest()");
      }

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
      if ( DEBUG && TYPE_CELL != type(queue) ) 
      {
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
      final int head = car(queue);
      final int tail = cdr(queue);
      if ( DEBUG && ( (NIL == head) != (NIL == tail) ) ) 
      {
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
      if ( NIL == head )
      {
         return TRUE;
      }
      if ( DEBUG && TYPE_CELL != type(head) )
      {
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
      if ( DEBUG && TYPE_CELL != type(tail) )
      {
         raiseError(ERR_INTERNAL);
         return FALSE;
      }
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
      if ( DEBUG && TYPE_CELL != type(queue) ) 
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
         setcdr(tail,list);
      }

      while ( NIL != cdr(queue) )
      {
         setcdr(queue,cdr(cdr(queue)));
      }
   }

   // TODO: get externally-visible error codes out of here, this is
   // not a public method!
   private int queuePopFront ( final int queue )
   {
      final boolean verbose = false;
      if ( verbose )
      {
         log("DEQUEUE: " + reg[regOutputQueue]);
      }

      final int queue_t = type(queue);
      if ( TYPE_CELL != queue_t ) 
      {
         if ( verbose )
         {
            log("not a queue");
         }
         return NIL;
      }

      final int head = car(queue);
      if ( NIL == head )
      {
         // empty queue
         if ( verbose )
         {
            log("empty queue");
         }
         return NIL;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         // TODO: corrupt queue
         if ( verbose )
         {
            log("corrupt queue");
         }
         return NIL;
      }
      final int value = car(head);
      // TODO: recycle head
      setcar(queue,cdr(head));
      if ( NIL == car(queue) )
      {
         setcdr(queue,NIL);
      }
      if ( verbose )
      {
         log("happy pop");
      }
      return value;
   }

   private static void log ( final Object msg )
   {
      System.out.println(msg);
   }


}
