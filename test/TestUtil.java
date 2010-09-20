/**
 * Test harness for Util.  Who watches the watchmen?
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class TestUtil extends Util
{
   public static void main ( final String[] argv )
   {
      log("TestUtil:");

      assertEquals("fooled you",null,null);
      assertEquals("fooled you","alif","alif");

      try
      {
         assertEquals("fooled you",1,2);
         throw new RuntimeException("bogus");
      }
      catch ( Throwable expected )
      {
      }

      try
      {
         assertEquals("fooled you",null,1);
         throw new RuntimeException("bogus");
      }
      catch ( Throwable expected )
      {
      }

      try
      {
         assertEquals("fooled you",null,new Object());
         throw new RuntimeException("bogus");
      }

      catch ( Throwable expected )
      {
      }

      try
      {
         assertEquals("fooled you","alif","baa");
         throw new RuntimeException("bogus");
      }
      catch ( Throwable expected )
      {
      }

      log("success");
   }
}
