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
   // TODO: expose this policy to caller, be sure unit tests stress both
   private static final boolean TRACK_DIRTY = false;

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
      this.dirties   = TRACK_DIRTY ? new boolean[lineCount] : null;
      for ( int i = 0; i < lineCount; ++i )
      {
         this.lines[i]      = new int[lineSize];
         this.roots[i]      = -1;
         if ( TRACK_DIRTY )
         {
            this.dirties[i] = false;
         }
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
      final int line = getRoot(root);
      lines[line][off] = value;
      if ( TRACK_DIRTY )
      {
         dirties[line]    = true;
      }
   }

   public int get ( final int addr )
   {
      final int root  = addr / lineSize;
      final int off   = addr % lineSize;
      final int line  = getRoot(root);
      final int value = lines[line][off];
      //log("get:     " + addr + "     " + value);
      return value;
   }

   private int getRoot ( final int root )
   {
      int line = -1;
      for ( int i = 0; i < lineCount; ++i )
      {
         if ( root == roots[i] )
         {
            // TODO: LRU stuff?
            //log("  hit:   " + root + " in   " + i);
            return i;
         }
         if ( -1 == roots[i] )
         {
            line = i;
         }
      }

      if ( -1 == line )
      {
         line = 0; // TODO: LRU stuff?
         if ( !TRACK_DIRTY || dirties[line] )
         {
            //log("  flush: " + roots[line] + " from " + line);
            flushLine(line);
         }         
         else
         {
            //log("  drop: " + roots[line] + " from " + line);
         }
      }
      else
      {
         //log("  first-use: " + line);
      }

      //log("  load:  " + root + " to   " + line);
      loadLine(line,root);

      return line;
   }

   private void loadLine ( final int line, final int root )
   {
      final int[]  buf = lines[line];
      int         addr = root * lineSize;
      //log("  " + line + " <== " + addr );
      for ( int i = 0; i < lineSize; ++i )
      {
         buf[i] = main.get(addr++);
      }
      roots[line] = root;
      if ( TRACK_DIRTY )
      {
         dirties[line]  = false;
      }
   }

   private void flushLine ( final int line )
   {
      final int[]  buf = lines[line];
      final int   root = roots[line];
      int         addr = root * lineSize;
      //log("  " + line + " ==> " + addr );
      for ( int i = 0; i < lineSize; ++i )
      {
         main.set(addr++,buf[i]);
      }
      if ( TRACK_DIRTY )
      {
         dirties[line]  = false;
      }
   }

   private static void log ( final Object obj )
   {
      System.out.println(obj);
   }

}
