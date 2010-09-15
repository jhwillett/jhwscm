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
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett, motherfuckers!
 * All rights reserved.
 */

public class Mem
{
   private final boolean DEBUG;
   private final boolean PROFILE;
   private final int     maxNumSlots;
   private final int[]   slots;

   public int numSet  = 0;
   public int numGet  = 0;
   public int maxAddr = 0;

   public Mem ( final int     maxNumSlots, 
                final boolean DEBUG, 
                final boolean PROFILE )
   {
      this.maxNumSlots = maxNumSlots;
      this.DEBUG       = DEBUG;
      this.PROFILE     = PROFILE;
      this.slots       = new int[maxNumSlots];
   }

   public void set ( final int addr, final int value )
   {
      if ( PROFILE )
      {
         numSet++;
         if ( addr > maxAddr ) maxAddr = addr;
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
         if ( addr > maxAddr ) maxAddr = addr;
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
