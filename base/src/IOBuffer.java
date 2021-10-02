/**
 * IOBuffer.java
 *
 * A representation of hardware-level I/O.
 *
 * Because this is a test/support layer, unlike JhwScm this is allowed
 * to throw exceptions on misuse.  JhwScm is responsible for making
 * sure that never happens!
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
 */

import java.util.Random;

public class IOBuffer
{
   private static final boolean verbose = false;

   private static final Random debugRand = new Random(11031978);

   public static class Stats
   {
      public int numPeek   = 0;
      public int numPop    = 0;
      public int numPush   = 0;
   }

   public final Stats global;
   public final Stats local;

   public final boolean VERBOSE;
   public final boolean DEBUG;

   private final byte[] buf;
   private int          start  = 0;
   private int          end    = 0;
   private int          len    = 0;
   private boolean      closed = false;

   /**
    * IOBuffer starts in an open state.
    */
   public IOBuffer ( final int     byteCount,
                     final boolean VERBOSE, 
                     final boolean DEBUG,
                     final Stats   global, 
                     final Stats   local )
   {
      this.VERBOSE = VERBOSE;
      this.DEBUG   = DEBUG;
      this.global  = global;
      this.local   = local;
      if ( byteCount <= 0 )
      {
         throw new IllegalArgumentException("nonpos byteCount " + byteCount);
      }
      this.buf = new byte[byteCount];
   }

   /**
    * @returns true if the buffer is empty
    */
   public boolean isEmpty ()
   {
      return 0 == len;
   }

   /**
    * @returns true if the buffer is full
    */
   public boolean isFull ()
   {
      return start == end && ( 0 != len );
   }

   /**
    * close(), open() and isClosed() are only advisory: the bit shared
    * by them is more of an out-of-band communication between a
    * producer and a consumer than a change in the fundamental state
    * or meaning of the buffer.
    *
    * I/O can still proceed on a closed IOBuffer without error.
    *
    * @returns true if the buffer is closed, false otherwise
    */
   public boolean isClosed ()
   {
      return closed;
   }

   public void close ()
   {
      if ( VERBOSE ) log("close()");
      closed = true;
   }

   public void open ()
   {
      if ( VERBOSE ) log("open()");
      closed = false;
   }

   /**
    * Retrieves the next value at the front of the buffer, without
    * removing that value.
    *
    * @throws SegFault if isEmpty()
    *
    * @returns the next value in the buffer
    */
   public byte peek ()
   {
      if ( null != local )  local.numPeek++;
      if ( null != global ) global.numPeek++;
      if ( isEmpty() )
      {
         throw new SegFault("peek() when isEmpty()");
      }
      final byte value = buf[start];
      if ( verbose )
      {
         log("peek():");
         log("  start: " + start);
         log("  end:   " + end);
         log("  len:   " + len);
         log("  value: " + value);
      }
      return value;
   }

   /**
    * Retrieves the next value at the front of the buffer, removing
    * that value.
    *
    * @throws SegFault if isEmpty()
    *
    * @returns the next value in the buffer
    */
   public byte pop ()
   {
      if ( null != local )  local.numPop++;
      if ( null != global ) global.numPop++;
      if ( isEmpty() )
      {
         throw new SegFault("pop() when isEmpty()");
      }
      final byte value = buf[start];
      if ( verbose )
      {
         log("pop():");
         log("  start: " + start);
         log("  end:   " + end);
         log("  len:   " + len);
         log("  value: " + value);
      }
      start += 1;
      start %= buf.length;
      len   -= 1;
      return value;
   }

   /**
    * Queues a value at the end of the buffer.
    *
    * @param value the value to be stored in the buffer
    *
    * @throws SegFault if isFull()
    */
   public void push ( final byte value )
   {
      if ( null != local )  local.numPush++;
      if ( null != global ) global.numPush++;
      if ( isFull() )
      {
         throw new SegFault("push() when isFull()");
      }
      if ( verbose )
      {
         log("push():");
         log("  start: " + start);
         log("  end:   " + end);
         log("  len:   " + len);
         log("  value: " + value);
      }
      buf[end] = value;
      end += 1;
      end %= buf.length;
      len += 1;
   }

   private static void log ( final Object obj )
   {
      System.out.println(obj);
   }
}
