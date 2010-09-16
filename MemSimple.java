/**
 * MemSimple.java
 *
 * A simple implementation of Mem, which keeps the slots in a single
 * Java array which never resizes.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class MemSimple implements Mem
{
   private final int[] slots;

   public MemSimple ( final int length )
   {
      if ( length < 0 )
      {
         throw new IllegalArgumentException("neg length " + length);
      }
      this.slots = new int[length];
   }

   public int length ()
   {
      return slots.length;
   }

   public void set ( final int addr, final int value )
   {
      slots[addr] = value;
   }

   public int get ( final int addr )
   {
      return slots[addr];
   }
}
