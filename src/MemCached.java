/**
 * MemCached.java
 *
 * A cache: sits in front of some other Mem.  
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

import java.util.Random; // TODO: roll your own LCG

public class MemCached implements Mem
{
   private static final boolean TRACK_DIRTY = true;

   private static final int ALG_NONE = 0;
   private static final int ALG_DUMB = 1;
   private static final int ALG_RAND = 2;
   private static final int ALG_INC  = 3;
   private static final int ALG      = ALG_INC;

   // Wow: ALG_RAND and ALG_INC are surprisingly effective.
   //
   // Naturally when I was in here first I expected this would end up
   // as LRU.  Now, that may not be true.  Sure, LRU might be a little
   // better, but ALG_INC is mindblowingly better than nothing and
   // would be a much easier circuit to write.

   private final Mem       mem;
   private final int       lineSize;
   private final int       lineCount;
   private final int[][]   lines;
   private final int[]     roots;
   private final boolean[] dirties;
   private final Stats     global;
   private final Stats     local;

   private final Random    rand;
   private       int       next = 0;

   public static class Stats
   {
      public int numHits   = 0;
      public int numMisses = 0;
      public int numFlush  = 0;
      public int numDrop   = 0;
      public int numLoad   = 0;
   }

   public MemCached ( final Mem   main,
                      final int   lineSize, 
                      final int   lineCount )
   {
      this(main,lineSize,lineCount,null,null);
   }

   public MemCached ( final Mem   mem,
                      final int   lineSize, 
                      final int   lineCount,
                      final Stats global, 
                      final Stats local )
   {
      if ( lineSize <= 0 )
      {
         throw new IllegalArgumentException("nonpos lineSize " + lineSize);
      }
      if ( lineCount <= 0 )
      {
         throw new IllegalArgumentException("nonpos lineCount " + lineCount);
      }
      if ( 0 != mem.length() % lineSize )
      {
         throw new IllegalArgumentException("nonmodulo lineSize " + 
                                            lineSize + 
                                            " vs " + 
                                            mem.length());
      }
      this.mem       = mem;
      this.lineSize  = lineSize;
      this.lineCount = lineCount;
      this.lines     = new int[lineCount][];
      this.roots     = new int[lineCount];
      this.dirties   = TRACK_DIRTY ? new boolean[lineCount] : null;
      this.local     = local;
      this.global    = global;
      for ( int i = 0; i < lineCount; ++i )
      {
         this.lines[i]      = new int[lineSize];
         this.roots[i]      = -1;
         if ( TRACK_DIRTY )
         {
            this.dirties[i] = false;
         }
      }
      if ( ALG == ALG_RAND )
      {
         rand = new Random(12051973);
      }
      else
      {
         rand = null;
      }
   }

   public int length ()
   {
      return mem.length();
   }

   public void set ( final int addr, final int value )
   {
      if ( ALG_NONE == ALG )
      {
         mem.set(addr,value);
         return;
      }
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
      if ( ALG_NONE == ALG )
      {
         return mem.get(addr);
      }
      final int root  = addr / lineSize;
      final int off   = addr % lineSize;
      final int line  = getRoot(root);
      final int value = lines[line][off];
      return value;
   }

   private int getRoot ( final int root )
   {
      int line = -1;
      for ( int i = 0; i < lineCount; ++i )
      {
         if ( root == roots[i] )
         {
            if ( null != local )  local.numHits++;
            if ( null != global ) global.numHits++;
            return i;
         }
         if ( -1 == roots[i] )
         {
            line = i;
         }
      }

      if ( -1 == line )
      {
         switch ( ALG )
         {
         case ALG_DUMB:
            line = 0;
            break;
         case ALG_RAND:
            line = rand.nextInt(lineCount);
            break;
         case ALG_INC:
            line  = next;
            next += 1;
            next %= lineCount;
            break;
         default:
            throw new RuntimeException("bogus ALG: " + ALG);
         }
         if ( !TRACK_DIRTY || dirties[line] )
         {
            flushLine(line);
            if ( null != local )  local.numFlush++;
            if ( null != global ) global.numFlush++;
         }         
         else
         {
            if ( null != local )  local.numDrop++;
            if ( null != global ) global.numDrop++;
         }
         if ( null != local )  local.numMisses++;
         if ( null != global ) global.numMisses++;
      }

      loadLine(line,root);
      if ( null != local )  local.numLoad++;
      if ( null != global ) global.numLoad++;

      return line;
   }

   private void loadLine ( final int line, final int root )
   {
      final int[]  buf = lines[line];
      int         addr = root * lineSize;
      //log("  " + line + " <== " + addr );
      for ( int i = 0; i < lineSize; ++i )
      {
         buf[i] = mem.get(addr++);
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
         mem.set(addr++,buf[i]);
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
