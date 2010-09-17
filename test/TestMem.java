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
      final MemStats.Stats stats1    = new MemStats.Stats();
      final MemStats.Stats stats2    = new MemStats.Stats();
      final int            pageSize  = 1024;
      final int            pageCount = 4;
      final int            lineSize  = 64;
      final int            lineCount = 1;
      final Mem mems[] = {
         new MemSimple(0),
         new MemSimple(1),
         new MemSimple(256),
         // new MemPaged(0,0), // nonpos pageSize
         // new MemPaged(0,1), // nonpos pageSize
         new MemPaged(1,0),
         new MemPaged(1,1),
         new MemPaged(1,256),
         new MemPaged(256,1),
         new MemPaged(16,16),
         new MemPaged(256,4),
         new MemPaged(4,256),
         new MemStats(new MemSimple(pageSize * pageCount),null,null),
         new MemStats(new MemSimple(pageSize * pageCount),stats1,null),
         new MemStats(new MemSimple(pageSize * pageCount),null,stats1),
         new MemStats(new MemSimple(pageSize * pageCount),stats1,stats2),
         new MemStats(new MemSimple(pageSize * pageCount),stats1,stats1),
         //new MemCached(new MemSimple(pageSize * pageCount),lineCount,lineSize),
         //new MemCached(new MemSimple(pageSize * pageCount),lineSize,lineCount),
      };

      Random rand = null;

      for ( int memid = 0; memid < mems.length; ++memid )
      {
         final Mem mem    = mems[memid];
         final int length = mem.length();
         log("memid " + memid + ":");
         log("  mem:    " + mem);
         log("  length: " + length);
         for ( int addr = 0; addr < length; ++addr )
         {
            final int value1 = length-addr;
            mem.set(addr,value1);
            final int value2 = mem.get(addr);
            assertEquals("addr " + addr,value1,value2);
         }
         for ( int addr = 0; addr < length; ++addr )
         {
            final int value1 = length-addr;
            final int value2 = mem.get(addr);
            assertEquals("addr " + addr,value1,value2);
         }
         for ( int addr = length-1; addr >= 0; --addr )
         {
            final int value1 = length-addr;
            final int value2 = mem.get(addr);
            assertEquals("addr " + addr,value1,value2);
         }

         rand = new Random(4321);
         final int[] addrs = new int[Math.min(128,length)];
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
