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
         mach.ioBuf(-1);
         throw new RuntimeException("Machine.ioBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.ioBuf(num);
         throw new RuntimeException("Machine.ioBuf() out of spec"); 
      }
      catch ( IndexOutOfBoundsException expected )
      {
      }
      try
      {
         mach.ioBuf(num+1);
         throw new RuntimeException("Machine.ioBuf() out of spec"); 
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
         final IOBuffer buf = mach.ioBuf(i);
         if ( null == buf )
         {
            throw new RuntimeException("Machine.ioBuf() out of spec"); 
         }
      }
      if ( 0 < num )
      {
         mach.closeIoBuf(0);
         if ( null != mach.ioBuf(0) )
         {
            throw new RuntimeException("Machine.ioBuf() out of spec"); 
         }
         mach.closeIoBuf(0);
         mach.closeIoBuf(0);
         mach.closeIoBuf(0);
         if ( null != mach.ioBuf(0) )
         {
            throw new RuntimeException("Machine.ioBuf() out of spec"); 
         }
      }
   }
}
