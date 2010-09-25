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
    * @returns a code from Firmware.ERROR_foo.  ERROR_INCOMPLETE
    * indicates more cycles are needed.  Firmware.ERROR_COMPLETE
    * indicates that the firmware ran to completion.
    */
   public int drive ( final int numSteps )
   {
      if ( VERBOSE ) log("drive(): numSteps " + numSteps);
      if ( numSteps < 0 )
      {
         throw new IllegalArgumentException("neg numSteps: " + numSteps);
      }
      for ( int step = 0; -1 == numSteps || step < numSteps; ++step )
      {
         if ( PROFILE ) local.numCycles  += 1;
         if ( PROFILE ) global.numCycles += 1;
         final int code = firmware.step(machine);
         if ( Firmware.ERROR_INCOMPLETE != code )
         {
            if ( VERBOSE ) log("drive(): firmware end: " + code);
            return code;
         }
      }
      final int code = Firmware.ERROR_INCOMPLETE;
      if ( VERBOSE ) log("drive(): jiffy end: " + code);
      return code;
   }

   private static void log ( final Object obj )
   {
      System.out.println(obj);
   }
}
