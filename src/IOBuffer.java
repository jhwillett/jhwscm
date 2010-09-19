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

public class IOBuffer
{
   private static final boolean verbose = false;

   private final byte[] buf;
   private int          start = 0;
   private int          end   = 0;
   private int          len   = 0;

   public IOBuffer ( final int byteCount )
   {
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
