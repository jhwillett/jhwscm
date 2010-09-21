/**
 * Firmware.java
 *
 * Abstraction of lowest-level program which runs directly on a
 * System.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public interface Firmware
{
   public static final int ERROR_COMPLETE   =  0;
   public static final int ERROR_INCOMPLETE = -1;

   /**
    * Called before step(), not allowed to fail.  
    *
    * The firmware should initialize the Machine to a base state.
    */
   public void boot ( final Machine mach );

   /**
    * Drives a single step of computation.  
    * 
    * @returns ERROR_COMPLETE, ERROR_INCOMPLETE, or some other error
    * code.
    */
   public int step ( final Machine mach );
}
