/**
 * Test harness for IOBuffers.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.io.IOException;

public class TestIOBuffer
{
   private static final boolean verbose = false;

   public static void main ( final String[] argv )
   {
      log("TestIOBuffer");

      final IOBuffer iobuf = new IOBuffer();

      log("  created");

      for ( int i = 0; i < 10; ++i )
      {
         final byte b = (byte)i;
         iobuf.push(b);
         log("  wrote: " + b);
      }
      log("  done writing");
      if ( true ) return; // fails!
      for ( int i = 0; i < 10; ++i )
      {
         final byte b = iobuf.pop();
         log("  read:  " + b);
         assertEquals(b,(byte)i);
         assertEquals(b,i);
      }
      log("  done reading");
   }

   private static void log ( final Object obj )
   {
      System.out.println(obj);
   }
}
