/**
 * Test harness for Machine.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class TestMachine extends Util
{
   private static boolean REPORT  = true;
   private static boolean PROFILE = true;
   private static boolean VERBOSE = false;
   private static boolean DEBUG   = true;

   private static Machine newMachine ()
   {
      final Machine machine = new Machine(PROFILE,VERBOSE,DEBUG);
      return machine;
   }

   private static void ioEdgeCases ()
   {
      assertEquals(JhwScm.BAD_ARG,newMachine().input(null,0,0));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(null,0,0));

      assertEquals(JhwScm.BAD_ARG,newMachine().input(new byte[0],-1,0));
      assertEquals(JhwScm.BAD_ARG,newMachine().input(new byte[0],0,-1));
      assertEquals(JhwScm.BAD_ARG,newMachine().input(new byte[0],2,3));
      assertEquals(JhwScm.BAD_ARG,newMachine().input(new byte[0],3,2));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(new byte[0],-1,0));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(new byte[0],-1,0));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(new byte[0],0,-1));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(new byte[0],0,-1));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(new byte[0],2,3));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(new byte[0],3,2));
      assertEquals(JhwScm.BAD_ARG,newMachine().output(new byte[3],2,3));

      assertEquals(0,             newMachine().input(new byte[0],0,0));
      assertEquals(0,             newMachine().input(new byte[0],0,0));
      assertEquals(0,             newMachine().input(new byte[0],0,0));
      assertEquals(0,             newMachine().input(new byte[1],1,0));
      assertEquals(0,             newMachine().output(new byte[0],0,0));
      
      {
         final int code = newMachine().output(new byte[5],2,3);
         if ( -1 != code && 0 != code )
         {
            throw new RuntimeException("output() out of spec");
         }
      }
   }

   public static void main ( final String[] argv )
   {
      log("TestMachine");
      depth++;

      ioEdgeCases();

      log("TODO: write me");
   }
}
