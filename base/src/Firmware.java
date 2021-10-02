/**
 * Firmware.java
 *
 * Abstraction of lowest-level program which runs directly on a
 * System.
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
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
   public static final int ERROR_BLOCKED          = -7;

   /**
    * Called before step(), not allowed to fail.  
    *
    * The firmware should initialize the Machine to a base state.
    */
   public void boot ( final Machine mach );

   /**
    * Resets to top level loop, clearing any error state or current
    * computation, but preserving any progress made good.
    */
   public void clear ( final Machine mach );

   /**
    * Drives a single step of computation.  
    * 
    * @returns ERROR_COMPLETE, ERROR_INCOMPLETE, or some other
    * ERROR_foo.
    */
   public int step ( final Machine mach );
}
