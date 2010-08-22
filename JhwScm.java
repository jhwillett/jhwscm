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
   public static final int SUCCESS    = 0;
   public static final int BAD_ARG    = 1;
   public static final int INCOMPLETE = 2;
   public static final int FAILURE    = 3;

   public JhwScm ()
   {
   }

   /**
    * Places all characters into the VM's input queue.
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

   private static final int MASK_TYPE    = 0xF0000000;
   private static final int MASK_VALUE   = ~MASK_TYPE;

   private static final int TYPE_NIL     = 0x00000000;
   private static final int TYPE_FIXINT  = 0x10000000;
   private static final int TYPE_CELL    = 0x20000000;
   private static final int TYPE_CHAR    = 0x30000000;

   private static final int NIL          = code(TYPE_NIL,0);

   private static final int regFreeCellList = 0; // unused cells
   private static final int regStackList    = 1; // stack frames
   private static final int regEnvList      = 2; // environment frames
   private static final int regInputList    = 3; // pending input chars
   private static final int regOutputList   = 4; // pending output chars

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
    * @returns SUCCESS on success, else FAILURE
    */
   public int selfTest ()
   {
      // consistency check
      final int t = 0x12345678 & TYPE_FIXINT;
      final int v = 0x12345678 & MASK_VALUE;
      final int c = code(t,v);
      if ( t != type(c) )
      {
         return FAILURE;
      }
      if ( v != value(c) )
      {
         return FAILURE;
      }

      int i = 0;
      for ( int p = reg[regFreeCellList]; NIL != p; p = cdr(p) )
      {
         // just loop over the free cell list to see we don't freak
         // out, such as in an infinite loop or something
         i++;
      }
      // if this is a just-created selfTest(), we should see i = heap.length/

      // Now a test which burns a free cell.
      //
      // TODO: find a way to make this a non-mutating test?
      //
      final int i0    = code(TYPE_FIXINT,0x01234567);
      final int i1    = code(TYPE_FIXINT,0x07654321);
      final int i2    = code(TYPE_FIXINT,0x01514926);
      final int cell0 = cons(i0,i1); 
      if ( NIL != cell0 )
      {
         if ( i0 != car(cell0) )
         {
            return FAILURE;
         }
         if ( i1 != cdr(cell0) )
         {
            return FAILURE;
         }
         final int cell1 = cons(i2,cell0); 
         if ( NIL != cell1 )
         {
            if ( i2 != car(cell1) )
            {
               return FAILURE;
            }
            if ( cell0 != cdr(cell1) )
            {
               return FAILURE;
            }
            if ( i0 != car(cdr(cell1)) )
            {
               return FAILURE;
            }
            if ( i1 != cdr(cdr(cell1)) )
            {
               return FAILURE;
            }
         }
      }

      return SUCCESS;
   }

   // TODO: do we want an ERROR code distinct from NIL in many of
   // these places?

   /**
    * @returns NIL on allocation failure, else a newly allocated and
    * initialize cons cell.
    */
   private int cons ( final int car, final int cdr )
   {
      final int cell = reg[regFreeCellList];
      if ( NIL == cell )
      {
         return NIL;
      }
      final int ar = ar(cell);
      if ( NIL == ar )
      {
         return NIL;
      }
      final int dr = dr(cell);
      if ( NIL == dr )
      {
         return NIL;
      }
      reg[regFreeCellList] = dr;
      heap[ar] = car;
      heap[dr] = cdr;
      return cell;
   }
   private int car ( final int cell )
   {
      final int ar = ar(cell);
      if ( NIL == ar )
      {
         return NIL;
      }
      return heap[ar];
   }
   private int cdr ( final int cell )
   {
      final int dr = dr(cell);
      if ( NIL == dr )
      {
         return NIL;
      }
      return heap[dr];
   }
   private int ar ( final int cell )
   {
      final int type = type(cell);
      if ( TYPE_CELL != type )
      {
         return NIL;
      }
      final int value = value(cell);
      return 2*value + 0;
   }
   private int dr ( final int cell )
   {
      final int type = type(cell);
      if ( TYPE_CELL != type )
      {
         return NIL;
      }
      final int value = value(cell);
      return 2*value + 1;
   }

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

   
}
