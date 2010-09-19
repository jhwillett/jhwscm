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

public class TestIOBuffer extends Util
{
   private static final boolean verbose = false;

   public static void main ( final String[] argv )
   {
      log("TestIOBuffer");

      // TODO: overflow, underflow, peek(), isFull(), isEmpty(), etc.

      final int[][] tests = {
         { 1, 0, 0 },

         { 1, 1,    0 },
         { 1, 1,    1 },
         { 1, 1,  127 },
         { 1, 1,  128 },
         { 1, 1,  255 },
         { 1, 1,  256 },
         { 1, 1,   -1 },
         { 1, 1,   -2 },
         { 1, 1, -127 },
         { 1, 1, -128 },
         { 1, 1, -255 },
         { 1, 1, -256 },

         { 1, 1, 0 },
         { 5, 1, 0 },
         { 5, 3, 0 },
         { 5, 5, 0 },
      };

      for ( int i = 0; i < tests.length; ++i )
      {
         final int[] test    = tests[i];
         final int byteCount = test[0];
         final int numOps    = test[1];
         final int mutation  = test[2];
         depth++;
         test(byteCount,numOps,mutation);
         depth--;
      }

      log("success");
   }

   public static void test ( final int byteCount,
                             final int numOps,
                             final int mutation )
   {
      log("test:");
      depth++;
      log("byteCount: " + byteCount);
      log("numOps:    " + numOps);
      log("mutation:  " + mutation);

      final IOBuffer iobuf = new IOBuffer(byteCount);
      for ( int i = 0; i < numOps; ++i )
      {
         final byte a = (byte)(i+mutation);
         iobuf.push(a);
      }
      for ( int i = 0; i < numOps; ++i )
      {
         final byte a = (byte)(i+mutation);
         final byte b = iobuf.pop();
         assertEquals(a, b);
      }
      depth--;
   }

}
