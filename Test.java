/**
 * Test harness for JhwScm.
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

      // bogus args to entry points result in BAD_ARG, not an exception
      {
         final int code = new JhwScm().input(null);
         assertEquals("input(null) is a bad arg",JhwScm.BAD_ARG,code);
      }
      {
         final int code = new JhwScm().drive(-2);
         assertEquals("drive(-2) is a bad arg",JhwScm.BAD_ARG,code);
      }
      {
         final int code = new JhwScm().output(null);
         assertEquals("output(null) is a bad arg",JhwScm.BAD_ARG,code);
      }

      selfTest(new JhwScm());

      // empty args are OK
      {
         final int code = new JhwScm().input("");
         assertEquals("input(\"\")",JhwScm.SUCCESS,code);
      }
      {
         final int code = new JhwScm().drive(0);
         assertEquals("drive(0)",JhwScm.SUCCESS,code);
      }
      {
         final int code = new JhwScm().drive(-1);
         assertEquals("drive(-1) (w/ empty input)",JhwScm.SUCCESS,code);
      }
      {
         final StringBuilder output = new StringBuilder();
         final int           code   = new JhwScm().output(output);
         assertEquals("output()",JhwScm.SUCCESS,code);
         assertEquals("output is empty",0,output.length());
      }

      // some content-free end-to-ends
      final int[] variousNumCyclesEnoughForEmptyExpr = { -1, 5, 10 };
      for ( int numCycles : variousNumCyclesEnoughForEmptyExpr )
      {
         final String        msg    = "cycles: " + numCycles;
         final StringBuilder output = new StringBuilder();
         final JhwScm        scm    = new JhwScm();
         final int           icode  = scm.input("");
         final int           dcode  = scm.drive(numCycles);
         final int           ocode  = scm.output(output);
         assertEquals(msg,JhwScm.SUCCESS,icode);
         assertEquals(msg,JhwScm.SUCCESS,dcode);
         assertEquals(msg,JhwScm.SUCCESS,ocode);
         assertEquals(msg,0,output.length());
         selfTest(scm);
      }

      // first content: simple integer expressions are self-evaluating
      // (but take some time).
      final String[] variousFixnumExprs = { "0", "1", "1234", "-1", "-4321" };
      for ( String fixnumExpr : variousFixnumExprs )
      {
         final String        msg    = "fixnum self-evaluating: " + fixnumExpr;
         final StringBuilder output = new StringBuilder();
         final JhwScm        scm    = new JhwScm();
         final int           icode  = scm.input(fixnumExpr);
         final int           dcode  = scm.drive(-1);
         final int           ocode  = scm.output(output);
         assertEquals(msg,JhwScm.SUCCESS,icode);
         assertEquals(msg,JhwScm.SUCCESS,dcode);
         assertEquals(msg,JhwScm.SUCCESS,ocode);
         assertEquals(msg,fixnumExpr,output.toString());
         selfTest(scm);
         expectSuccess(fixnumExpr,fixnumExpr); // validate expectSuccess();
      }

      // first computation: even simple integer take nonzero cycles
      {
         final StringBuilder output = new StringBuilder();
         final JhwScm        scm    = new JhwScm();
         final int           icode  = scm.input("0");
         final int           dcode1 = scm.drive(0);
         final int           dcode2 = scm.drive(0);
         final int           dcode3 = scm.drive(-1);
         final int           dcode4 = scm.drive(0);
         final int           ocode  = scm.output(output);
         assertEquals(JhwScm.SUCCESS,   icode);
         assertEquals("should be incomplete so far",JhwScm.INCOMPLETE,dcode1);
         assertEquals("should be incomplete so far",JhwScm.INCOMPLETE,dcode2);
         assertEquals("should be successful",       JhwScm.SUCCESS,   dcode3);
         assertEquals("should still be successful", JhwScm.SUCCESS,   dcode4);
         assertEquals(JhwScm.SUCCESS,   ocode);
         assertEquals("0",output.toString());
         selfTest(scm);
      }

      // #t and #f are self-evaluating
      expectSuccess("#t","#t");
      expectSuccess("#f","#f");

      // unbound variables fail
      expectFailure("a");
      expectFailure("a1");

      // simple arithmetic
      expectSuccess("(+ 0)","0");
      expectSuccess("(+ 1)","1");
      expectSuccess("(+ 0 1)","1");
      expectSuccess("(+ 100 2)","102");
      expectSuccess("(* 97 2)","184");
      expectSuccess("(* 2 3 5)","30");
      expectFailure("(+ a b)");

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

      // simple conditionals: in Scheme, only #f is false
      expectSuccess("(if #f 2 5)","5");
      expectSuccess("(if #t 2 5)","2");
      expectSuccess("(if '() 2 5)","2"); 
      expectSuccess("(if 0 2 5)","2");

      // TODO: when you do mutators, be sure to check that only one
      // alternative in an (if) is executed.

      // simple special form
      expectSuccess("(quote ())","()");
      expectSuccess("(quote (1 2))","(1 2)");
      expectSuccess("(quote (a b))","(a b)");
      expectFailure("(+ 1 (quote ()))");

      // simple quote sugar
      expectSuccess("'()","()");
      expectSuccess("'(1 2)","(1 2)");
      expectSuccess("'(a b)","(a b)");
      expectFailure("(+ 1 '())");

      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define a 100)","",scm);
         expectSuccess("a","100",scm);
         selfTest(scm);
      }
      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define a 100)","",   scm);
         expectSuccess("(define b   2)","",   scm);
         expectSuccess("(+ a b)",       "102",scm);
         expectFailure("(+ a c)",             scm);
         selfTest(scm);
      }
      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define foo +)","",  scm);
         expectSuccess("(foo 13 18)",   "31",scm);
         expectFailure("(foo 13 '())",       scm);
         selfTest(scm);
      }
      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define (foo a b) (+ a b))","",  scm);
         expectSuccess("(foo 13 18)",               "31",scm);
         expectFailure("(foo 13 '())",                   scm);
         selfTest(scm);
      }
   }

   private static void selfTest ( final JhwScm scm )
   {
      assertEquals("self-test",JhwScm.SUCCESS,scm.selfTest());
   }

   private static void expectSuccess ( final String expr, final String expect )
   {
      expectSuccess(expr,expect,null);
   }

   private static void expectSuccess ( final String expr, 
                                       final String expect,
                                       JhwScm       scm )
   {
      final StringBuilder output = new StringBuilder();
      if ( null == scm )
      {
         scm = new JhwScm();
      }
      final int icode = scm.input(expr);
      assertEquals(JhwScm.SUCCESS,icode);
      final int dcode = scm.drive(-1);
      assertEquals("should succeed evaluating: " + expr, JhwScm.SUCCESS, dcode);
      final int ocode = scm.output(output);
      assertEquals(JhwScm.SUCCESS,ocode);
      assertEquals(expect,output.toString());
   }

   private static void expectFailure ( final String expr )
   {
      expectFailure(expr,null);
   }

   private static void expectFailure ( final String expr, JhwScm scm )
   {
      final StringBuilder output = new StringBuilder();
      if ( null == scm )
      {
         scm = new JhwScm();
      }
      final int icode = scm.input(expr);
      assertEquals(JhwScm.SUCCESS,icode);
      final int dcode = scm.drive(-1);
      assertEquals("should fail evaluating: " + expr, JhwScm.FAILURE, dcode);
   }
}
