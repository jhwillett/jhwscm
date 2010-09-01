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
   private static final boolean verbose = false;

   public static void main ( final String[] argv )
   {
      JhwScm.SILENT = true;

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
         assertEquals("drive(0)",JhwScm.INCOMPLETE,code);
      }
      {
         final int code = new JhwScm().drive(-1);
         assertEquals("drive(-1) (w/ empty input)",JhwScm.SUCCESS,code);
      }
      {
         final StringBuilder buf  = new StringBuilder();
         final int           code = new JhwScm().output(buf);
         assertEquals("output()",JhwScm.SUCCESS,code);
         assertEquals("output is empty",0,buf.length());
      }

      // some content-free end-to-ends
      final int[] variousNumCyclesEnoughForEmptyExpr = { -1, 5, 10 };
      for ( int numCycles : variousNumCyclesEnoughForEmptyExpr )
      {
         final String        msg   = "cycles: " + numCycles;
         final StringBuilder buf   = new StringBuilder();
         final JhwScm        scm   = new JhwScm();
         final int           icode = scm.input("");
         assertEquals(msg,JhwScm.SUCCESS,icode);
         final int           dcode = scm.drive(numCycles);
         assertEquals(msg,JhwScm.SUCCESS,dcode);
         final int           ocode = scm.output(buf);
         assertEquals(msg,JhwScm.SUCCESS,ocode);
         assertEquals(msg,0,buf.length());
         selfTest(scm);
      }

      // first content: simple integer expressions are self-evaluating
      // and self-printing.
      final String[] simpleInts = { 
         "0", "1", "1234", "-1", "-4321", "10", "1001", 
      };
      for ( final String expr : simpleInts )
      {
         expectSuccess(expr,                   expr);
         expectSuccess(" " + expr,             expr);
         expectSuccess(expr + " " ,            expr);
         expectSuccess(" " + expr + " ",       expr);
         expectSuccess("\n" + expr,            expr);
         expectSuccess(expr + "\n" ,           expr);
         expectSuccess("\t" + expr + "\t\r\n", expr);
      }

      // second content: tweakier integer expressions are
      // self-evaluating but not self-printing.
      final String[][] tweakyInts = { 
         { "007", "7" }, { "-007", "-7" }, {"-070", "-70" },
      };
      for ( final String[] pair : tweakyInts )
      {
         expectSuccess(pair[0],             pair[1]);
         expectSuccess(" " + pair[0],       pair[1]);
         expectSuccess(pair[0] + " " ,      pair[1]);
         expectSuccess(" " + pair[0] + " ", pair[1]);
      }

      // first computation: even simple integer take nonzero cycles
      {
         final StringBuilder buf    = new StringBuilder();
         final JhwScm        scm    = new JhwScm();
         final int           icode  = scm.input("0");
         assertEquals(JhwScm.SUCCESS, icode);
         final int           dcode1 = scm.drive(0);
         assertEquals("should be incomplete so far",JhwScm.INCOMPLETE,dcode1);
         final int           dcode2 = scm.drive(0);
         assertEquals("should be incomplete so far",JhwScm.INCOMPLETE,dcode2);
         final int           dcode3 = scm.drive(-1);
         assertEquals("should be successful",       JhwScm.SUCCESS,   dcode3);
         final int           dcode4 = scm.drive(0);
         assertEquals("should be incomplete again",JhwScm.INCOMPLETE, dcode4);
         final int           ocode  = scm.output(buf);
         assertEquals(JhwScm.SUCCESS, ocode);
         assertEquals("0",buf.toString());
         selfTest(scm);
      }

      // boolean literals are self-evaluating
      expectSuccess("#t",   "#t");
      expectSuccess("#f",   "#f");
      expectSuccess(" #t ", "#t");
      expectSuccess("#f ",  "#f");
      expectSuccess(" #f",  "#f");
      expectLexical("#x");

      // unbound variables fail
      expectSemantic("a");
      expectSemantic("a1");

      // some lexical, rather than semantic, error case expectations
      expectSemantic("()");
      expectSemantic("\r(\t)\n");
      expectLexical("(");
      expectLexical(" (  ");
      expectLexical(")");
      expectSemantic("(()())");
      expectSemantic("  ( ( )    ( ) )");
      expectSemantic("(()()))");     // fails on expr before reaching extra ')'
      expectSemantic(" ( () ())) "); // fails on expr before reaching extra ')'
      expectLexical("((()())");

      expectSuccess("-",   "-",  new JhwScm(false));
      expectSemantic("-");
      expectSuccess("-asd", "-asd",  new JhwScm(false));
      expectSemantic("-as");
      
      expectSemantic("(a b c)");
      expectSemantic("(a (b c))");
      expectSemantic("((a b) c)");
      expectSemantic("((a b c))");
      expectLexical("((a b) c");
      expectLexical("((a b c)");

      expectSuccess("(a b c)",      "(a b c)",   new JhwScm(false));
      expectSuccess("(a (b c))",    "(a (b c))", new JhwScm(false));
      expectSuccess("((a b) c)",    "((a b) c)", new JhwScm(false));
      expectSuccess("((a b c))",    "((a b c))", new JhwScm(false));
      expectLexical("((a b) c",                  new JhwScm(false));
      expectLexical("((a b c)",                  new JhwScm(false));
      expectSuccess("()",           "()",        new JhwScm(false));
      expectSuccess("\r(\t)\n",     "()",        new JhwScm(false));
      expectLexical("(",                         new JhwScm(false));
      expectLexical(" (  ",                      new JhwScm(false));
      expectLexical(")",                         new JhwScm(false));
      expectSuccess("(()())",       "(() ())",   new JhwScm(false));
      expectSuccess(" ( ( ) ( ) )", "(() ())",   new JhwScm(false));
      expectLexical("(()()))",                   new JhwScm(false));
      expectLexical(" ( () ())) ",               new JhwScm(false));
      expectLexical("((()())",                   new JhwScm(false));

      // improper list experssions: yay!
      expectSuccess("(1 . 2)",      "(1 . 2)",   new JhwScm(false));
      expectSuccess("(1 2 . 3)",    "(1 2 . 3)", new JhwScm(false));
      expectLexical("(1 . 2 3)",                 new JhwScm(false));
      expectLexical("( . 2 3)",                  new JhwScm(false));
      expectLexical("(1 . )",                    new JhwScm(false));
      expectLexical("(1 .)",                     new JhwScm(false));
      expectLexical("(1 . 2 3)");
      expectLexical("( . 2 3)");
      expectLexical("(1 . )");
      expectLexical("(1 .)");

      expectSuccess("(1 . ())",     "(1)",       new JhwScm(false));
      expectSuccess("(1 .())",      "(1)",       new JhwScm(false));

      // Guile does this, with nothing before the dot in a dotted list
      // but I do not quite understand why it works.
      // 
      // What is more surprising, is once I had the basic dotted list
      // working in (read) and (print), the first time I tried these
      // cases they behaved as expected, as Guile does.
      //
      // Which makes me feel warm and fuzzy, like this is a funny edge
      // case in the definition and my implementation was faithful
      // enough to the definition that it exhibits the same edge
      // cases, although I did not anticipate them at time of
      // implementation.
      //
      // Still, this demands I meditate on it to understand fully why
      // this is so.
      //
      expectSuccess("( . 2 )",    "2",           new JhwScm(false));
      expectSuccess("( . () )",   "()",          new JhwScm(false));
      expectLexical("( . 2 3 )");
      expectSuccess("(. abc )",   "abc",         new JhwScm(false));

      if ( false )
      {
         // Probably not until I handle floats!
         JhwScm.SILENT = false;
         expectSuccess("(1 .2)",       "(1 0.2)",   new JhwScm(false));
      }

      // character literals are self-evaluating - though some of them
      // are tweaky (self-evaluate but don't self-print)
      final String[] simpleChars = { 
         "#\\1", "#\\a", "#\\A", "#\\z", "#\\Z", 
         "#\\~", "#\\<", "#\\>", "#\\\\","#\\'",
         "#\\(", "#\\)", "#\\)",
      };
      for ( final String expr : simpleChars )
      {
         expectSuccess(expr,                   expr);
         expectSuccess(" " + expr,             expr);
         expectSuccess(expr + " " ,            expr);
         expectSuccess(" " + expr + " ",       expr);
         expectSuccess("\n" + expr,            expr);
         expectSuccess(expr + "\n" ,           expr);
         expectSuccess("\t" + expr + "\t\r\n", expr);
      }
      final String[][] tweakyChars = { 
         { "#\\ ",      "#\\space"   }, 
         { "#\\\n",     "#\\newline" },
         // TODO: long-forms character literal inputs are deferred -
         // lexer really has to look forward a long way to decide if
         // it's valid.  Ditto for failure modes.
         /*
         { "#\\space",  "#\\space"   },
         { "#\\SPACE",  "#\\space"   },
         { "#\\newline","#\\newline" },
         { "#\\NeWlInE","#\\newline" },
          */
      };
      for ( final String[] pair : tweakyChars )
      {
         expectSuccess(pair[0],             pair[1]);
         expectSuccess(" " + pair[0],       pair[1]);
         expectSuccess(pair[0] + " " ,      pair[1]);
         expectSuccess(" " + pair[0] + " ", pair[1]);
      }
      expectLexical("#");
      expectLexical("#\\");
      expectLexical("# ");
      expectLexical("#\n");
      if ( false )
      {
         expectLexical("#\\spac");
         expectLexical("#\\asdf");
      }

      // string literals expressions are self-evaluating and
      // self-printing.
      final String[] simpleStrs = { 
         "\"\"", "\" \"", "\"\t\"", "\"a\"", "\"Hello, World!\"",
      };
      for ( final String expr : simpleStrs )
      {
         expectSuccess(expr,                   expr);
         expectSuccess(" " + expr,             expr);
         expectSuccess(expr + " " ,            expr);
         expectSuccess(" " + expr + " ",       expr);
         expectSuccess("\n" + expr,            expr);
         expectSuccess(expr + "\n" ,           expr);
         expectSuccess("\t" + expr + "\t\r\n", expr);
      }
      expectLexical("\"");
      expectLexical("\"hello");

      JhwScm.SILENT = false;

      // simple arithmetic - more than testing math, also requires a
      // top-level env with symbols bound to primitive functions
      expectSuccess("+","????");
      expectSuccess("(+ 0)","0");
      expectSuccess("(+ 1)","1");
      expectSuccess("(+ 0 1)","1");
      expectSuccess("(+ 100 2)","102");
      expectSuccess("(+ 100 -2)","98");
      expectSuccess("(* 97 2)","184");
      expectSuccess("(* 2 3 5)","30");
      expectSemantic("(+ a b)");

      // TODO: test min, max, bounds, 2s-complement nature of fixints?

      // defining symbols
      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define a 100)","",   scm);
         selfTest(scm);
         expectSuccess("a",             "100",scm);
         selfTest(scm);
         expectSuccess("(define a 100)","",   scm);
         selfTest(scm);
         expectSuccess("(define b   2)","",   scm);
         selfTest(scm);
         expectSuccess("(+ a b)",       "102",scm);
         selfTest(scm);
         expectSemantic("(+ a c)",            scm);
         selfTest(scm);
      }

      // cons and list have a particular relationship
      expectSuccess("(cons 1 2)","(1 . 2)");
      expectSuccess("(car (cons 1 2))","1");
      expectSuccess("(cdr (cons 1 2))","2");
      expectSuccess("(list)","()");
      expectSuccess("(list 1 2)","(1 2)");
      expectSemantic("(car 1)");
      expectSemantic("(car '())");
      expectSemantic("(cdr 1)");
      expectSemantic("(cdr '())");
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
      expectSemantic("(+ 1 (quote ()))");

      // simple quote sugar
      expectSuccess("'()","()");
      expectSuccess("'(1 2)","(1 2)");
      expectSuccess("'(a b)","(a b)");
      expectSemantic("(+ 1 '())");

      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define foo +)","",  scm);
         expectSuccess("(foo 13 18)",   "31",scm);
         expectSemantic("(foo 13 '())",      scm);
         selfTest(scm);
      }

      // defining functions
      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define (foo a b) (+ a b))","",  scm);
         expectSuccess("(foo 13 18)",               "31",scm);
         expectSemantic("(foo 13 '())",                  scm);
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
      final StringBuilder buf = new StringBuilder();
      if ( null == scm )
      {
         scm = new JhwScm();
      }
      final int icode = scm.input(expr);
      assertEquals("input failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   icode);
      final int dcode = scm.drive(-1);
      assertEquals("drive failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   dcode);
      final int ocode = scm.output(buf);
      assertEquals("output failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   ocode);
      assertEquals("result failure on \"" + expr + "\":",
                   expect,
                   buf.toString());
      if ( verbose )
      {
         System.out.print("pass: expr \"");
         System.out.print(expr);
         System.out.print("\" evaluated to \"");
         System.out.print(buf);
         System.out.print("\"");
         System.out.println("\"");
      }
      selfTest(scm);
   }

   private static void expectLexical ( final String expr )
   {
      expectFailure(expr,null,JhwScm.FAILURE_LEXICAL);
   }

   private static void expectLexical ( final String expr, JhwScm scm )
   {
      expectFailure(expr,scm,JhwScm.FAILURE_LEXICAL);
   }

   private static void expectSemantic ( final String expr )
   {
      expectFailure(expr,null,JhwScm.FAILURE_SEMANTIC);
   }

   private static void expectSemantic ( final String expr, JhwScm scm )
   {
      expectFailure(expr,scm,JhwScm.FAILURE_SEMANTIC);
   }

   private static void expectFailure ( final String expr, 
                                       JhwScm       scm,
                                       final int    expectedError )
   {
      if ( null == scm )
      {
         scm = new JhwScm();
      }
      final int icode = scm.input(expr);
      assertEquals("input failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   icode);
      final int dcode = scm.drive(-1);
      if ( JhwScm.SUCCESS == dcode )
      {
         final StringBuilder buf = new StringBuilder();
         final int ocode         = scm.output(buf);
         System.out.print("unexpected success: expr \"");
         System.out.print(expr);
         System.out.print("\" evaluated to \"");
         System.out.print(buf);
         System.out.print("\"");
         System.out.println("\"");
      }
      assertEquals("should fail evaluating \"" + expr + "\":",
                   expectedError, 
                   dcode);
      selfTest(scm);
   }
}
