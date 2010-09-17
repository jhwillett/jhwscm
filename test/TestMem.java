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

   public static void main ( final String[] argv )
      throws java.io.IOException
   {

      final int limit = 4 * 1024;
      final Mem mems[] = {
         new MemSimple(limit),
         //new MemStats(),
         //new MemPaged(),
         //new MemCached(),
      };

      Random rand = null;

      for ( final Mem mem : mems )
      {
         for ( int addr = 0; addr < limit; ++addr )
         {
            final int value1 = limit-addr;
            mem.set(addr,value1);
            final int value2 = mem.get(addr);
            assertEquals("addr " + addr,value1,value2);
         }
         for ( int addr = 0; addr < limit; ++addr )
         {
            final int value1 = limit-addr;
            final int value2 = mem.get(addr);
            assertEquals("addr " + addr,value1,value2);
         }
         for ( int addr = limit-1; addr >= 0; --addr )
         {
            final int value1 = limit-addr;
            final int value2 = mem.get(addr);
            assertEquals("addr " + addr,value1,value2);
         }

         rand = new Random(4321);
         final int[] addrs = new int[128];
         for ( int i = 0; i < addrs.length; ++i )
         {
            addrs[i] = i;
         }
         for ( int i = addrs.length-1; i > 0; --i )
         {
            final int j   = rand.nextInt(i+1);
            final int tmp = addrs[i];
            addrs[i]      = addrs[j];
            addrs[j]      = tmp;
         }
         rand = new Random(987);
         for ( int i = 0; i < addrs.length; ++i )
         {
            final int addr   = addrs[i];
            final int value1 = rand.nextInt();
            //log("set " + addr + " to " + value1);
            mem.set(addr,value1);
         }
         rand = new Random(987);
         for ( int i = 0; i < addrs.length; ++i )
         {
            final int addr   = addrs[i];
            final int value1 = rand.nextInt();
            final int value2 = mem.get(addr);
            //log("get " + addr + " to " + value2);
            assertEquals("addr " + addr,value1,value2);
         }
      }

   }

   private static void log ( final Object obj )
   {
      System.out.println(obj);
   }
}
