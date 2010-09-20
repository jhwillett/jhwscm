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
   public static final int COMPLETE   =  0;
   public static final int INCOMPLETE = -1;

   /**
    * Called before step(), not allowed to fail.  
    *
    * The firmware should initialize the Machine to a base state.
    */
   public void boot ();

   /**
    * Drives a single step of computation.  
    * 
    * @returns COMPLETE, INCOMPLETE, or some other error code.
    */
   public int step ();
}
