/**
 * IOBuffer.java
 *
 * A representation of hardware-level I/O.
 *
 * Because this is a test/support layer, unlike JhwScm this is allowed
 * to throw exceptions on misuse.  JhwScm is responsible for making
 * sure that never happens!
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

import java.util.Random;

public class IOBuffer
{
   private static final boolean verbose = false;

   private static final Random debugRand = new Random(11031978);

   public static class Stats
   {
      public int numInput  = 0;
      public int numOutput = 0;
      public int numPeek   = 0;
      public int numPop    = 0;
      public int numPush   = 0;
   }

   public final Stats global;
   public final Stats local;

   public final boolean VERBOSE;
   public final boolean DEBUG;

   private final byte[] buf;
   private int          start = 0;
   private int          end   = 0;
   private int          len   = 0;

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

   /**
    * Transfers up to len bytes from in[off..len-1] to buffer.
    *
    * @throws NullPointerException if buf is null
    *
    * @throws IndexOutOfBoundsException off or len are negative, or
    * off+len are larger than buf.length.
    *
    * @returns the number of bytes transferred from buf[off..n-1]
    */
   public int input ( final byte[] buf, int off, final int len ) 
   {
      if ( VERBOSE ) log("input(): " + off + "+" + len + " / " + buf.length);
      check(buf,off,len);
      final int max = DEBUG ? debugRand.nextInt(len+1) : len;
      if ( VERBOSE ) log("input max:  " + max + " / " + len);
      for ( int i = 0; i < max; ++i )
      {
         if ( isFull() )
         {
            return i;
         }
         final byte b = buf[off++];
         if ( VERBOSE ) log("input(): pushing byte " + b);
         if ( VERBOSE ) log("input(): pushing char " + (char)b);
         push(b);
         if ( null != local )  local.numInput++;
         if ( null != global ) global.numInput++;
      }
      return max;
   }

   /**
    * Transfers up to len bytes from the buffer to buf[off..len-1].
    *
    * @throws NullPointerException if buf is null
    *
    * @throws IndexOutOfBoundsException off or len are negative, or
    * off+len are larger than buf.length.
    *
    * @returns the number of bytes transferred into buf[off..n-1]
    */
   public int output ( final byte[] buf, int off, final int len ) 
   {
      if ( VERBOSE ) log("output(): " + off + "+" + len + " / " + buf.length);
      check(buf,off,len);
      final int max = DEBUG ? debugRand.nextInt(len+1) : len;
      if ( VERBOSE ) log("output max: " + max + " / " + len);
      for ( int i = 0; i < max; ++i )
      {
         if ( isEmpty() )
         {
            return i;
         }
         final byte b = pop();
         buf[off++] = b;
         if ( VERBOSE ) log("output(): popped byte " + b);
         if ( VERBOSE ) log("output(): popped char " + (char)b);
         if ( null != local )  local.numOutput++;
         if ( null != global ) global.numOutput++;
      }
      if ( VERBOSE ) log("output(): shifted: " + max);
      return max;
   }

   private static void check ( final byte[] buf, int off, final int len )
   {
      if ( null == buf )
      {
         throw new NullPointerException("null buf");
      }
      if ( 0 <= off && 0 <= len && buf.length >= (off+len) )
      {
         return;
      }
      final String stats = 
         "off " + off + " len " + len + " total " + buf.length;
      throw new IndexOutOfBoundsException(stats);
   }

   private static void log ( final Object obj )
   {
      System.out.println(obj);
   }
}
