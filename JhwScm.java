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
   public static final int SUCCESS       = 0;
   public static final int INCOMPLETE    = 1;

   public static final int BAD_ARG       = 1000; // often added to a specifier

   public static final int OUT_OF_MEMORY = 2000; // often added to a specifier

   public static final int FAILURE       = 3000; // often added to a specifier

   public JhwScm ()
   {
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
         return BAD_ARG;
      }
      final int tmpQueue = cons(NIL,NIL);
      if ( NIL == tmpQueue )
      {
         return OUT_OF_MEMORY + 1;
      }
      for ( int i = 0; i < input.length(); ++i )
      {
         final char c    = input.charAt(i);
         System.out.println("  INPUTTING: " + c + " " + (int)c);
         final int  code = code(TYPE_CHAR,c);
         if ( !queuePushBack(tmpQueue,code) )
         {
            // TODO: could recycle the cell at tmpQueue here.
            return OUT_OF_MEMORY + 2;
         }
      }
      System.out.println("  OUT");
      if ( NIL == reg[regInputQueue] )
      {
         reg[regInputQueue] = cons(NIL,NIL);
      }
      System.out.println("  SPLICING");
      final int err = queueSpliceBack(reg[regInputQueue],car(tmpQueue));
      // TODO: could recycle the cell at tmpQueue here.
      System.out.println("  DONE");
      return err;
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
      System.out.println("QUEUE: " + reg[regOutputQueue]);
      if ( NIL == reg[regOutputQueue] )
      {
         reg[regOutputQueue] = cons(NIL,NIL);
         System.out.println("REQUEUE: " + reg[regOutputQueue]);
      }
      while ( !queueEmpty(reg[regOutputQueue]) )
      {
         final int  f = queuePopFront(reg[regOutputQueue]);
         final int  v = value(f);
         final char c = (char)(MASK_VALUE & v);
         System.out.println("f " + f + " v " + v + " " + (int)c);

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

   private static final int NIL          = code(TYPE_NIL,0);

   private static final int regFreeCellList = 0; // unused cells
   private static final int regStackList    = 1; // stack frames
   private static final int regEnvList      = 2; // environment frames
   private static final int regInputQueue   = 3; // pending input chars
   private static final int regOutputQueue  = 4; // pending output chars

   private final int[] heap = new int[1024];
   private final int[] reg  = new int[16];

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
   }

   /**
    * Checks that the VM is internally consistent, that all internal
    * invariants are still true.
    *
    * @returns SUCCESS on success, else (FAILURE+code)
    */
   public int selfTest ()
   {
      final boolean verbose = true;
      if ( verbose )
      {
         System.out.println("JhwScm.selfTest()");
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

      final int numFree   = listLength(reg[regFreeCellList]);
      final int numStack  = listLength(reg[regStackList]);
      final int numEnv    = listLength(reg[regEnvList]);
      final int numInput  = queueLength(reg[regInputQueue]);
      final int numOutput = queueLength(reg[regOutputQueue]);
      if ( verbose )
      {
         System.out.println("  numFree:   " + numFree);
         System.out.println("  numStack:  " + numStack);
         System.out.println("  numEnv:    " + numEnv);
         System.out.println("  numInput:  " + numInput);
         System.out.println("  numOutput: " + numOutput);
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
         System.out.println("  newNumFree: " + newNumFree);
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

   // TODO: do we want an ERROR code distinct from NIL in many of
   // these places?


   /**
    * @returns NIL on allocation failure, else a newly allocated and
    * initialize cons cell.
    */
   private int cons ( final int car, final int cdr )
   {
      final int cell       = reg[regFreeCellList];
      final int t          = type(cell);
      if ( TYPE_CELL != t )
      {
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
      if ( TYPE_CELL != type(cell) )
      {
         return NIL;
      }
      return heap[(value(cell) << 1) + 0];
   }
   private int cdr ( final int cell )
   {
      if ( TYPE_CELL != type(cell) )
      {
         return NIL;
      }
      return heap[(value(cell) << 1) + 1];
   }
   private int setcar ( final int cell, final int value )
   {
      if ( TYPE_CELL != type(cell) )
      {
         return NIL;
      }
      heap[(value(cell) << 1) + 0] = value;
      return NIL;
   }
   private int setcdr ( final int cell, final int value )
   {
      if ( TYPE_CELL != type(cell) )
      {
         return NIL;
      }
      heap[(value(cell) << 1) + 1] = value;
      return NIL;
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
    * @returns the length of the queue rooted at cell: as a regular
    * int, not as a TYPE_FIXINT!  Is unsafe about cycles!
    */
   private int queueLength ( final int queue )
   {
      final int queue_t = type(queue);
      if ( TYPE_CELL != queue_t ) return 0;
      final int car     = car(queue);
      final int car_t   = type(car);
      if ( TYPE_CELL != car_t ) return 0;
      return listLength(car);
   }

   private boolean queueEmpty ( final int queue )
   {
      final int queue_t = type(queue);
      if ( TYPE_CELL != queue_t ) 
      {
         return false;
      }
      final int head = car(queue);
      final int tail = cdr(queue);
      return ( NIL == head && NIL == tail );
   }

   private boolean queuePushBack ( final int queue, final int value )
   {
      final int queue_t = type(queue);
      if ( TYPE_CELL != queue_t ) 
      {
         return false;
      }

      final int new_cell = cons(value,NIL);
      if ( NIL == new_cell )
      {
         // TODO: or return OUT_OF_MEMORY?
         return false;
      }

      // INVARIANT: head and tail are both NIL (e.g. empty) or they
      // are both cells!
      final int head = car(queue);
      final int tail = cdr(queue);

      if ( NIL == head || NIL == tail )
      {
         if ( NIL != head || NIL != tail )
         {
            // TODO: corrupt queue!
            //
            // TODO: recycle new_cell?
            return false;
         }
         setcar(queue,new_cell);
         setcdr(queue,new_cell);
         return true;
      }

      if ( (TYPE_CELL != type(head)) || (TYPE_CELL != type(tail)) )
      {
         // TODO: corrupt queue!
         //
         // TODO: recycle new_cell?
         return false;
      }
      setcdr(tail,new_cell);
      setcdr(queue,new_cell);
      return true;
   }

   private int queueSpliceBack ( final int queue, final int list )
   {
      if ( TYPE_CELL != type(queue) ) 
      {
         return BAD_ARG;
      }

      // INVARIANT: head and tail are both NIL (e.g. empty) or they
      // are both cells!
      final int head = car(queue);
      final int tail = cdr(queue);

      if ( NIL == head || NIL == tail )
      {
         if ( NIL != head || NIL != tail )
         {
            // TODO: corrupt queue!
            return FAILURE + 900;
         }
         setcar(queue,list);
         setcdr(queue,list);
      }
      else
      {
         if ( (TYPE_CELL != type(head)) || (TYPE_CELL != type(tail)) )
         {
            // TODO: corrupt queue!
            return FAILURE + 901;
         }
         setcdr(tail,list);
      }

      while ( NIL != cdr(queue) )
      {
         setcdr(queue,cdr(cdr(queue)));
      }

      return SUCCESS;
   }

   private int queuePopFront ( final int queue )
   {
      System.out.println("DEQUEUE: " + reg[regOutputQueue]);

      final int queue_t = type(queue);
      if ( TYPE_CELL != queue_t ) 
      {
         System.out.println("not a queue");
         return NIL;
      }

      final int head = car(queue);
      if ( NIL == head )
      {
         // empty queue
         System.out.println("empty queue");
         return NIL;
      }
      if ( TYPE_CELL != type(head) ) 
      {
         // TODO: corrupt queue
         System.out.println("corrupt queue");
         return NIL;
      }
      final int value = car(head);
      // TODO: recycle head
      setcar(queue,cdr(head));
      if ( NIL == car(queue) )
      {
         setcdr(queue,NIL);
      }
      System.out.println("happy pop");
      return value;
   }


}
