/**
 * Test harness for Util.  Who watches the watchmen?
 *
 * Copyright (C) 2010,2021 Jesse H. Willett
 * MIT License (see jhwscm/LICENSE.txt)
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
