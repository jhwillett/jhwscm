/**
 * SegFault.java
 *
 * Failure mode for the Mem family.
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
 */

public class SegFault extends RuntimeException
{
   public SegFault ( final String msg )
   {
      super(msg);
   }
}

