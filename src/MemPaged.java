/**
 * MemPaged.java
 *
 * A more complicated implementation of Mem.  
 *
 * Pages are fixed-size Java int[], but a page table is maintained and
 * allocation of each page is deferred until it is accessed.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class MemPaged implements Mem
{
   private final int length;
   private final int numPages;
   private final int pageSize;

   private final int[][] pages;

   public MemPaged ( final int length, final int pageSize )
   {
      if ( length < 0 )
      {
         throw new IllegalArgumentException("neg length " + length);
      }
      if ( pageSize <= 0 )
      {
         throw new IllegalArgumentException("nonpos pageSize " + pageSize);
      }
      if ( 0 != length%pageSize )
      {
         throw new IllegalArgumentException("incompatible");
      }
      this.length   = length;
      this.pageSize = pageSize;
      this.numPages = length/pageSize;
      this.pages    = new int[numPages][];
   }

   public int length ()
   {
      return length;
   }

   public void set ( final int addr, final int value )
   {
      final int pageid = addr/pageSize;
      final int offset = addr%pageSize;
      getpage(pageid)[offset] = value;
   }

   public int get ( final int addr )
   {
      final int pageid = addr/pageSize;
      final int offset = addr%pageSize;
      return getpage(pageid)[offset];
   }

   private int[] getpage ( final int pageid )
   {
      if ( null == pages[pageid] )
      {
         pages[pageid] = new int[pageSize];
      }
      return pages[pageid];
   }
}
