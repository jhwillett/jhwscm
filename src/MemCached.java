/**
 * MemCached.java
 *
 * A cache: sits in front of some other Mem.  
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class MemCached implements Mem
{
   private final Mem       main;
   private final int       lineSize;
   private final int[][]   lines;
   private final int[]     roots;
   private final boolean[] dirties;

   public MemCached ( final Mem main,
                      final int lineSize, 
                      final int lineCount )
   {
      if ( lineSize <= 0 )
      {
         throw new IllegalArgumentException("nonpos lineSize " + lineSize);
      }
      if ( lineCount < 0 )
      {
         throw new IllegalArgumentException("neg lineCount " + lineCount);
      }
      this.main     = main;
      this.lineSize = lineSize;
      this.lines    = new int[lineCount][];
      this.roots    = new int[lineCount];
      this.dirties  = new boolean[lineCount];
      for ( int i = 0; i < lineCount; ++i )
      {
         this.lines[i] = new int[lineSize];
      }
   }

   public int length ()
   {
      return main.length();
   }

   public void set ( final int addr, final int value )
   {
      throw new RuntimeException("unimplemented");
   }

   public int get ( final int addr )
   {
      final int root = addr / lineSize;
      final int off  = addr % lineSize;
      int line = findFreeLine(root);
      if ( -1 == line )
      {
         line = purgeLine();
         loadLine(root,line);
      }
      return lines[root][off];
   }

   private int findFreeLine ( final int root )
   {
      throw new RuntimeException("unimplemented");
   }

   private int purgeLine ()
   {
      throw new RuntimeException("unimplemented");
   }

   private int loadLine ( final int root, final int line )
   {
      throw new RuntimeException("unimplemented");
   }

   private int flushLine ( final int line )
   {
      throw new RuntimeException("unimplemented");
   }

   private int pcLoadLetter ()
   {
      throw new RuntimeException("What the fuck is PC LOAD LETTER?");
   }
}
