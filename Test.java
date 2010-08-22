/**
 * Test harness for MyScm.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett, motherfuckers!
 * All rights reserved.
 */

import static org.junit.Assert.assertEquals;

public class Test
{
   public static void main ( final String[] argv )
   {
      System.out.println("Hello, World! - Test");
      System.out.println("  argv.length: " + argv.length);
      for ( int i = 0; i < argv.length; ++i )
      {
         System.out.println("  " + i + ": " + argv[i]);
      }

      // bogus args to entry points result in BAD_ARG, not an exception
      {
         final int code = new MyScm().input(null);
         assertEquals("input(null) is a bad arg",MyScm.BAD_ARG,code);
      }
      {
         final int code = new MyScm().drive(-2);
         assertEquals("drive(-2) is a bad arg",MyScm.BAD_ARG,code);
      }
      {
         final int code = new MyScm().output(null);
         assertEquals("output(null) is a bad arg",MyScm.BAD_ARG,code);
      }

      // empty args are OK
      {
         final int code = new MyScm().input("");
         assertEquals("input(\"\") is ok",MyScm.SUCCESS,code);
      }
      {
         final int code = new MyScm().drive(0);
         assertEquals("drive(0) is ok",MyScm.SUCCESS,code);
      }
      {
         final int code = new MyScm().drive(-1);
         assertEquals("drive(-1) is ok (w/ empty input)",MyScm.SUCCESS,code);
      }
      {
         final StringBuilder output = new StringBuilder();
         final int           code   = new MyScm().output(output);
         assertEquals("output() is ok",MyScm.SUCCESS,code);
         assertEquals("output is empty",0,output.length());
      }

      // some content-free end-to-ends
      {
         final StringBuilder output = new StringBuilder();
         final MyScm         scm    = new MyScm();
         final int           icode  = scm.input("");
         final int           dcode  = scm.drive(-1);
         final int           ocode  = scm.output(output);
         assertEquals(MyScm.SUCCESS,icode);
         assertEquals(MyScm.SUCCESS,dcode);
         assertEquals(MyScm.SUCCESS,ocode);
         assertEquals(0,output.length());
      }
      {
         final StringBuilder output = new StringBuilder();
         final MyScm         scm    = new MyScm();
         final int           icode  = scm.input("");
         final int           dcode  = scm.drive(0);
         final int           ocode  = scm.output(output);
         assertEquals(MyScm.SUCCESS,icode);
         assertEquals(MyScm.SUCCESS,dcode);
         assertEquals(MyScm.SUCCESS,ocode);
         assertEquals(0,output.length());
      }
      {
         final StringBuilder output = new StringBuilder();
         final MyScm         scm    = new MyScm();
         final int           icode  = scm.input("");
         final int           dcode  = scm.drive(10);
         final int           ocode  = scm.output(output);
         assertEquals(MyScm.SUCCESS,icode);
         assertEquals(MyScm.SUCCESS,dcode);
         assertEquals(MyScm.SUCCESS,ocode);
         assertEquals(0,output.length());
      }

      // first content: simple integer expressions are self-evaluating
      // (but take some time).
      {
         final StringBuilder output = new StringBuilder();
         final MyScm         scm    = new MyScm();
         final int           icode  = scm.input("0");
         final int           dcode  = scm.drive(-1);
         final int           ocode  = scm.output(output);
         assertEquals(MyScm.SUCCESS,icode);
         assertEquals(MyScm.SUCCESS,dcode);
         assertEquals(MyScm.SUCCESS,ocode);
         assertEquals("0",output);
      }

      // first computation: even simple integer take nonzero cycles
      {
         final StringBuilder output = new StringBuilder();
         final MyScm         scm    = new MyScm();
         final int           icode  = scm.input("0");
         final int           dcode1 = scm.drive(0);
         final int           dcode2 = scm.drive(0);
         final int           dcode3 = scm.drive(-1);
         final int           dcode4 = scm.drive(0);
         final int           ocode  = scm.output(output);
         assertEquals(MyScm.SUCCESS,   icode);
         assertEquals(MyScm.INCOMPLETE,dcode1);
         assertEquals(MyScm.INCOMPLETE,dcode2);
         assertEquals(MyScm.SUCCESS,   dcode3);
         assertEquals(MyScm.SUCCESS,   dcode4);
         assertEquals(MyScm.SUCCESS,   ocode);
         assertEquals("0",output);
      }

      // several valid, simple computations:
      expectSuccess("","");
      expectSuccess("0","0");
      expectSuccess("1","1");
      expectSuccess("-1","-1");


      expectFailure("a");
      expectFailure("a1");

      expectSuccess("#t","#t");
      expectSuccess("#f","#f");

      expectSuccess("(+ 0)","0");
      expectSuccess("(+ 1)","1");
      expectSuccess("(+ 0 1)","1");
      expectSuccess("(+ 100 2)","102");
      expectSuccess("(* 97 2)","184");
      expectSuccess("(* 2 3 5)","30");
      expectFailure("(+ a b)");

      expectSuccess("(if #f 2 5)","5");
      expectSuccess("(if #t 2 5)","2");

      // in Scheme, only #f is false
      expectSuccess("(if '() 2 5)","2"); 
      expectSuccess("(if 0 2 5)","2");

      // cons and list have a particular relationship
      expectSuccess("(cons 1 2)","(1 . 2)");
      expectSuccess("(car (cons 1 2))","1");
      expectSuccess("(cdr (cons 1 2))","2");
      expectSuccess("(list)","()");
      expectSuccess("(list 1 2)","(1 2)");
      expectFailure("(car 1)");
      expectFailure("(car '())");
      expectFailure("(cdr 1)");
      expectFailure("(cdr '())");
      expectSuccess("(cons 1 '())","(1)");
      expectSuccess("(cons 1 (cons 2 '()))","(1 2)");

      expectSuccess("'()","()");
      expectSuccess("'(1 2)","(1 2)");
      expectSuccess("'(a b)","(a b)");
      expectFailure("(+ 1 '())");

      {
         final MyScm scm = new MyScm();
         expectSuccess("(define a 100)","",scm);
         expectSuccess("a","100",scm);
      }
      {
         final MyScm scm = new MyScm();
         expectSuccess("(define a 100)","",   scm);
         expectSuccess("(define b   2)","",   scm);
         expectSuccess("(+ a b)",       "102",scm);
         expectFailure("(+ a c)",             scm);
      }
      {
         final MyScm scm = new MyScm();
         expectSuccess("(define foo +)","",  scm);
         expectSuccess("(foo 13 18)",   "31",scm);
         expectFailure("(foo 13 '())",       scm);
      }
      {
         final MyScm scm = new MyScm();
         expectSuccess("(define (foo a b) (+ a b))","",  scm);
         expectSuccess("(foo 13 18)",               "31",scm);
         expectFailure("(foo 13 '())",                   scm);
      }
   }

   private static void expectSuccess ( final String expr, final String expect )
   {
      expectSuccess(expr,expect,null);
   }

   private static void expectSuccess ( final String expr, 
                                       final String expect,
                                       MyScm        scm )
   {
      final StringBuilder output = new StringBuilder();
      if ( null == scm )
      {
         scm = new MyScm();
      }
      final int icode = scm.input(expr);
      assertEquals(MyScm.SUCCESS,icode);
      final int dcode = scm.drive(-1);
      assertEquals("should succeed evaluating: " + expr, MyScm.SUCCESS, dcode);
      final int ocode = scm.output(output);
      assertEquals(MyScm.SUCCESS,ocode);
      assertEquals(expect,output);
   }

   private static void expectFailure ( final String expr )
   {
      expectFailure(expr,null);
   }

   private static void expectFailure ( final String expr, MyScm scm )
   {
      final StringBuilder output = new StringBuilder();
      if ( null == scm )
      {
         scm = new MyScm();
      }
      final int icode = scm.input(expr);
      assertEquals(MyScm.SUCCESS,icode);
      final int dcode = scm.drive(-1);
      assertEquals("should fail evaluating: " + expr, MyScm.FAILURE, dcode);
   }
}
