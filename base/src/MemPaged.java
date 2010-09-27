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
   private final int     length;
   private final int     pageSize;
   private final int[][] pages;

   public MemPaged ( final int pageSize, final int pageCount )
   {
      if ( pageSize <= 0 )
      {
         throw new IllegalArgumentException("nonpos pageSize " + pageSize);
      }
      if ( pageCount < 0 )
      {
         throw new IllegalArgumentException("neg pageCount " + pageCount);
      }
      this.length   = pageSize * pageCount;
      this.pageSize = pageSize;
      this.pages    = new int[pageCount][];
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
