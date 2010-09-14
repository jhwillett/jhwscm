/**
 * Test harness for JhwScm.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett, motherfuckers!
 * All rights reserved.
 */

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.io.IOException;

public class Test
{
   private static final boolean verbose = false;

   private static final Random debugRand = new Random(1234);

   private static boolean DO_REP = true;
   private static boolean SILENT = true;
   private static boolean DEBUG  = true;

   private static JhwScm newScm ( final boolean do_rep )
   {
      return new JhwScm(do_rep,SILENT,DEBUG);
   }

   private static JhwScm newScm ()
   {
      return new JhwScm(DO_REP,SILENT,DEBUG);
   }

   public static void main ( final String[] argv )
      throws java.io.IOException
   {
      // bogus args to entry points result in BAD_ARG, not an exception
      {
         final int code = newScm().input(null,0,0);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().input(new byte[0],0,0);
         assertEquals(JhwScm.SUCCESS,code);
      }
      {
         final int code = newScm().input(new byte[0],-1,0);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().input(new byte[0],0,-1);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().input(new byte[0],3,2);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().input(new byte[0],2,3);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().output(null,0,0);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().output(new byte[0],0,0);
         assertEquals(0,code);
      }
      {
         final int code = newScm().output(new byte[0],-1,0);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().output(new byte[0],0,-1);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().output(new byte[0],3,2);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().output(new byte[0],2,3);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().output(new byte[3],2,3);
         assertEquals(JhwScm.BAD_ARG,code);
      }
      {
         final int code = newScm().output(new byte[5],2,3);
         assertEquals(-1,code);
      }
      {
         final int code = newScm().drive(-2);
         assertEquals(JhwScm.BAD_ARG,code);
      }


      // empty args are OK
      {
         final int code = newScm().input(new byte[0],0,0);
         assertEquals("input(\"\")",JhwScm.SUCCESS,code);
      }
      {
         final int code = newScm().input(new byte[0],0,0);
         assertEquals("input(\"\")",JhwScm.SUCCESS,code);
      }
      {
         final int code = newScm().input(new byte[1],1,0);
         assertEquals("input(\"\")",JhwScm.SUCCESS,code);
      }
      {
         final int code = newScm().drive(0);
         assertEquals("drive(0)",JhwScm.INCOMPLETE,code);
      }
      {
         final int code = newScm().drive(-1);
         assertEquals("drive(-1) (w/ empty input)",JhwScm.SUCCESS,code);
      }
      {
         final StringBuilder buf  = new StringBuilder();
         final int           code = output(newScm(),buf);
         assertEquals("output()",JhwScm.SUCCESS,code);
         assertEquals("output is empty",0,buf.length());
      }

      // some content-free end-to-ends
      final int[] variousNumCyclesEnoughForEmptyExpr = { -1, 5, 10 };
      for ( int numCycles : variousNumCyclesEnoughForEmptyExpr )
      {
         final String        msg   = "cycles: " + numCycles;
         final StringBuilder buf   = new StringBuilder();
         final JhwScm        scm   = newScm();
         final int           icode = input(scm,"");
         assertEquals(msg,JhwScm.SUCCESS,icode);
         final int           dcode = scm.drive(numCycles);
         assertEquals(msg,JhwScm.SUCCESS,dcode);
         final int           ocode = output(scm,buf);
         assertEquals(msg,JhwScm.SUCCESS,ocode);
         assertEquals(msg,0,buf.length());
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
         final JhwScm        scm    = newScm();
         final int           icode  = input(scm,"0");
         assertEquals(JhwScm.SUCCESS, icode);
         final int           dcode1 = scm.drive(0);
         assertEquals("should be incomplete so far",JhwScm.INCOMPLETE,dcode1);
         final int           dcode2 = scm.drive(0);
         assertEquals("should be incomplete so far",JhwScm.INCOMPLETE,dcode2);
         final int           dcode3 = scm.drive(-1);
         assertEquals("should be successful",       JhwScm.SUCCESS,   dcode3);
         final int           dcode4 = scm.drive(0);
         assertEquals("should be incomplete again",JhwScm.INCOMPLETE, dcode4);
         final int           ocode  = output(scm,buf);
         assertEquals(JhwScm.SUCCESS, ocode);
         assertEquals("0",buf.toString());
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
      expectSemantic("(())");

      expectSuccess("-",   "-",  newScm(false));
      expectSuccess("-",   null);
      expectSuccess("-asd", "-asd",  newScm(false));
      expectSemantic("-as");
      
      expectSemantic("(a b c)");
      expectSemantic("(a (b c))");
      expectSemantic("((a b) c)");
      expectSemantic("((a b c))");
      expectLexical("((a b) c");
      expectLexical("((a b c)");

      expectSuccess("(a b c)",      "(a b c)",   newScm(false));
      expectSuccess("(a (b c))",    "(a (b c))", newScm(false));
      expectSuccess("((a b) c)",    "((a b) c)", newScm(false));
      expectSuccess("((a b c))",    "((a b c))", newScm(false));
      expectSuccess("((a)b)",       "((a) b)",   newScm(false));
      expectSuccess("((a )b)",      "((a) b)",   newScm(false));
      expectSuccess("((a ) b)",     "((a) b)",   newScm(false));
      expectSuccess("( (a )b)",     "((a) b)",   newScm(false));
      expectSuccess("( (a) b)",     "((a) b)",   newScm(false));
      expectSuccess("( (a)b )",     "((a) b)",   newScm(false));
      expectLexical("((a b) c",                  newScm(false));
      expectLexical("((a b c)",                  newScm(false));
      expectSuccess("()",           "()",        newScm(false));
      expectSuccess("\r(\t)\n",     "()",        newScm(false));
      expectLexical("(",                         newScm(false));
      expectLexical(" (  ",                      newScm(false));
      expectLexical(")",                         newScm(false));
      expectSuccess("(()())",       "(() ())",   newScm(false));
      expectSuccess(" ( ( ) ( ) )", "(() ())",   newScm(false));
      expectLexical("(()()))",                   newScm(false));
      expectLexical(" ( () ())) ",               newScm(false));
      expectLexical("((()())",                   newScm(false));

      // improper list experssions: yay!
      expectSuccess("(1 . 2)",      "(1 . 2)",   newScm(false));
      expectSuccess("(1 2 . 3)",    "(1 2 . 3)", newScm(false));
      expectLexical("(1 . 2 3)",                 newScm(false));
      expectLexical("( . 2 3)",                  newScm(false));
      expectLexical("(1 . )",                    newScm(false));
      expectLexical("(1 .)",                     newScm(false));
      expectLexical("(1 . 2 3)");
      expectLexical("( . 2 3)");
      expectLexical("(1 . )");
      expectLexical("(1 .)");

      expectSuccess("(1 . ())",     "(1)",       newScm(false));
      expectSuccess("(1 .())",      "(1)",       newScm(false));

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
      expectSuccess("( . 2 )",    "2",           newScm(false));
      expectSuccess("( . 2 )",    "2",           newScm(true));
      expectSuccess("( . () )",   "()",          newScm(false));
      expectLexical("( . 2 3 )");
      expectSuccess("(. abc )",   "abc",         newScm(false));

      if ( false )
      {
         // Probably not until I handle floats!
         SILENT = false;
         expectSuccess("(1 .2)",       "(1 0.2)",   newScm(false));
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

      // Check some basic symbol bindings are preset for us which
      // would enable our upcoming (eval) tests...
      //
      // R5RS and R6RS do not specify how these print - and so far
      // neither do I.  So there's no unit test here, just that they
      // evaluate OK.
      //
      expectSuccess("+",       null);
      expectSuccess("*",       null);
      expectSuccess("cons",    null);
      expectSuccess("car",     null);
      expectSuccess("cdr",     null);
      expectSuccess("list",    null);
      expectSuccess("if",      null);
      expectSuccess("quote",   null);
      expectSuccess("define",  null);
      expectSuccess("lambda",  null);

      // Dipping my toe into basic edge cases around implied (eval)
      // and some simple arithmetic - more than testing math.
      //
      expectSemantic("(())");
      expectSemantic("(1)");
      expectSemantic("(\"a\")");
      expectSemantic("(#\\a)");
      expectSemantic("(() 0)");
      expectSemantic("(1 0)");
      expectSemantic("(\"a\" 0)");
      expectSemantic("(#\\a 0)");
      expectSuccess("(+ 0 0)", "0");
      expectSuccess("(+ 0 1)", "1");
      expectSuccess("(+ 0 2)", "2");
      expectSuccess("(+ 2 0)", "2");
      expectSuccess("(+ 2 3)", "5");
      expectSuccess("(+ 2 -3)","-1");
      expectSuccess("(+ -2 -3)", "-5");
      expectSuccess("(- 2  3)","-1");
      expectSuccess("(- 2 -3)","5");
      expectSuccess("(- -3 2)","-5");
      expectSuccess("(+ 100 2)","102");
      expectSuccess("(+ 100 -2)","98");
      expectSuccess("(* 97 2)","194");
      expectSuccess("(* -97 2)","-194");
      expectSuccess("(* -97 -2)","194");
      expectSuccess("(* 97 -2)","-194");
      expectSemantic("(+ a b)");

      if ( true )
      {
         // TODO: note, for now + and * are binary only: this will
         // change!
         expectSemantic("(+ 0)");
         expectSemantic("(+)");
         expectSemantic("(+ 1)");
         expectSemantic("(* 2 3 5)");
         expectSemantic("(*)");
      }
      else
      {
         expectSuccess("(+ 0)","0");
         expectSuccess("(+)","0");
         expectSuccess("(+ 1)","1");
         expectSuccess("(* 2 3 5)","30");
         expectSuccess("(*)","1");
      }
      expectSuccess("(+0)",      "0");
      expectSuccess("(+1 10)",   "10");
      expectSuccess("(+3 3 5 7)","15");
      expectSemantic("(+0 1)");
      expectSemantic("(+1)");
      expectSemantic("(+1 1 2)");
      expectSemantic("(+3)");
      expectSemantic("(+3 1 2)");
      expectSemantic("(+3 1 2 3 4)");

      // simple special form
      expectSuccess("(quote ())","()");
      expectSuccess("(quote (1 2))","(1 2)");
      expectSuccess("(quote (a b))","(a b)");
      expectSemantic("(+ 1 (quote ()))");
      expectSuccess("(quote 9)",                "9");
      expectSuccess("(quote (quote 9))",        "(quote 9)");
      expectSuccess("(quote (quote (quote 9)))","(quote (quote 9))");

      // simple quote sugar
      expectSuccess("'()","()");
      expectSuccess("'(1 2)","(1 2)");
      expectSuccess("'(a b)","(a b)");
      expectSemantic("(+ 1 '())");
      expectSuccess("'9",       "9");
      if ( false )
      {
         // See the quote-quote-quote discussion in DIARY.txt.
         //
         // TODO: the quoted-quote question, and how to print it
         expectSuccess("''9",      "(quote 9)");
         expectSuccess("'''9",     "(quote (quote 9))");
         expectSuccess(" ' ' ' 9 ","(quote (quote 9))");
      }

      expectSuccess("(equal? 10  10)","#t");
      expectSuccess("(equal? 11  10)","#f");
      expectSuccess("(equal? 'a  'a)","#t");
      expectSuccess("(equal? 'a  'b)","#f");
      expectSuccess("(equal? 10  'b)","#f");
      expectSuccess("(<       9  10)","#t");
      expectSuccess("(<      10   9)","#f");
      expectSuccess("(<      10  10)","#f");
      expectSuccess("(<     -10   0)","#t");
      expectSuccess("(<      -1  10)","#t");
      expectSuccess("(<       0 -10)","#f");
      expectSemantic("(< 10 'a)");

      // simple conditionals: in Scheme, only #f is false
      // 
      // TODO: test w/ side effects that short-cut semantics work.
      //
      // TODO: when you do mutators, be sure to check that only one
      // alternative in an (if) is executed.
      //
      expectSuccess("(if #f 2 5)","5");
      expectSuccess("(if #t 2 5)","2");
      expectSuccess("(if '() 2 5)","2"); 
      expectSuccess("(if 0 2 5)","2");
      expectSuccess("(if 0 (+ 2 1) 5)","3");
      expectSuccess("(if 0 2 5)","2");
      expectSuccess("(if #t (+ 2 1) (+ 4 5))","3");
      expectSuccess("(if #f (+ 2 1) (+ 4 5))","9");
      expectSuccess("(if (equal? 1 1) 123 321)","123");
      expectSuccess("(if (equal? 2 1) 123 321)","321");

      // cons, car, cdr, and list have a particular relationship
      //
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

      // OUCH! w/ no garbage collection, no proper tail recursion, and
      // a 512 cell heap, this goes OOM.  More than 4 KB to interpret
      // that?
      //
      expectSuccess("(cons 1 (cons 2 '()))","(1 2)");

      // (read) and (print) are exposed, though of course print is
      // exposed as (display) via R5RS.  I am displeased, b/c
      // "read-eval-display" loop doesn't have the same robust
      // tradition.  SICP uses (print)...
      //
      expectSuccess("(display 5)","5");
      expectSuccess("(display 5)2","52");
      expectSuccess("(display (+ 3 4))(+ 1 2)","73");
      expectSuccess("(display '(+ 3 4))(+ 1 2)","(+ 3 4)3");
      expectSuccess("(read)5","5");
      expectSuccess("(read)(+ 1 2)","(+ 1 2)");
      
      // defining symbols
      {
         final JhwScm scm = newScm();
         expectSuccess("(define a 100)","",   scm);
         expectSuccess("a",             "100",scm);
         expectSuccess("(define a 100)","",   scm);
         expectSuccess("(define b   2)","",   scm);
         expectSuccess("(+ a b)",       "102",scm);
         expectSemantic("(+ a c)",            scm);
      }
      expectSemantic("(define a)");

      // redefining symbols
      expectSuccess("(define a 1)a(define a 2)a","12");

      {
         final JhwScm scm = newScm();
         expectSuccess("(define foo +)","",  scm);
         expectSuccess("(foo 13 18)",   "31",scm);
         expectSemantic("(foo 13 '())",      scm);
      }

      // defining functions
      expectSemantic("(lambda)");
      expectSemantic("(lambda ())");
      expectSuccess("(lambda () 1)","???");
      expectSuccess("((lambda () 1))","1");
      expectSemantic("((lambda () 1) 10)");
      expectSuccess("(lambda (a) 1)","???");
      expectSuccess("((lambda (a) 1) 10)","1");
      expectSemantic("((lambda (a) 1))");
      expectSemantic("((lambda (a) 1) 10 20)");
      expectSuccess("(lambda (a b) (* a b))",       "???");
      expectSuccess("((lambda (a) (* 3 a)) 13)",    "39");
      expectSuccess("((lambda (a b) (* a b)) 13 5)","65");
      {
         final JhwScm scm = newScm();
         expectSuccess("(define (foo a b) (+ a b))","",  scm);
         expectSuccess("(foo 13 18)",               "31",scm);
         expectSemantic("(foo 13 '())",                  scm);
      }

      {
         // ambition: nontrivial user-defined recursive function
         // 
         // This one, Factorial, is good for playing w/ tail-recursion.
         // The naive form is not tail recursive and consumes O(n) stack
         // space - but the tail-recursive function is super simple.
         //
         // TODO: demonstrate non-tail-recursive OOMs at a certain scale,
         // but its tail-recursive twin runs just fine to vastly larger
         // scale.
         final String fact = 
            "(define (fact n) (if (< n 2) 1 (* n (fact (- n 1)))))";
         final JhwScm scm = newScm();
         expectSuccess(fact,        "",       scm);
         expectSuccess("fact",      "???",    scm);
         expectSuccess("(fact -1)", "1",      scm);
         expectSuccess("(fact 0)",  "1",      scm);
         expectSuccess("(fact 1)",  "1",      scm);
         expectSuccess("(fact 2)",  "2",      scm);
         expectSuccess("(fact 3)",  "6",      scm);
         expectSuccess("(fact 4)",  "24",     scm);
         expectSuccess("(fact 5)",  "120",    scm);
         expectSuccess("(fact 6)",  "720",    scm);
         expectSuccess("(fact 10)", "3628800",scm);
         System.out.println("fact simple:");
         System.out.println("  numCallsToCons: " + scm.numCallsToCons);
         System.out.println("  maxHeapTop:     " + scm.maxHeapTop);
      }
      {
         final String help = 
            "(define (help n a) (if (< n 2) a (help (- n 1) (* n a))))";
         final String fact = 
            "(define (fact n) (help n 1))";
         final JhwScm scm = newScm();
         expectSuccess(fact,        "",       scm);
         expectSuccess(help,        "",       scm); // note, define help 2nd ;)
         expectSuccess("fact",      "???",    scm);
         expectSuccess("help",      "???",    scm);
         expectSuccess("(fact -1)", "1",      scm);
         expectSuccess("(fact 0)",  "1",      scm);
         expectSuccess("(fact 1)",  "1",      scm);
         expectSuccess("(fact 2)",  "2",      scm);
         expectSuccess("(fact 3)",  "6",      scm);
         expectSuccess("(fact 4)",  "24",     scm);
         expectSuccess("(fact 5)",  "120",    scm);
         expectSuccess("(fact 6)",  "720",    scm);
         expectSuccess("(fact 10)", "3628800",scm);
         System.out.println("fact 2/ help:");
         System.out.println("  numCallsToCons: " + scm.numCallsToCons);
         System.out.println("  maxHeapTop:     " + scm.maxHeapTop);
      }

      {
         // ambition: nontrivial user-defined recursive function
         // 
         // This one, the Fibonacci Sequence, is not tail-recursive
         // and will eat O(n) stack space and runs in something awful
         // like O(n*n) or worse - making it also a good stressor for
         // garbage collection.
         // 
         // Fib can be made half-tail-recursive though...
         // 
         // Later, there exists a good memoized dynamic programming
         // version which would be good for comparison.
         //
         final String fib = 
            "(define (fib n) (if (< n 2) n (+ (fib (- n 1)) (fib (- n 2)))))";
         final JhwScm scm = newScm();
         expectSuccess(fib,"",scm);
         expectSuccess("fib","???",scm);
         expectSuccess("(fib 0)","0",scm);
         expectSuccess("(fib 1)","1",scm);
         expectSuccess("(fib 2)","1",scm);
         expectSuccess("(fib 3)","2",scm);
         expectSuccess("(fib 4)","3",scm);
         expectSuccess("(fib 5)","5",scm);
         
         // Before gc, (fib 6) would OOM at heapSize 32 kcells, (fib
         // 10) at 128 kcells.
         //
         // With CLEVER_STACK_RECYCLING, both fit in under 4 kcells.
         //
         // W/ CLEVER_STACK_RECYCLING, (fib 20) shows a maxHeapTop of
         // 400 kslots, but it burned through 34 mcells on the way
         // there!
         //
         expectSuccess("(fib 6)","8",scm);     // OOM at 32 kcells
         expectSuccess("(fib 10)","55",scm);   // OOM at 128 kcells
         if ( false )
         {
            // Takes like a minute...
            expectSuccess("(fib 20)","6765",scm); // OOM at 256 kcells, unknown
         }
         System.out.println("fib:");
         System.out.println("  numCallsToCons: " + scm.numCallsToCons);
         System.out.println("  maxHeapTop:     " + scm.maxHeapTop);
      }

      // min, max, bounds, 2s-complement nature of fixints
      //
      // TODO: it would be nice if this were fancy, if worked without
      // knowing a-priori how many bits were in the fixints, found it,
      // and checked edges.
      //
      // Of course, you'd also want that test to be halting if fixints
      // ever started autopromoting to bigints... :)
      //
      expectSuccess("268435455", "-1");
      expectSuccess("268435456", "0");
      expectSuccess("268435457", "1");
      expectSuccess("(+ 268435455 0)", "-1");
      expectSuccess("(+ 268435456 0)", "0");
      expectSuccess("(+ 268435456 1)", "1");
      expectSuccess("(+ 268435457 1)", "2");
      expectSuccess("(+ 134217728 134217727)", "-1");
      expectSuccess("(- 0 268435456)", "0");
      expectSuccess("(- 0 268435455)", "1");
      expectSuccess("(equal? 0 268435456)", "#t");
      expectSuccess("(equal? 0 (* 100 268435456))", "#t");

      // let, local scopes:
      // 
      expectSemantic("(let ())");
      expectSuccess("(let () 32)","32");
      expectSuccess("(let()32)","32");
      expectSuccess("(let ((a 10)) (+ a 32))","42");
      expectSuccess("(let ((a 10) (b 32)) (+ a b))","42");
      expectSemantic("(let ((a 10) (b 32)) (+ a c))");
      expectSemantic("(let ((a 10) (b a)) b)");
      expectSemantic("(let ((a 10) (b (+ a 1))) b)");
      if ( true )
      {
         // Heh, guard that those names stay buried.  
         //
         // An overly simple early form of sub_let pushed frames onto
         // the env, but didn't pop them.
         final JhwScm scm = newScm();
         expectSuccess("(let ((a 10)) a)", "10", scm);
         expectSemantic("a",scm);
      }

      // nested lexical scopes:
      if ( true )
      {
         expectSuccess("(let ((a 10)) (let ((b 32)) (+ a b)))","42");
         expectSuccess("(let ((a 10)) (let ((b (+ 32 a))) b))","42");
      }
      {
         // SURPRISE!  This is SUPPOSED to fail for (fact 2) or higher!!!!!
         //
         // My new-and-improved closures are not broken, they're right!
         //
         // Of course, in the meantime I've still discovered that
         // sub_let is broken....
         //
         final String fact = 
            "(define (fact n)"                                               +
            "  (let ((help"                                                  +
            "        (lambda (n a) (if (< n 2) a (help (- n 1) (* n a))))))" +
            "    (help n 1)))";
         final JhwScm scm = newScm();
         expectSuccess(fact,       "",   scm);
         expectSuccess("fact",     "???",scm);
         expectSuccess("(fact -1)","1",  scm);
         expectSuccess("(fact 0)", "1",  scm);
         expectSuccess("(fact 1)", "1",  scm);
         if ( true )
         {
            expectSemantic("(fact 2)"); // HAHAHHAHHAHAHA
         }
         else
         {
            // TODO: save it for letrec*
            SILENT = false;
            expectSuccess("(fact 2)", "2",  scm); // HAHAHHAHHAHAHA
            expectSuccess("(fact 3)", "6",  scm);
            expectSuccess("(fact 4)", "24", scm);
            expectSuccess("(fact 5)", "120",scm);
            expectSuccess("(fact 6)", "720",scm);
         }
      }

      // control special form: (begin)
      //
      // We need user-code-accesible side-effects to detect that
      // (begin) is really doing the earlier items... though in a
      // pinch we can use (define) for that, but it might confound
      // with other syntactic special cases.
      // 
      expectSuccess("(begin)",          "");
      expectSuccess("(begin 1)",        "1");
      expectSuccess("(begin 1 2 3 4 5)","5");

      // control special form: cond 
      //
      // We need user-code-accesible side-effects to detect that (cond)
      // only evaluates the matching clause.
      expectSuccess("(cond)","");
      expectSuccess("(cond (#f 2))","");
      expectSuccess("(cond (#t 1) (#f 2))","1");
      expectSuccess("(cond (#f 1) (#t 2))","2");
      expectSuccess("(cond (#t 1) (#t 2))","1");
      expectSuccess("(cond (#f 1) (#f 2))","");
      expectSuccess("(cond ((equal? 3 4) 1) ((equal? 5 (+ 2 3)) 2))","2");
      expectSuccess("(cond (#f) (#t 2))","2");
      expectSuccess("(cond (#f) (#t))","#t");
      expectSuccess("(cond (#f) (7))","7");
      if ( false )
      {
         // You know, "else" is really special-casey, special-syntaxy.
         // Really suggests that (cond) just isn't fundamental and
         // doesn't belong in the VM.  It might as well be an (if)
         // chain anyhow, it is required to be sequential in testing
         // and to only evaluate one body.
         //
         // So it probably should be cut from the microcode layer.
         // But in any case, I'm not implementing "else" for now.
         SILENT = false;
         expectSuccess("(cond ((equal? 3 4) 1) (else 2))","2");
         expectSemantic("else"); // else is *not* just bound to #t!
      }

      // TODO: let*, letrec
      //
      // Totally not fundamental, even less so than let.  Wait.

      // TODO: control special form: case
      //
      // We need user-code-accesible side-effects to detect that (case)
      // only evaluates the matching clause.
      expectSemantic("(case)");
      expectSemantic("(case 1)");
      expectSemantic("(case 1 1)");
      expectSemantic("(case 1 (1))");
      expectSemantic("(case 1 (1 1))");
      expectSuccess("(case 1 ((1) 1))","1");
      expectSuccess("(case 7 ((2 3) 100) ((4 5) 200) ((6 7) 300))","300");
      expectSuccess("(case 7 ((2 3) 100) ((6 7) 200) ((4 5) 300))","200");
      expectSuccess("(case 7 ((2 3) 100) ((4 5) 200))",            "");
      expectSuccess("(case  (+ 3 4) ((2 3) 100) ((6 7      ) 200))","200");
      expectSuccess("(case  (+ 3 4) ((2 3) 100) ((6 (+ 3 4)) 200))","");
      expectSuccess("(case '(+ 3 4) ((2 3) 100) ((6 (+ 3 4)) 200))","");
      expectSuccess("(case 7 (() 10) ((7) 20))","20"); // empty label ok
      expectSemantic("(case 7 ((2 3) 100) ((6 7)))");  // bad clause
      expectSemantic("(case 7 ((2 3)) ((6 7) 1))");    // bad clause
      expectSemantic("(case 7 (()) ((7) 20))");        // bad clause
      expectSemantic("(case 7 (10) ((7) 20))");        // bad clause
      expectSuccess("(case '7 ((2 3) 100) ((6 '7) 200) ((4 5) 300))","");
      expectSuccess("(case \"7\" ((2 3) 100) ((6 \"7\") 200) ((4 5) 300))","");
      expectSuccess("(case #t ((2 3) 100) ((6 #t) 200) ((4 5) 300))","200");
      expectSuccess("(case #f ((2 3) 100) ((6 #f) 200) ((4 5) 300))","200");
      expectSuccess("(case #\\a ((2 3) 100) ((6 #\\a) 200) ((4 5) 300))","200");
      if ( false )
      {
         // See "else" rant among (cond) tests.
         expectSuccess("(case 7 ((2 3) 100) ((4 5) 200) (else 300))", "300");
      }
      if ( false )
      {
         // TODO: an error if a case label is duplicated!
         expectSemantic("(case 7 ((5 5) 100))");
         expectSemantic("(case 7 ((5 3) 100) ((4 5) 200))");
      }

      // variadic (lambda) and (define), inner defines, etc.
      // 
      expectSuccess("(lambda () (+ 1 2) 7)","???");
      expectSuccess("((lambda () (+ 1 2) 7))","7");
      expectSuccess("((lambda () (display (+ 1 2)) 7))","37");
      {
         // Are the nested-define defined symbols in scope of the
         // "real" body?
         final JhwScm scm = newScm();
         expectSuccess("(define (a x) (define b 2) (+ x b))", "",    scm);
         expectSuccess("(a 10)",                              "12",  scm);
         expectSuccess("a",                                   "???", scm);
         expectSemantic("b",                                         scm);
      }
      {
         // Can we do more than one?
         final JhwScm scm = newScm();
         expectSuccess("(define (f) (define a 1) (define b 2) (+ a b))","",scm);
         expectSuccess("(f)","3",scm);
      }
      {
         // Can we do it for an inner helper function?
         final JhwScm scm = newScm();
         final String fact = 
            "(define (fact n)"                                            +
            "  (define (help n a) (if (< n 2) a (help (- n 1) (* n a))))" +
            "  (help n 1))";
         expectSuccess(fact,       "",   scm);
         expectSuccess("fact",     "???",scm);
         expectSuccess("(fact -1)","1",  scm);
         expectSuccess("(fact 0)", "1",  scm);
         expectSuccess("(fact 1)", "1",  scm);
         expectSuccess("(fact 2)", "2",  scm);
         expectSuccess("(fact 3)", "6",  scm);
         expectSuccess("(fact 4)", "24", scm);
         expectSuccess("(fact 5)", "120",scm);
         expectSuccess("(fact 6)", "720",scm);
      }
      {
         // Do nested defines really act like (begin)?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f) (define a 1) (display 8) (define b 2) (+ a b))";
         expectSuccess(def,"",scm);
         expectSuccess("(f)","83",scm);
      }
      {
         // Do nested defines really act like (begin) when we have args?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f b) (define a 1) (display 8) (+ a b))";
         expectSuccess(def,"",scm);
         expectSuccess("(f 4)","85",scm);
         expectSuccess("(f 3)","84",scm);
      }
      {
         // Are nested defines in one another's scope, in any order?
         final JhwScm scm = newScm();
         final String F = 
            "(define (F b) (define a 1) (define (g x) (+ a x)) (g b))";
         final String G = 
            "(define (G b) (define (g x) (+ a x)) (define a 1) (g b))";
         expectSuccess(F,"",scm);
         expectSuccess("(F 4)","5",scm);
         expectSuccess(G,"",scm);
         expectSuccess("(G 4)","5",scm);
      }
      {
         // What about defines in lambdas?
         final JhwScm scm = newScm();
         final String def = 
            "((lambda (x) (define a 7) (+ x a)) 5)";
         expectSuccess(def,"12",scm);
      }
      {
         // What about closures?
         final JhwScm scm = newScm();
         final String def = 
            "(((lambda (x) (lambda (y) (+ x y))) 10) 7)";
         expectSuccess(def,"17",scm);
      }
      {
         // What about closures?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f x) (lambda (y) (+ x y)))";
         expectSuccess(def,"",scm);
         expectSuccess("(f 10)","???",scm);
         expectSuccess("((f 10) 7)","17",scm);
      }
      {
         // What about closures?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f x) (define (h y) (+ x y)) h)";
         expectSuccess(def,"",scm);
         expectSuccess("(f 10)","???",scm);
         expectSuccess("((f 10) 7)","17",scm);
      }

      {
         // variadic stress on the body of let
         expectSuccess("(let ((a 1)) (define b 2) (+ a b))","3");
         expectSuccess("(let ((a 1)) (display 7) (define b 2) (+ a b))","73");
         expectSemantic("(let ((a 1)))");
      }

      // check that map works w/ both builtins and user-defineds
      {
         final JhwScm scm = newScm();
         final String def = "(define (f x) (+ x 10))";
         expectSuccess("(map display '())",      "()");     
         expectSuccess("(map display '(1 2 3))", "123(  )");

         // TODO: On the preceeding, Guile sez:
         //
         //   guile> (map display '(1 2 3))
         //   123(#<unspecified> #<unspecified> #<unspecified>)
         //
         // So when we get fancier, maybe we want VOID to be
         // nonprinting at the top level, but somehow printing at
         // other levels.
         //
         // In any case, it is clear that Guile *does* have a concept
         // of (display) having a concrete return value instead of
         // just C-style void, and that that value is not the same as
         // the empty list, or the same as false.
         //
         // I am reconciled with VOID and/or UNDEFINED.  They mean we
         // need a couple of special cases in sub_eval and sub_print,
         // but we avoid the more frequent special case of dealing
         // with subs-that-return-nothing.
         //
         // Update: I have merged VOID and UNDEFINED into UNSPECIFIED.

         expectSuccess(def,                      "",           scm);
         expectSuccess("f",                      "???",        scm);
         expectSuccess("(f 13)",                 "23",         scm);
         expectSuccess("(map f '())",            "()",         scm);
         expectSuccess("(map f '(1 2 3))",       "(11 12 13)", scm);
      }
      
      // TODO: user-level variadics
      if ( false )
      {
         SILENT = false;
         expectSuccess("((lambda x x) 3 4 5 6)",              "(3 4 5 6)");
         expectSuccess("((lambda ( . x)) 3 4 5 6)",           "(3 4 5 6)");
         expectSuccess("((lambda (x y . z) z) 3 4 5 6)",      "(5 6)");
         expectSuccess("(define (f x y . z) z)(foo 3 4 5 6)", "(5 6)");
      }

      // TODO: error for names to collide in formals:
      if ( false )
      {
         SILENT = false;
         expectSemantic("(lambda (x x) 1)");
         expectSemantic("(lambda (x a x) 1)");
         expectSemantic("(lambda (a x b x) 1)");
         expectSemantic("(lambda (x a x x) 1)");
         expectSemantic("(define (f x x) 1)");
         expectSemantic("(define (f x a x) 1)");
         expectSemantic("(define (f a x b x) 1)");
         expectSemantic("(define (f x a x x) 1)");
         expectSemantic("(let ((x 1) (x 2)) 1)");
         expectSemantic("(let ((x 1) (x 2)) 1)");
         expectSemantic("(let ((x 1) (a 10) (x 2)) 1)");
         expectSemantic("(let ((a 10) (x 1) (b 20) (x 2)) 1)");
         expectSemantic("(let ((x 1) (a 10) (x 2) (b 20)) 1)");
      }

      // Here's an interesting thing:
      //
      // Guile:
      //
      //   guile> (display (read)).(newline)
      //   #{.}#
      //
      // Scsh:
      //
      //   > (display (read)).(newline)
      //   
      //   Error: unexpected " . "
      //          #{Input-fdport #{Input-channel "standard input"}}
      //
      // I wonder about what Guile is doing.  Could #{.}# be the
      // external representation of the special syntactic token used
      // for dotted lists?  No:
      //
      // Guile:
      //
      //   guile> .
      //   ERROR: Unbound variable: #{.}#
      //   ABORT: (unbound-variable)
      //   guile> #{.}#
      //   ERROR: Unbound variable: #{.}#
      //   ABORT: (unbound-variable)
      //
      // Seems that #{.}# means .-as-symbol.  However:
      //
      //   guile> (define #{.}# 10)
      //   
      //   Backtrace:
      //   In current input:
      //   2: 0* (define . 10)
      //   
      //   <unnamed port>:2:1: In procedure memoization in expression (define . 10):
      //   <unnamed port>:2:1: In line 1: Bad expression (define . 10).
      //   ABORT: (syntax-error)
      //
      // Weird.  So is it or is it not a symbol?

      System.out.println();
      System.out.println("overall num cons: " + JhwScm.UNIVERSAL_NUM_CONS);
   }

   private static void expectSuccess ( final String expr, final String expect )
      throws java.io.IOException
   {
      expectSuccess(expr,expect,null);
   }

   private static void expectSuccess ( final String expr, 
                                       final String expect,
                                       JhwScm       scm )
      throws java.io.IOException
   {
      final StringBuilder buf = new StringBuilder();
      if ( null == scm )
      {
         scm = newScm();
      }
      final int icode = input(scm,expr);
      assertEquals("input failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   icode);
      final int dcode = scm.drive(-1);
      assertEquals("drive failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   dcode);
      final int ocode = output(scm,buf);
      assertEquals("output failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   ocode);
      if ( null != expect )
      {
         assertEquals("result failure on \"" + expr + "\":",
                      expect,
                      buf.toString());
      }
      if ( verbose )
      {
         System.out.print("pass: expr \"");
         System.out.print(expr);
         System.out.print("\" evaluated to \"");
         System.out.print(buf);
         System.out.print("\"");
         System.out.println("\"");
      }
   }

   private static void expectLexical ( final String expr )
      throws java.io.IOException
   {
      expectFailure(expr,null,JhwScm.FAILURE_LEXICAL);
   }

   private static void expectLexical ( final String expr, JhwScm scm )
      throws java.io.IOException
   {
      expectFailure(expr,scm,JhwScm.FAILURE_LEXICAL);
   }

   private static void expectSemantic ( final String expr )
      throws java.io.IOException
   {
      expectFailure(expr,null,JhwScm.FAILURE_SEMANTIC);
   }

   private static void expectSemantic ( final String expr, JhwScm scm )
      throws java.io.IOException
   {
      expectFailure(expr,scm,JhwScm.FAILURE_SEMANTIC);
   }

   private static void expectFailure ( final String expr, 
                                       JhwScm       scm,
                                       final int    expectedError )
      throws java.io.IOException
   {
      if ( null == scm )
      {
         scm = newScm();
      }
      final int icode = input(scm,expr);
      assertEquals("input failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   icode);
      final int dcode = scm.drive(-1);
      if ( JhwScm.SUCCESS == dcode )
      {
         final StringBuilder buf = new StringBuilder();
         final int ocode         = output(scm,buf);
         System.out.print("unexpected success: expr \"");
         System.out.print(expr);
         System.out.print("\" evaluated to \"");
         System.out.print(buf);
         System.out.print("\"");
         System.out.println();
      }
      assertEquals("should fail evaluating \"" + expr + "\":",
                   expectedError, 
                   dcode);
   }

   /**
    * Convenience wrapper around JhwScm.input(byte[],int,int).
    *
    * Places all characters into the VM's input queue.
    *
    * On failure, the VM's input queue is left unchanged: input() is
    * all-or-nothing.
    *
    * @param input characters to be copied into the VM's input queue.
    * @throws nothing, not ever
    * @returns SUCCESS on success, otherwise an error code.
    */
   private static int input ( final JhwScm scm, final CharSequence input ) 
   {
      final byte[] buf = input.toString().getBytes();
      int off = 0;
      while ( off < buf.length )
      {
         final int len = buf.length - off;
         final int n   = scm.input(buf,off,len);
         if ( n < 0 )
         {
            return n;
         }
         off += n;
      }
      return JhwScm.SUCCESS;
   }


   /**
    * Convenience wrapper around JhwScm.output(byte[],int,int).
    */
   private static int output ( final JhwScm scm, final Appendable out ) 
      throws IOException
   {
      final byte[] buf = new byte[1+debugRand.nextInt(10)];
      for ( int off = 0; true; )
      {
         final int n = scm.output(buf,off,buf.length-off);
         if ( -1 > n )
         {
            return n; // error code
         }
         if ( -1 == n )
         {
            return JhwScm.SUCCESS;
         }
         for ( int i = off; i < off+n; ++i )
         {
            out.append((char)buf[i]);
         }
         off += n;
         if ( off >= buf.length )
         {
            off = 0;
         }
      }
   }

}
