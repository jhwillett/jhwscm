/**
 * Mem.java
 *
 * Abstraction of a memory bank: main RAM, an intermediate cache, a
 * register bank, etc.
 *
 * Going OO here even where performance may be critical b/c I intend a
 * number of distinct implementations, both for exploring how much
 * Java costs and for various debugging and machine-architecture
 * experiments.
 *
 * Because this is a test/support layer, unlike JhwScm this is allowed
 * to throw exceptions on misuse.  JhwScm is responsible for making
 * sure that never happens!
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett, motherfuckers!
 * All rights reserved.
 */

public class Mem
{
   private final boolean DEBUG;
   private final boolean PROFILE;
   public  final int     maxNumSlots;
   private final int[]   slots;

   public int numSet  = 0;
   public int numGet  = 0;
   public int maxAddr = 0;

   public static int universalMaxNumSlots = 0;
   public static int universalNumSet      = 0;
   public static int universalNumGet      = 0;
   public static int universalMaxAddr     = 0;

   public Mem ( final int     maxNumSlots, 
                final boolean DEBUG, 
                final boolean PROFILE )
   {
      if ( maxNumSlots < 0 )
      {
         throw new IllegalArgumentException("neg maxNumSlots " + maxNumSlots);
      }
      this.maxNumSlots = maxNumSlots;
      this.DEBUG       = DEBUG;
      this.PROFILE     = PROFILE;
      this.slots       = new int[maxNumSlots];
      if ( PROFILE && maxNumSlots > universalMaxNumSlots)
      {
         universalMaxNumSlots = maxNumSlots;
      }
   }

   public int length ()
   {
      return maxNumSlots;
   }

   public void set ( final int addr, final int value )
   {
      if ( PROFILE )
      {
         numSet++;
         universalNumSet++;
         if ( addr > maxAddr )            maxAddr          = addr;
         if ( maxAddr > universalMaxAddr) universalMaxAddr = maxAddr;
      }
      slots[addr] = value;
   }

   public int get ( final int addr )
   {
      if ( PROFILE )
      {
         if ( DEBUG )
         {
            if ( addr > maxAddr ) 
            {
               throw new SegFault("addr " + addr + " > maxAddr " + maxAddr);
            }
         }
         numGet++;
         universalNumGet++;
         if ( addr > maxAddr )            maxAddr          = addr;
         if ( maxAddr > universalMaxAddr) universalMaxAddr = maxAddr;
      }
      return slots[addr];
   }

   private class SegFault extends RuntimeException
   {
      public SegFault ( final String msg )
      {
         super(msg);
      }
   }
}
