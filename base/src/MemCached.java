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
   private static final int     ALG_NONE    = 0;
   private static final int     ALG_DUMB    = 1;
   private static final int     ALG_RAND    = 2;
   private static final int     ALG_INC     = 3;
   private static final int     ALG_CLOCK   = 4;

   public  static final boolean TRACK_DIRTY = true;
   public  static final int     ALG         = ALG_CLOCK;

   // Wow: ALG_RAND and ALG_INC are surprisingly effective.
   //
   // Naturally when I was in here first I expected this would end up
   // as LRU.  Now, that may not be true.  Sure, LRU might be a little
   // better, but ALG_INC is mindblowingly better than nothing and
   // would be a much easier circuit to write.
   //
   // I think that ALG_INC, after at least the initial cache
   // population is established, works out to a replacement policy of
   // "oldest".  I can't recall texts discussing that one: "least
   // recently used" is always the media darling.
   // 
   // But "oldest" gives some very satisfactory results for such a
   // minimal implementation, and it has me charmed.
   // 
   // Wikipedia at http://en.wikipedia.org/wiki/Cache_algorithms only
   // lists:
   //
   //   * 1.1 Belady's Algorithm
   //   * 1.2 Least Recently Used
   //   * 1.3 Most Recently Used
   //   * 1.4 Pseudo-LRU
   //   * 1.5 Random Replacement
   //   * 1.6 Segmented LRU
   //   * 1.7 2-Way Set Associative
   //   * 1.8 Direct-mapped cache
   //   * 1.9 Least-Frequently Used
   //   * 1.10 Adaptive Replacement Cache
   //   * 1.11 Multi Queue Caching Algorithm
   // 
   // I read the unfamiliar ones: none of them is "oldest".  Huh.
   // Maybe I just discovered something novel and useful that is below
   // the entire field's radar.
   //
   // Ah, no dice:
   //
   //  http://en.wikipedia.org/wiki/Page_replacement_algorithm
   //
   // Includes:
   //
   //   The simplest page-replacement algorithm is a FIFO
   //   algorithm. The first-in, first-out (FIFO) page replacement
   //   algorithm is a low-overhead algorithm that requires little
   //   book-keeping on the part of the operating system.
   //
   // Funny how "Cache Algorithms" and "Page Replacement Algorithms"
   // are such divergant articles.  Might be worth studying both, see
   // what the deal is w/ page replacement that I'm not familiar with.
   //
   // Ah, again.  From the page replacement article:
   //
   //   Clock
   //
   //   Clock is a more efficient version of FIFO than Second-chance
   //   because pages don't have to be constantly pushed to the back
   //   of the list, but it performs the same general function as
   //   Second-Chance. The clock algorithm keeps a circular list of
   //   pages in memory, with the "hand" (iterator) pointing to the
   //   oldest page in the list.
   //
   // That's what I did here for ALG_INC, with the MemCached.next
   // serving as the clock hand.
   //
   // No, wait, that's not true.  They're saying Clock does the same
   // general function as Second-Chance.  I'm not doing Second-Chance,
   // I'm definitely doing plain FIFO - but I am using the circular
   // buffer with a "hand" to do it.  So maybe full Clock would be an
   // easy extension.
   //
   // As I interpret what Second-Chance and Clock do, is as each line
   // comes into the cache it gets a bit, basically a single Get Out
   // of Jail free card: it is FIFO, but each cache entry gets to
   // survive one cycle of the FIFO queue with immunity.  In Clock,
   // when we need to free up a line, we look at the mark where the
   // hand is pointing.  If marked, we unmark it, advance the hand,
   // and keep looking.  If unmarked, we purge that line.
   //
   // I made a mistake in my first implementation of Clock.  Tests
   // showed it to have *identical* cache performance to my FIFO, over
   // a range of cache sizes.  I logged the hand's movement, and
   // noticed it would always find a vulnerable line immediately, or
   // it would loop around a full cycle, clearing all lineCount lines,
   // before it found a vulnerable line.
   //
   // Turns out the get-out-of-jail-free marks need to be set on each
   // access, not just on load.  My mistake.  The Wikipedia article
   // calls these "reference bits", but the description of when they
   // are set is presented earlier, under Not Recently Used.  The
   // FIFO, Second-Chance, and Clock sections take that knowledge as
   // read - my bad for skimming and assuming.

   // Since I'm thinking about this layer as hardware, I want to keep
   // it as simple as possible.
   //
   // Therefore, ALG_RAND does not attempt any robustness with regards
   // to using any PRNG software.  Instead, we just accumulate cheap
   // shoddy entropy deterministically based on how we are acessed.
   // This technique has nothing to recommend it except dumbness.

   private final Mem       mem;
   private final int       lineSize;
   private final int       lineCount;
   private final int[][]   lines;
   private final int[]     roots;
   private final boolean[] dirties;
   private final boolean[] getOutOfJailFree;
   private final Stats     global;
   private final Stats     local;

   private       int       entropy = 0;
   private       int       next    = 0;

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
      if ( null == mem )
      {
         throw new NullPointerException("null mem");
      }
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
      this.local     = local;
      this.global    = global;
      if ( TRACK_DIRTY )
      {
         this.dirties = new boolean[lineCount];
      }
      else
      {
         this.dirties = null;
      }
      if ( ALG == ALG_RAND )
      {
         entropy ^= 0x12051973;
      }
      if ( ALG == ALG_CLOCK )
      {
         getOutOfJailFree = new boolean[lineCount];
      }
      else
      {
         getOutOfJailFree = null;
      }
      for ( int i = 0; i < lineCount; ++i )
      {
         this.lines[i]      = new int[lineSize];
         this.roots[i]      = -1;
         if ( TRACK_DIRTY )
         {
            this.dirties[i] = false;
         }
         if ( ALG == ALG_CLOCK )
         {
            // Who cares?  If the marks are initially random junk,
            // that just means we are slighty less efficient in the
            // first clock revolution.
            this.getOutOfJailFree[i] = false;
         }
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
      if ( ALG == ALG_RAND )
      {
         entropy ^= addr;
         entropy ^= value;
         entropy ^= 0x13041941;
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
      if ( ALG == ALG_RAND )
      {
         entropy ^= addr;
         entropy ^= 0x10021943;
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
            if ( ALG_CLOCK == ALG )
            {
               getOutOfJailFree[i] = true;
            }
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
            line     = (0x7FFFFFFF & entropy) % lineCount;
            entropy += 97;
            break;
         case ALG_INC:
            line  = next;
            next += 1;
            next %= lineCount;
            break;
         case ALG_CLOCK:
            while ( true )
            {
               line  = next;
               next += 1;
               next %= lineCount;
               if ( !getOutOfJailFree[line] )
               {
                  break;
               }
               getOutOfJailFree[line] = false;
            }
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
      if ( ALG_CLOCK == ALG )
      {
         getOutOfJailFree[line] = true;
      }

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
