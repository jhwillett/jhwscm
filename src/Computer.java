/**
 * Computer.java
 *
 * A Machine plus the Firmware loaded upon it!
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class Computer
{
   public final Machine  machine;
   public final Firmware firmware;

   public static class Stats
   {
      public int numCycles = 0;
   }

   public static final Stats global = new Stats();
   public        final Stats local  = new Stats();

   private final boolean PROFILE;
   private final boolean VERBOSE;
   private final boolean DEBUG;

   public Computer ( final Machine  machine,
                     final Firmware firmware,
                     final boolean  PROFILE, 
                     final boolean  VERBOSE, 
                     final boolean  DEBUG )
   {
      this.machine = machine;
      this.firmware = firmware;
      this.PROFILE = PROFILE;
      this.VERBOSE = VERBOSE;
      this.DEBUG   = DEBUG;
   }
}
