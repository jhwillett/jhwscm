/**
 * Test harness for IOBuffers.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class TestIOBuffer extends Util
{
   private static boolean PROFILE = true;
   private static boolean VERBOSE = false;
   private static boolean DEBUG   = true;

   private static IOBuffer newBuf ()
   {
      return new IOBuffer(64,PROFILE,VERBOSE,DEBUG);
   }

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

      assertEquals(IOBuffer.BAD_ARG,newBuf().input(null,0,0));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(null,0,0));

      assertEquals(IOBuffer.BAD_ARG,newBuf().input(new byte[0],-1,0));
      assertEquals(IOBuffer.BAD_ARG,newBuf().input(new byte[0],0,-1));
      assertEquals(IOBuffer.BAD_ARG,newBuf().input(new byte[0],2,3));
      assertEquals(IOBuffer.BAD_ARG,newBuf().input(new byte[0],3,2));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(new byte[0],-1,0));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(new byte[0],-1,0));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(new byte[0],0,-1));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(new byte[0],0,-1));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(new byte[0],2,3));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(new byte[0],3,2));
      assertEquals(IOBuffer.BAD_ARG,newBuf().output(new byte[3],2,3));
      assertEquals(0,               newBuf().input(new byte[0],0,0));
      assertEquals(0,               newBuf().input(new byte[0],0,0));
      assertEquals(0,               newBuf().input(new byte[0],0,0));
      assertEquals(0,               newBuf().input(new byte[1],1,0));
      assertEquals(0,               newBuf().output(new byte[0],0,0));
      
      {
         final int code = newBuf().output(new byte[5],2,3);
         if ( -1 != code && 0 != code )
         {
            throw new RuntimeException("output() out of spec");
         }
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

      final IOBuffer iobuf = new IOBuffer(byteCount,PROFILE,VERBOSE,DEBUG);
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
