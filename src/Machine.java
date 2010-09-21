/**
 * A machine.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
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
      public final MemStats.Stats  heapStats     = new MemStats.Stats();
      public final MemStats.Stats  regStats      = new MemStats.Stats();
      public final MemCached.Stats cacheStats    = new MemCached.Stats();
      public final MemStats.Stats  cacheTopStats = new MemStats.Stats();
      public       int             numInput      = 0;
      public       int             numOutput     = 0;
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
                    final boolean DEBUG )
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

      final int numIoBufs = 2;
      final int ioBufSize = 1024;
      this.iobufs = new IOBuffer[numIoBufs];
      for ( int i = 0; i < numIoBufs; ++i )
      {
         this.iobufs[i] = new IOBuffer(ioBufSize,PROFILE,VERBOSE,DEBUG);
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
    * @returns null if the buffer at ioBufId has been close()d, else
    * the IOBuffer at ioBufId.
    */
   public IOBuffer ioBuf ( final int ioBufId )
   {
      if ( ioBufId < 0 || ioBufId >= iobufs.length )
      {
         final String msg = "ioBufId " + ioBufId + " / " + iobufs.length;
         throw new IndexOutOfBoundsException(msg);
      }
      return iobufs[ioBufId];
   }

   /**
    * Closes the buffer at ioBufId.
    *
    * @throws IndexOutOfBoundsException if n < 0 or n >= numIoBufs()
    */
   public void closeIoBuf ( final int ioBufId )
   {
      if ( ioBufId < 0 || ioBufId >= iobufs.length )
      {
         final String msg = "ioBufId " + ioBufId + " / " + iobufs.length;
         throw new IndexOutOfBoundsException(msg);
      }
      iobufs[ioBufId] = null;
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
