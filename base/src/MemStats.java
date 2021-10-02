/**
 * MemStats.java
 *
 * A wrapper implementation of Mem, which keeps statistics on an
 * underlying implementation.
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
 */

public class MemStats implements Mem
{
   private final Mem   mem;
   private final Stats global;
   private final Stats local;

   public static class Stats
   {
      public int numSet  = 0;
      public int numGet  = 0;
      public int maxAddr = 0;
   }

   public MemStats ( final Mem mem, final Stats global, final Stats local )
   {
      this.mem    = mem;
      this.global = global;
      this.local  = local;
   }

   public int length ()
   {
      return mem.length();
   }

   public void set ( final int addr, final int value )
   {
      if ( null != local )
      {
         local.numSet++;
         if ( addr > local.maxAddr  ) local.maxAddr  = addr;
      }
      if ( null != global )
      {
         global.numSet++;
         if ( addr > global.maxAddr ) global.maxAddr = addr;
      }
      mem.set(addr,value);
   }

   public int get ( final int addr )
   {
      if ( null != local )
      {
         local.numGet++;
         if ( addr > local.maxAddr  ) local.maxAddr  = addr;
      }
      if ( null != global )
      {
         global.numGet++;
         if ( addr > global.maxAddr ) global.maxAddr = addr;
      }
      return mem.get(addr);
   }
}
