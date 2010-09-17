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
   private final int       lineCount;
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
      if ( lineCount <= 0 )
      {
         throw new IllegalArgumentException("nonpos lineCount " + lineCount);
      }
      if ( 0 != main.length()%lineSize )
      {
         throw new IllegalArgumentException("nonmodulo lineSize " + 
                                            lineSize + 
                                            " vs " + 
                                            main.length());
      }
      this.main      = main;
      this.lineSize  = lineSize;
      this.lineCount = lineCount;
      this.lines     = new int[lineCount][];
      this.roots     = new int[lineCount];
      this.dirties   = new boolean[lineCount];
      for ( int i = 0; i < lineCount; ++i )
      {
         this.lines[i]   = new int[lineSize];
         this.roots[i]   = -1;
         this.dirties[i] = false;
      }
   }

   public int length ()
   {
      return main.length();
   }

   public void set ( final int addr, final int value )
   {
      //log("set:     " + addr + "     " + value);
      final int root = addr / lineSize;
      final int off  = addr % lineSize;
      final int line = getRootIntoLine(root);
      lines[line][off] = value;
      dirties[line]    = true;
   }

   public int get ( final int addr )
   {
      final int root  = addr / lineSize;
      final int off   = addr % lineSize;
      final int line  = getRootIntoLine(root);
      final int value = lines[line][off];
      //log("get:     " + addr + "     " + value);
      return value;
   }

   private int getRootIntoLine ( final int root )
   {
      int line = -1;
      for ( int i = 0; i < lineCount; ++i )
      {
         if ( root == this.roots[i] )
         {
            // TODO: LRU stuff?
            ////log("  hit:   " + root + " in   " + i);
            return i;
         }
         if ( -1 == this.roots[i] )
         {
            line = i;
         }
      }

      if ( -1 == line )
      {
         line = 0; // TODO: LRU stuff?
         if ( dirties[line] )
         {
            //log("  flush: " + roots[line] + " from " + line);
            for ( int i = 0; i < lineSize; ++i )
            {
               main.set(roots[line]+i,lines[line][i]);
            }
         }         
         else
         {
            //log("  drop: " + roots[line] + " from " + line);
         }
      }

      //log("  load:  " + root + " to   " + line);
      for ( int i = 0; i < lineSize; ++i )
      {
         lines[line][i] = main.get(root+i);
      }
      roots[line]   = root;
      dirties[line] = false;

      return line;
   }

   private static void log ( final Object obj )
   {
      System.out.println(obj);
   }

}
