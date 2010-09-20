/**
 * Test harness for Computer.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class TestComputer extends Util
{
   private static class TestFirmware implements Firmware
   {
      int maxStep = 0;
      int numBoot = 0;
      int numStep = 0;

      public TestFirmware ( final int maxStep )
      {
         this.maxStep = maxStep;
      }

      public void boot ( final Machine mach )
      {
         numBoot++;
      }

      public int step ( final Machine mach )
      {
         numStep++;
         return ( numStep >= maxStep ) ? COMPLETE : INCOMPLETE;
      }
   }
   
   public static void main ( final String[] argv )
   {
      log("TestComputer");
      depth++;

      final Machine      mach = new Machine(false,false,false);
      final TestFirmware firm = new TestFirmware(50);

      assertEquals(0,firm.numBoot);
      assertEquals(0,firm.numStep);

      final Computer     comp = new Computer(mach,firm,false,false,false);

      assertEquals(1,firm.numBoot);
      assertEquals(0,firm.numStep);

      assertEquals(Firmware.INCOMPLETE,comp.drive(0));
      assertEquals(1,firm.numBoot);
      assertEquals(0,firm.numStep);
      assertEquals(Firmware.INCOMPLETE,comp.drive(1));
      assertEquals(1,firm.numBoot);
      assertEquals(1,firm.numStep);
      assertEquals(Firmware.INCOMPLETE,comp.drive(1));
      assertEquals(1,firm.numBoot);
      assertEquals(2,firm.numStep);
      assertEquals(Firmware.INCOMPLETE,comp.drive(10));
      assertEquals(1,firm.numBoot);
      assertEquals(12,firm.numStep);
      assertEquals(Firmware.INCOMPLETE,comp.drive(10));
      assertEquals(1,firm.numBoot);
      assertEquals(22,firm.numStep);
      assertEquals(Firmware.INCOMPLETE,comp.drive(10));
      assertEquals(1,firm.numBoot);
      assertEquals(32,firm.numStep);
      assertEquals(Firmware.INCOMPLETE,comp.drive(10));
      assertEquals(1,firm.numBoot);
      assertEquals(42,firm.numStep);
      assertEquals(Firmware.COMPLETE,comp.drive(10));
      assertEquals(1,firm.numBoot);
      assertEquals(50,firm.numStep);

      try
      {
         comp.drive(-1);
         throw new RuntimeException("bogus");
      }
      catch ( Throwable expected )
      {
      }

      log("success");
   }
}
