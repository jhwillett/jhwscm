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
   final byte[] buf   = new byte[512];
   int          start = 0;
   int          end   = 0;
   int          len   = 0;

   public IOBuffer ()
   {
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
      return buf[start];
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
      end += 1;
      end %= buf.length;
      len += 1;
      buf[end] = value;
   }
}
