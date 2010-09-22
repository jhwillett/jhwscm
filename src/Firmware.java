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
   public static final int ERROR_COMPLETE         =  0;
   public static final int ERROR_INCOMPLETE       = -1;
   public static final int ERROR_OUT_OF_MEMORY    = -2;
   public static final int ERROR_FAILURE_LEXICAL  = -3;
   public static final int ERROR_FAILURE_SEMANTIC = -4;
   public static final int ERROR_INTERNAL_ERROR   = -5;
   public static final int ERROR_UNIMPLEMENTED    = -6;


   /**
    * Called before step(), not allowed to fail.  
    *
    * The firmware should initialize the Machine to a base state.
    */
   public void boot ( final Machine mach );

   /**
    * Drives a single step of computation.  
    * 
    * @returns ERROR_COMPLETE, ERROR_INCOMPLETE, or some other
    * ERROR_foo.
    */
   public int step ( final Machine mach );
}
