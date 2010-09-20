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

   public static final int     PORT_CLOSED       = -1;
   public static final int     BAD_ARG           = -2;

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

   private int scmDepth  = 0; // debug
   private int javaDepth = 0; // debug

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

      this.iobufs = new IOBuffer[] { 
         new IOBuffer(1024), 
         new IOBuffer(1024) 
      };
   }

   ////////////////////////////////////////////////////////////////////
   //
   // client control points
   //
   ////////////////////////////////////////////////////////////////////

   /**
    * Transfers up to len bytes from in[off..len-1] to the VM's input
    * port buffer.
    *
    * Never results in OUT_OF_MEMORY, FAILURE_SEMANTIC, etc.
    * PORT_CLOSED and BAD_ARG are the only specified failure modes.
    *
    * A return result of 0 frequently indicates more cycles in drive()
    * are needed to clear out the port buffers.
    *
    * @returns BAD_ARGS if any of the arguments are invalid,
    * PORT_CLOSED if close() has been called on the port, else the
    * number of bytes transferred from buf[off..n-1].
    */
   public int input ( final byte[] buf, int off, final int len ) 
   {
      if ( DEBUG ) javaDepth = 0;
      if ( VERBOSE ) log("input(): " + off + "+" + len + " / " + buf.length);
      if ( null == buf )
      {
         if ( VERBOSE ) log("input():  null arg");
         return BAD_ARG;
      }
      if ( off < 0 )
      {
         if ( VERBOSE ) log("input(): bad off: " + off);
         return BAD_ARG;
      }
      if ( len < 0 )
      {
         if ( VERBOSE ) log("input(): bad len: " + len);
         return BAD_ARG;
      }
      if ( off+len > buf.length )
      {
         if ( VERBOSE ) log("output(): " + off + "+" + len + " / " + buf.length);
         return BAD_ARG;
      }
      final IOBuffer iobuf = iobufs[0];
      if ( null == buf )
      {
         return PORT_CLOSED;
      }
      final int max = DEBUG ? debugRand.nextInt(len+1) : len;
      for ( int i = 0; i < max; ++i )
      {
         if ( iobuf.isFull() )
         {
            return i;
         }
         final byte b = buf[off++];
         if ( VERBOSE ) log("input(): pushing byte " + b);
         if ( VERBOSE ) log("input(): pushing char " + (char)b);
         iobuf.push(b);
         if ( PROFILE ) local.numInput++;
         if ( PROFILE ) global.numInput++;
      }
      return max;
   }

   /**
    * Transfers up to len bytes from the VM's output port buffer and
    * copies them to buf[off..len-1].
    *
    * Never results in OUT_OF_MEMORY, FAILURE_SEMANTIC, etc.
    * PORT_CLOSED and BAD_ARG are the only specified failure modes.
    *
    * A return result of 0 frequently indicates more cycles in drive()
    * are needed to clear out the port buffers.
    *
    * @returns BAD_ARGS if any of the arguments are invalid,
    * PORT_CLOSED if close() has been called on the port, else the
    * number of bytes transferred into buf[off..n-1].
    */
   public int output ( final byte[] buf, int off, final int len ) 
   {
      if ( DEBUG ) javaDepth = 0;
      if ( VERBOSE ) log("output(): " + off + "+" + len + " / " + buf.length);
      if ( null == buf )
      {
         if ( VERBOSE ) log("output(): null arg");
         return BAD_ARG;
      }
      if ( off < 0 )
      {
         if ( VERBOSE ) log("output(): bad off: " + off);
         return BAD_ARG;
      }
      if ( len < 0 )
      {
         if ( VERBOSE ) log("output(): bad len: " + len);
         return BAD_ARG;
      }
      if ( off+len > buf.length )
      {
         if ( VERBOSE ) log("output(): " + off + "+" + len + " / " + buf.length);
         return BAD_ARG;
      }
      final IOBuffer iobuf = iobufs[1];
      if ( null == buf )
      {
         return PORT_CLOSED;
      }
      final int max = DEBUG ? debugRand.nextInt(len+1) : len;
      for ( int i = 0; i < max; ++i )
      {
         if ( iobuf.isEmpty() )
         {
            if ( 0 == i )
            {
               if ( VERBOSE ) log("output(): empty and done");
               return -1;
            }
            else
            {
               if ( VERBOSE ) log("output(): empty, but shifted: " + i);
               return i;
            }
         }
         final byte b = iobuf.pop();
         buf[off++] = b;
         if ( VERBOSE ) log("output(): popped byte " + b);
         if ( VERBOSE ) log("output(): popped char " + (char)b);
         if ( PROFILE ) local.numOutput++;
         if ( PROFILE ) global.numOutput++;
      }
      if ( VERBOSE ) log("output(): shifted: " + max);
      return max;
   }

   ////////////////////////////////////////////////////////////////////
   //
   // logging and debug utilities
   //
   ////////////////////////////////////////////////////////////////////

   // scmDepth and javaDepth are ONLY used for debug: they are *not*
   // sanctioned VM state.
   //
   private void log ( final Object msg )
   {
      if ( !VERBOSE ) return;
      final int lim = (scmDepth + javaDepth);
      for (int i = 0; i < lim; ++i)
      {
         System.out.print("  ");
      }
      System.out.println(msg);
   }
}
