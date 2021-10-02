/**
 * A machine.
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
 */

import java.util.Random;

public class Machine
{
   public static final boolean USE_PAGED_MEM     = false;
   public static final int     PAGE_SIZE         = 1024;
   public static final int     PAGE_COUNT        = 6; // need 512 unopt

   public static final boolean USE_CACHED_MEM    = true;
   public static final int     LINE_SIZE         = 16;
   public static final int     LINE_COUNT        = 16;

   private static final int    IO_STRESS_PERCENT = 13;
   private static final Random debugRand         = new Random(11031978);

   public static class Stats
   {
      public final MemStats.Stats   heapStats     = new MemStats.Stats();
      public final MemStats.Stats   regStats      = new MemStats.Stats();
      public final MemCached.Stats  cacheStats    = new MemCached.Stats();
      public final MemStats.Stats   cacheTopStats = new MemStats.Stats();
      public IOBuffer.Stats[]       ioStats       = null;

      private void ensureIoStatsCapacity ( final int capacity )
      {
         if ( null != ioStats && capacity <= ioStats.length ) return;
         final IOBuffer.Stats[] newIoStats = new IOBuffer.Stats[capacity];
         if ( null != ioStats )
         {
            for ( int i = 0; i < ioStats.length; ++i )
            {
               newIoStats[i] = ioStats[i];
            }
         }
         ioStats = newIoStats;
         for ( int i = ioStats.length - 1; i >= 0; --i )
         {
            if ( null != ioStats[i] ) break;
            ioStats[i] = new IOBuffer.Stats();
         }
      }
   }

   public static final Stats global = new Stats();
   public        final Stats local  = new Stats();

   public final boolean PROFILE;
   public final boolean VERBOSE;
   public final boolean DEBUG;

   public final Mem        reg;
   public final Mem        heap;
   public final IOBuffer[] iobufs;

   public Machine ( final boolean PROFILE, 
                    final boolean VERBOSE, 
                    final boolean DEBUG,
                    final int...  ioBufSizes )
   {
      this.PROFILE = PROFILE;
      this.VERBOSE = VERBOSE;
      this.DEBUG   = DEBUG;

      Mem mem = null;

      mem = new MemSimple(32);
      if ( PROFILE )
      {
         mem = new MemStats(mem,global.regStats,local.regStats);
      }
      this.reg = mem;

      if ( USE_PAGED_MEM )
      {
         mem = new MemPaged(PAGE_SIZE, PAGE_COUNT);
      }
      else
      {
         //  16 kcells:  0.5 sec
         //  32 kcells:  0.6 sec
         //  64 kcells:  1.0 sec
         // 128 kcells:  4.2 sec  *** big nonlinearity up
         // 256 kcells: 10.6 sec  *** small nonlinearity up
         // 512 kcells: 11.5 sec  *** small nonlinearity down
         mem = new MemSimple(PAGE_SIZE * PAGE_COUNT);
      }
      if ( PROFILE )
      {
         mem = new MemStats(mem,global.heapStats,local.heapStats);
      }
      if ( USE_CACHED_MEM )
      {
         final MemCached.Stats glo;
         final MemCached.Stats loc;
         if ( PROFILE )
         {
            glo = global.cacheStats;
            loc = local.cacheStats;
         }
         else
         {
            glo = null;
            loc = null;
         }
         mem = new MemCached(mem,LINE_SIZE,LINE_COUNT,glo,loc);
         if ( PROFILE )
         {
            mem = new MemStats(mem,global.cacheTopStats,local.cacheTopStats);
         }
      }
      this.heap = mem;

      if ( PROFILE )
      {
         global.ensureIoStatsCapacity(ioBufSizes.length);
         local.ensureIoStatsCapacity(ioBufSizes.length);
      }
      this.iobufs = new IOBuffer[ioBufSizes.length];
      for ( int i = 0; i < ioBufSizes.length; ++i )
      {
         final IOBuffer.Stats glo;
         final IOBuffer.Stats loc;
         if ( PROFILE )
         {
            glo = global.ioStats[i];
            loc = local.ioStats[i];
         }
         else
         {
            glo = null;
            loc = null;
         }
         this.iobufs[i] = new IOBuffer(ioBufSizes[i],VERBOSE,DEBUG,glo,loc);
      }
   }

   ////////////////////////////////////////////////////////////////////
   //
   // client control points
   //
   ////////////////////////////////////////////////////////////////////

   /**
    * Valid ioBufIds are in 0 .. numIoBufs()-1.
    *
    * @returns the number of buffers
    */
   public int numIoBufs ()
   {
      return iobufs.length;
   }

   /**
    * @throws IndexOutOfBoundsException if n < 0 or n >= numIoBufs()
    *
    * @returns the buffer at ioBufId
    */
   public IOBuffer getIoBuf ( final int ioBufId )
   {
      if ( ioBufId < 0 || ioBufId >= iobufs.length )
      {
         final String msg = "ioBufId " + ioBufId + " / " + iobufs.length;
         throw new IndexOutOfBoundsException(msg);
      }
      return iobufs[ioBufId];
   }

   ////////////////////////////////////////////////////////////////////
   //
   // logging and debug utilities
   //
   ////////////////////////////////////////////////////////////////////

   private void log ( final Object msg )
   {
      if ( !VERBOSE ) return;
      final int lim = 0;
      for (int i = 0; i < lim; ++i)
      {
         System.out.print("  ");
      }
      System.out.println(msg);
   }
}
