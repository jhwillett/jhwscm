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
      this.machine  = machine;
      this.firmware = firmware;
      this.PROFILE  = PROFILE;
      this.VERBOSE  = VERBOSE;
      this.DEBUG    = DEBUG;
      this.firmware.boot(machine);
   }

   /**
    * Drives all pending computation to completion.
    *
    * @param numSteps the number of VM steps to execute
    *
    * @throws IllegalArgumentException if numSteps < 0
    *
    * @returns Firmware.COMPLETE on success, Firmware.INCOMPLETE if
    * more cycles are needed, otherwise an error code.
    */
   public int drive ( final int numSteps )
   {
      if ( numSteps < 0 )
      {
         throw new IllegalArgumentException("neg numSteps: " + numSteps);
      }
      for ( int step = 0; -1 == numSteps || step < numSteps; ++step )
      {
         if ( PROFILE ) local.numCycles  += 1;
         if ( PROFILE ) global.numCycles += 1;
         final int code = firmware.step(machine);
         if ( Firmware.INCOMPLETE != code )
         {
            return code;
         }
      }
      return Firmware.INCOMPLETE;
   }
}
