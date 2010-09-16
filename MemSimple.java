/**
 * MemSimple.java
 *
 * A simple implementation of Mem, which keeps the slots in a single
 * Java array which never resizes.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett, motherfuckers!
 * All rights reserved.
 */

public class MemSimple implements Mem
{
   private final boolean DEBUG;
   private final boolean PROFILE;
   private final int     maxNumSlots;
   private final int[]   slots;

   public int numSet  = 0;
   public int numGet  = 0;
   public int maxAddr = 0;

   public static int universalMaxNumSlots = 0;
   public static int universalNumSet      = 0;
   public static int universalNumGet      = 0;
   public static int universalMaxAddr     = 0;

   public MemSimple ( final int     maxNumSlots, 
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
}
