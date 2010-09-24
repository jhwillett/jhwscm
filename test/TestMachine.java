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

   public static void main ( final String[] argv )
   {
      log("TestMachine");
      depth++;

      final Machine mach = newMachine();
      final int num = mach.numIoBufs();
      try
      {
         mach.getIoBuf(-1);
         fail("Machine.getIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.getIoBuf(num);
         fail("Machine.getIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.getIoBuf(num+1);
         fail("Machine.getIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.closeIoBuf(-1);
         fail("Machine.closeIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.closeIoBuf(num);
         fail("Machine.closeIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.closeIoBuf(num+1);
         fail("Machine.closeIoBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }

      for ( int i = 0; i < num; ++i )
      {
         final IOBuffer buf = mach.getIoBuf(i);
         if ( null == buf )
         {
            fail("Machine.getIoBuf() out of spec"); 
         }
      }
      if ( 0 < num )
      {
         mach.closeIoBuf(0);
         if ( null != mach.getIoBuf(0) )
         {
            fail("Machine.getIoBuf() out of spec"); 
         }
         mach.closeIoBuf(0);
         mach.closeIoBuf(0);
         mach.closeIoBuf(0);
         if ( null != mach.getIoBuf(0) )
         {
            fail("Machine.getIoBuf() out of spec"); 
         }
      }
   }
}
