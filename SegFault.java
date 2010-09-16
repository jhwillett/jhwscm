/**
 * SegFault.java
 *
 * Failure mode for the Mem family.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class SegFault extends RuntimeException
{
   public SegFault ( final String msg )
   {
      super(msg);
   }
}

