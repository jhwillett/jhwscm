/**
 * Test harness for Mems.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.io.IOException;

public class TestMem
{
   private static final boolean verbose = false;

   private static final Random debugRand = new Random(4321);

   public static void main ( final String[] argv )
      throws java.io.IOException
   {

      final Mem mems[] = {
         new MemSimple(1024),
         //new MemStats(),
         //new MemPaged(),
         //new MemCached(),
      };

      for ( final Mem mem : mems )
      {
         mem.set(10,10);
         assertEquals(10,mem.get(10));
      }
   }      
}
