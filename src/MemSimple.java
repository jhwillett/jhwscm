/**
 * MemSimple.java
 *
 * A simple implementation of Mem.  Words are implemented as a
 * fixed-size Java int[].
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class MemSimple implements Mem
{
   private final int[] words;

   public MemSimple ( final int length )
   {
      if ( length < 0 )
      {
         throw new IllegalArgumentException("neg length " + length);
      }
      this.words = new int[length];
   }

   public int length ()
   {
      return words.length;
   }

   public void set ( final int addr, final int value )
   {
      words[addr] = value;
   }

   public int get ( final int addr )
   {
      return words[addr];
   }
}
