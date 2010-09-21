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
      assertEquals(Machine.BAD_ARG,newMachine().input(null,0,0));
      assertEquals(Machine.BAD_ARG,newMachine().output(null,0,0));

      assertEquals(Machine.BAD_ARG,newMachine().input(new byte[0],-1,0));
      assertEquals(Machine.BAD_ARG,newMachine().input(new byte[0],0,-1));
      assertEquals(Machine.BAD_ARG,newMachine().input(new byte[0],2,3));
      assertEquals(Machine.BAD_ARG,newMachine().input(new byte[0],3,2));
      assertEquals(Machine.BAD_ARG,newMachine().output(new byte[0],-1,0));
      assertEquals(Machine.BAD_ARG,newMachine().output(new byte[0],-1,0));
      assertEquals(Machine.BAD_ARG,newMachine().output(new byte[0],0,-1));
      assertEquals(Machine.BAD_ARG,newMachine().output(new byte[0],0,-1));
      assertEquals(Machine.BAD_ARG,newMachine().output(new byte[0],2,3));
      assertEquals(Machine.BAD_ARG,newMachine().output(new byte[0],3,2));
      assertEquals(Machine.BAD_ARG,newMachine().output(new byte[3],2,3));

      assertEquals(0,              newMachine().input(new byte[0],0,0));
      assertEquals(0,              newMachine().input(new byte[0],0,0));
      assertEquals(0,              newMachine().input(new byte[0],0,0));
      assertEquals(0,              newMachine().input(new byte[1],1,0));
      assertEquals(0,              newMachine().output(new byte[0],0,0));
      
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

      final Machine mach = newMachine();
      final int num = mach.numIoBufs();
      try
      {
         mach.iobuf(-1);
         throw new RuntimeException("Machine.iobuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.iobuf(num);
         throw new RuntimeException("Machine.iobuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.iobuf(num+1);
         throw new RuntimeException("Machine.iobuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.closeIoBuf(-1);
         throw new RuntimeException("Machine.closeIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.closeIoBuf(num);
         throw new RuntimeException("Machine.closeIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.closeIoBuf(num+1);
         throw new RuntimeException("Machine.closeIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }

      for ( int i = 0; i < num; ++i )
      {
         final IOBuffer buf = mach.iobuf(i);
         if ( null == buf )
         {
            throw new RuntimeException("Machine.iobuf() out of spec"); 
         }
      }
      if ( 0 < num )
      {
         mach.closeIoBuf(0);
         if ( null != mach.iobuf(0) )
         {
            throw new RuntimeException("Machine.iobuf() out of spec"); 
         }
         mach.closeIoBuf(0);
         mach.closeIoBuf(0);
         mach.closeIoBuf(0);
         if ( null != mach.iobuf(0) )
         {
            throw new RuntimeException("Machine.iobuf() out of spec"); 
         }
      }
   }
}
