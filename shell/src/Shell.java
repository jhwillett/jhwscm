/**
 * Shell.java
 *
 * A shell console which can handle general Computer assemblies.
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
 */

import java.io.IOException;

public class Shell
{
   public static void main ( final String[] argv )
      throws IOException
   {
      System.out.println("Hello, World!");

      Computer computer;
      {
         final Machine  mach = new Machine(false,
                                           false,
                                           false,
                                           1024,
                                           1024);
         final JhwScm   firm = new JhwScm(true,
                                          false,
                                          false,
                                          false);
         final Computer comp = new Computer(mach,
                                            firm,
                                            false,
                                            false,
                                            false);
         computer = comp;
      }

      final Machine  machine = computer.machine;
      final IOBuffer bufIn   = machine.getIoBuf(0);
      final IOBuffer bufOut  = machine.getIoBuf(1);
      bufIn.open();
      bufOut.open();

      final boolean interactive = true;

      int dcode = Firmware.ERROR_COMPLETE;
      while ( true )
      {
         if ( interactive && Firmware.ERROR_COMPLETE == dcode )
         {
            System.out.print("prompt> ");
         }
  
         while ( !bufIn.isFull() && System.in.available() > 0 )
         {
            final int b = System.in.read();
            if ( -1 == b )
            {
               bufIn.close();
            }
            else
            {
               bufIn.push((byte)b);
            }
         }

         do
         {
            dcode = computer.drive(1024);
         }
         while ( Firmware.ERROR_INCOMPLETE == dcode );

         while ( !bufOut.isEmpty() )
         {
            final byte b = bufOut.peek();
            bufOut.pop();
            System.out.write(0xFF & b);
         }
         System.out.flush();

         if ( Firmware.ERROR_COMPLETE == dcode )
         {
            System.exit(0);
         }
         else if ( Firmware.ERROR_BLOCKED == dcode )
         {
            continue;
         }
         else
         {
            System.err.println("ERROR: " + dcode);
            if ( interactive )
            {
               computer.clear();
               continue; 
            } 
            else
            {
               System.exit(dcode);
            }
         }
      }
   }
}
