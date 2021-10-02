/**
 * Test harness for Mems.
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
 */

import java.util.Random;

public class TestMem extends Util
{
   private static final boolean verbose = false;

   public static void main ( final String[] argv )
   {
      log("TestMem:");
      depth++;

      try
      {
         new MemSimple(-1);
         fail("MemSimple out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }

      try
      {
         new MemPaged(0,0);
         fail("MemPaged out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }
      try
      {
         new MemPaged(0,1);
         fail("MemPaged out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }
      try
      {
         new MemPaged(1,-1);
         fail("MemPaged out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }

      try
      {
         new MemCached(null,0,0);
         fail("MemCached out of spec");
      }
      catch ( NullPointerException expected )
      {
      }

      try
      {
         new MemCached(new MemSimple(1024),0,0);
         fail("MemCached out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }

      try
      {
         new MemCached(new MemSimple(1024),1,0);
         fail("MemCached out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }

      try
      {
         new MemCached(new MemSimple(1024),0,1);
         fail("MemCached out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }

      try
      {
         new MemCached(new MemSimple(1024),13,1);
         fail("MemCached out of spec");
      }
      catch ( IllegalArgumentException expected )
      {
      }

      final MemStats.Stats stats1 = new MemStats.Stats();
      final MemStats.Stats stats2 = new MemStats.Stats();

      test(new MemSimple(0));
      test(new MemSimple(1));
      test(new MemSimple(256));

      test(new MemPaged(1,0));
      test(new MemPaged(1,1));
      test(new MemPaged(1,256));
      test(new MemPaged(256,1));
      test(new MemPaged(16,16));
      test(new MemPaged(256,4));
      test(new MemPaged(4,256));

      test(new MemStats(new MemSimple(0),null,null));
      test(new MemStats(new MemSimple(0),stats1,null));
      test(new MemStats(new MemSimple(0),null,stats1));
      test(new MemStats(new MemSimple(0),stats1,stats2));
      test(new MemStats(new MemSimple(0),stats1,stats1));

      test(new MemStats(new MemSimple(1),null,null));
      test(new MemStats(new MemSimple(1),stats1,null));
      test(new MemStats(new MemSimple(1),null,stats1));
      test(new MemStats(new MemSimple(1),stats1,stats2));
      test(new MemStats(new MemSimple(1),stats1,stats1));

      test(new MemStats(new MemSimple(64),null,null));
      test(new MemStats(new MemSimple(64),stats1,null));
      test(new MemStats(new MemSimple(64),null,stats1));
      test(new MemStats(new MemSimple(64),stats1,stats2));
      test(new MemStats(new MemSimple(64),stats1,stats1));

      test(new MemCached(new MemSimple(0),1,1));
      test(new MemCached(new MemSimple(0),16,16));

      test(new MemCached(new MemSimple(1),1,1));

      test(new MemCached(new MemSimple(256),1,1));

      test(new MemCached(new MemSimple(256),1,2));
      test(new MemCached(new MemSimple(256),2,1));
      test(new MemCached(new MemSimple(256),2,2));
      test(new MemCached(new MemSimple(256),1,4));
      test(new MemCached(new MemSimple(256),4,1));
      test(new MemCached(new MemSimple(256),1,8));
      test(new MemCached(new MemSimple(256),8,1));
      test(new MemCached(new MemSimple(256),8,2));

      test(new MemCached(new MemSimple(256),16,16));

      test(new MemCached(new MemSimple(1024),1,1));
      test(new MemCached(new MemSimple(1024),2,1));

      test(new MemCached(new MemSimple(256),2,16));
      test(new MemCached(new MemSimple(256),16,2));

      test(new MemCached(new MemSimple(1024),512,16));

      test(new MemCached(new MemSimple(256),16,512));

      test(new MemCached(new MemSimple(256),2,2));
      test(new MemCached(new MemSimple(256),4,4));
      test(new MemCached(new MemSimple(256),8,8));
      test(new MemCached(new MemSimple(256),16,16));

      log("success");
   }

   private static void test ( final Mem mem )
   {
      //log("mem:      " + mem);

      final int length = mem.length();
      //log("  length: " + length);

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

      Random debugRand = null;
      debugRand = new Random(4321);
      final int[] addrs = new int[Math.min(128,length)];
      for ( int i = 0; i < addrs.length; ++i )
      {
         addrs[i] = i;
      }
      for ( int i = addrs.length-1; i > 0; --i )
      {
         final int j   = debugRand.nextInt(i+1);
         final int tmp = addrs[i];
         addrs[i]      = addrs[j];
         addrs[j]      = tmp;
      }
      debugRand = new Random(987);
      for ( int i = 0; i < addrs.length; ++i )
      {
         final int addr   = addrs[i];
         final int value1 = debugRand.nextInt();
         //log("set " + addr + " to " + value1);
         mem.set(addr,value1);
      }
      debugRand = new Random(987);
      for ( int i = 0; i < addrs.length; ++i )
      {
         final int addr   = addrs[i];
         final int value1 = debugRand.nextInt();
         final int value2 = mem.get(addr);
         //log("get " + addr + " to " + value2);
         assertEquals("addr " + addr,value1,value2);
      }
   }
}
