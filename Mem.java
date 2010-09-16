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
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public interface Mem
{
   /**
    * @returns the largest addressable word
    */
   public int length (); 

   /**
    * Sets the word at add to the value specified.
    */
   public void set ( final int addr, final int value );

   /**
    * Retrieves the value previously set at addr.
    *
    * If adder has never been set, results are undefined.
    * Implementations are allowed to throw, return a distinguished
    * value, or even return inconsistent variable random data.
    */
   public int get ( final int addr );
}
