/**
 * Test harness for IOBuffers.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

import java.util.Random;

public class TestIOBuffer extends Util
{
   private static boolean VERBOSE = false;
   private static boolean DEBUG   = true;

   private static final Random debugRand = new Random(1234);

   private static IOBuffer newBuf ()
   {
      return new IOBuffer(64,VERBOSE,DEBUG,null,null);
   }

   public static void main ( final String[] argv )
   {
      log("TestIOBuffer");

      // TODO: push(), peek(), pop()

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
         final int[] test      = tests[i];
         final int   byteCount = test[0];
         final int   numOps    = test[1];
         final int   mutation  = test[2];
         depth++;
         test(byteCount,numOps,mutation);
         depth--;
      }

      {
         final IOBuffer buf = newBuf();
         assertEquals(false,buf.isClosed());
         buf.open();
         assertEquals(false,buf.isClosed());
         buf.open();
         buf.open();
         assertEquals(false,buf.isClosed());
         buf.close();
         assertEquals(true,buf.isClosed());
         buf.close();
         buf.close();
         assertEquals(true,buf.isClosed());
         buf.open();
         assertEquals(false,buf.isClosed());
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

      // open() and close() have no effect on the buffer's
      // buffer-nature or communication-line nature, but for good
      // measure we scatter some calls here at random.

      final IOBuffer iobuf = new IOBuffer(byteCount,VERBOSE,DEBUG,null,null);
      assertEquals( true, iobuf.isEmpty());               // empty
      for ( int i = 0; i < numOps; ++i )
      {
         final byte a = (byte)(i+mutation);
         iobuf.push(a);
         final int rand = debugRand.nextInt(100);
         if ( rand < 9 )
         {
            iobuf.close();
         }
         else if ( rand < 19 )
         {
            iobuf.open();
         }
      }
      assertEquals( byteCount == numOps, iobuf.isFull()); // full
      for ( int i = 0; i < numOps; ++i )
      {
         final byte a = (byte)(i+mutation);
         final byte b = iobuf.pop();
         assertEquals(a, b);
         final int rand = debugRand.nextInt(100);
         if ( rand < 9 )
         {
            iobuf.close();
         }
         else if ( rand < 19 )
         {
            iobuf.open();
         }
      }
      assertEquals( true, iobuf.isEmpty());               // empty
      depth--;
   }

}
