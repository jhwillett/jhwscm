/**
 * Shell.java
 *
 * A shell console which can handle general Computer assemblies.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
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

      while ( true )
      {
         int dcode = Firmware.ERROR_INCOMPLETE;
         do
         {
            dcode = computer.drive(1024);
         }
         while ( Firmware.ERROR_INCOMPLETE == dcode );

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
            System.exit(dcode);
         }
      }
   }
}
