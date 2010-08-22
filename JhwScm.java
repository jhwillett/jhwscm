/**
 * Damnit, I'm writing my own Scheme again.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett, motherfuckers!
 * All rights reserved.
 */

public class JhwScm
{
   public static final int SUCCESS    = 0;

   public static final int BAD_ARG    = 1;

   public static final int INCOMPLETE = 2;

   public static final int FAILURE    = 3;

   public JhwScm ()
   {
   }

   /**
    * Places all characters into the VM's input queue.
    *
    * @param input characters to be copied into the VM's input queue.
    * @throws nothing, not ever
    * @returns SUCCESS on success, otherwise an error code.
    */
   public int input ( final CharSequence input )
   {
      if ( null == input )
      {
         return BAD_ARG;
      }
      return SUCCESS;
   }

   /**
    * Pulls all pending characters in the VM's output queue into the
    * output argument.
    *
    * @param output where the output is copied to.
    * @throws nothing, not ever
    * @returns SUCCESS on success, otherwise an error code.
    */
   public int output ( final Appendable output )
   {
      if ( null == output )
      {
         return BAD_ARG;
      }
      return SUCCESS;
   }

   /**
    * Drives all pending computation to completion.
    *
    * @throws nothing, not ever
    * @returns SUCCESS on success, otherwise an error code.
    */
   public int drive ()
   {
      return drive(-1);
   }

   /**
    * Drives all pending computation to completion.
    *
    * @param numSteps the number of VM steps to execute.  If numSteps
    * < 0, runs to completion.
    * @throws nothing, not ever
    * @returns SUCCESS on success, INCOMPLETE if more cycles are
    * needed, otherwise an error code.
    */
   public int drive ( final int numSteps )
   {
      if ( numSteps < -1 )
      {
         return BAD_ARG;
      }
      return SUCCESS;
   }
}
