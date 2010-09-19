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
   public static final int SUCCESS    =  0;
   public static final int INCOMPLETE = -1;

   /**
    * Drives a single step of computation.  
    * 
    * @returns SUCCESS, INCOMPLETE, or some other error code.
    */
   public int step ();
}
