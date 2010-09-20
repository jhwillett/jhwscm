/**
 * Support for unit tests.
 *
 * Normally I am opposed to putting anything concrete into a parent
 * class, I have a firm belief in pure two-tier OO with only
 * Interfaces at the top.
 *
 * But I'm breaking that rule here, where I'm just implementing my own
 * form of a bit of JUnit functionality so I can have a standalone
 * project with no external dependencies.
 *
 * Here, not only am I breaking received wisdom design principles but
 * also my own.
 *
 * Received wisdom would have me using reusing the defacto standard
 * module instead of reinventing the wheel.
 *
 * TODO: better names, and there are really three or four surfaces in
 * this discussion: module-to-client, client-to-dependencies,
 * parent-to-child, child-to-parent.
 *
 * My own principles would have me avoid the needless addition of a
 * nontrivial contract between parent and children classes.  Surface
 * area between a module and its clients ("in-and-out surface") is
 * fundamental.  Surface area between parents and children
 * ("up-and-down surface") is optional.  Surface area in two
 * dimensions is a recipie for spaghetti.
 *
 * Having expressed my principles in a way which is very satisfying to
 * me, even as I break them, I can express why I am reinventing the
 * wheel here (and why I often reinvent wheels).
 *
 * I've pointed out that in-and-out surface is fundamental: depending
 * on how you define your terms, code with no clients and/or no
 * dependencies is either impossible or very rare.
 *
 * By chosing to have a local implementation of the teensy subset of
 * JUnit that I like, I eliminate a dependency on an external package.
 * Thus, I greatly reduce some of the "in surface" of this module.
 *
 * As for violating my own principles - well, I'm doing this to
 * eliminate that in-surface and cut some duplicated code out from
 * among the many test classes, but I want to introduce it without
 * changing most lines in those tests.  Maybe after that's done, I'll
 * go back and make this a utility class instead of a parent.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

public class Util
{
   protected static int depth = 0;

   protected Util ()
   {
   }

   public static void log ( final Object obj )
   {
      for ( int i = 0; i < depth; ++i )
      {
         System.out.print(' ');
         System.out.print(' ');
      }
      System.out.println(obj);
   }

   public static void assertEquals ( final Object a, final Object b )
   {
      assertEquals(null,a,b);
   }

   public static void assertEquals ( final String msg, 
                                     final Object a, 
                                     final Object b )
   {
      if ( a == b ) return;
      if ( null != a && a.equals(b) ) return;
      if ( null != b && b.equals(a) ) return;
      throw new RuntimeException(msg + " expected " + a + " got " + b);
   }
}
