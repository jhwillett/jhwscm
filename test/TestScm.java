/**
 * Test harness for JhwScm.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

import static org.junit.Assert.assertEquals;

import java.util.Random;
import java.io.IOException;

public class TestScm
{
   private static final boolean verbose = false;

   private static final Random debugRand = new Random(1234);

   private static final int LEXICAL  = JhwScm.FAILURE_LEXICAL;
   private static final int SEMANTIC = JhwScm.FAILURE_SEMANTIC;

   private static class BatchType
   {
      final boolean reuse;
      final boolean do_rep;
      
      BatchType ( final boolean reuse, final boolean do_rep )
      {
         this.reuse  = reuse;
         this.do_rep = do_rep;
      }
   }
   private static final BatchType REP_DEPENDANT   = new BatchType(true, true);
   private static final BatchType REP_INDEPENDANT = new BatchType(false,true);
   private static final BatchType RE_DEPENDANT    = new BatchType(true, false);
   private static final BatchType RE_INDEPENDANT  = new BatchType(false,false);

   private static boolean DO_REP = true;
   private static boolean SILENT = true;
   private static boolean DEBUG  = false;

   private static boolean REPORT = true;

   private static int numExpects        = 0;
   private static int numBatches        = 0;
   private static int numHappyExpects   = 0;
   private static int numUnhappyExpects = 0;

   private static JhwScm newScm ( final boolean do_rep )
   {
      return new JhwScm(do_rep,SILENT,DEBUG);
   }

   private static JhwScm newScm ()
   {
      return new JhwScm(DO_REP,SILENT,DEBUG);
   }

   private static void ioEdgeCases ()
   {
      assertEquals(JhwScm.BAD_ARG,newScm().input(null,0,0));
      assertEquals(JhwScm.BAD_ARG,newScm().output(null,0,0));

      assertEquals(JhwScm.BAD_ARG,newScm().input(new byte[0],-1,0));
      assertEquals(JhwScm.BAD_ARG,newScm().input(new byte[0],0,-1));
      assertEquals(JhwScm.BAD_ARG,newScm().input(new byte[0],2,3));
      assertEquals(JhwScm.BAD_ARG,newScm().input(new byte[0],3,2));
      assertEquals(JhwScm.BAD_ARG,newScm().output(new byte[0],-1,0));
      assertEquals(JhwScm.BAD_ARG,newScm().output(new byte[0],-1,0));
      assertEquals(JhwScm.BAD_ARG,newScm().output(new byte[0],0,-1));
      assertEquals(JhwScm.BAD_ARG,newScm().output(new byte[0],0,-1));
      assertEquals(JhwScm.BAD_ARG,newScm().output(new byte[0],2,3));
      assertEquals(JhwScm.BAD_ARG,newScm().output(new byte[0],3,2));
      assertEquals(JhwScm.BAD_ARG,newScm().output(new byte[3],2,3));

      assertEquals(0,             newScm().input(new byte[0],0,0));
      assertEquals(0,             newScm().input(new byte[0],0,0));
      assertEquals(0,             newScm().input(new byte[0],0,0));
      assertEquals(0,             newScm().input(new byte[1],1,0));
      assertEquals(0,             newScm().output(new byte[0],0,0));
      assertEquals(-1,            newScm().output(new byte[5],2,3));
   }

   private static void driveEdgeCases ()
   {
      assertEquals(JhwScm.INCOMPLETE,newScm().drive(0));
      assertEquals(JhwScm.SUCCESS,   newScm().drive(-1)); // run-to-completion
      assertEquals(JhwScm.BAD_ARG,   newScm().drive(-2));
      assertEquals(JhwScm.BAD_ARG,   newScm().drive(-3));
   }

   public static void main ( final String[] argv )
      throws java.io.IOException
   {
      ioEdgeCases();

      driveEdgeCases();

      expect("","");

      // first content: simple integer expressions are self-evaluating
      // and self-printing.
      final String[] simpleInts = { 
         "0", "1", "1234", "-1", "-4321", "10", "1001", 
      };
      for ( final String expr : simpleInts )
      {
         final Object[][] tests = { 
            { expr,                   expr },
            { " " + expr,             expr },
            { expr + " " ,            expr },
            { " " + expr + " ",       expr },
            { "\n" + expr,            expr },
            { expr + "\n" ,           expr },
            { "\t" + expr + "\t\r\n", expr },
         };
         batch(tests,RE_INDEPENDANT);
         batch(tests,RE_DEPENDANT);
         batch(tests,REP_INDEPENDANT);
         batch(tests,REP_DEPENDANT);
      }


      // second content: tweakier integer expressions are self-reading
      // and self-evaluating but not self-printing.
      final String[][] tweakyInts = { 
         { "007", "7" }, { "-007", "-7" }, {"-070", "-70" },
      };
      for ( final String[] pair : tweakyInts )
      {
         final Object[][] tests = { 
            { pair[0],             pair[1] },
            { " " + pair[0],       pair[1] },
            { pair[0] + " " ,      pair[1] },
            { " " + pair[0] + " ", pair[1] },
         };
         batch(tests,RE_INDEPENDANT);
         batch(tests,RE_DEPENDANT);
         batch(tests,REP_INDEPENDANT);
         batch(tests,REP_DEPENDANT);
      }

      // boolean literals are self-evaluating and self-printing
      {
         final Object[][] tests = { 
            { "#t",   "#t" },
            { "#f",   "#f" },
            { " #t ", "#t" },
            { "#f ",  "#f" },
            { " #f",  "#f" },
            { "#x",   LEXICAL },
         };
         batch(tests,RE_INDEPENDANT);
         batch(tests,RE_DEPENDANT);
         batch(tests,REP_INDEPENDANT);
         batch(tests,REP_DEPENDANT);
      }

      // variables are self-reading and self-printing, but unbound
      // variables fail to evaluate
      {
         final Object[][] tests = { 
            { "a",       "a"       },
            { "a1",      "a1"      },
            { "a_0-b.c", "a_0-b.c" },
         };
         batch(tests,RE_INDEPENDANT);
         batch(tests,RE_DEPENDANT);
      }      
      {
         final Object[][] tests = { 
            { "a",  SEMANTIC },
            { "a1", SEMANTIC },
         };
         batch(tests,REP_INDEPENDANT);
      }  

      // some lexical, rather than semantic, error case expectations
      {
         final Object[][] tests = { 
            { "()",               SEMANTIC },
            { "\r(\t)\n",         SEMANTIC },
            { "(",                LEXICAL  },
            { " (  ",             LEXICAL  },
            { ")",                LEXICAL  },
            { "(()())",           SEMANTIC },
            { "  ( ( )    ( ) )", SEMANTIC },
            { "(()()))",          SEMANTIC }, // SEMANTIC before dangling ')'
            { " ( () ())) ",      SEMANTIC }, // SEMANTIC before dangling ')'
            { "((()())",          LEXICAL  },
            { "(())",             SEMANTIC },
         };
         batch(tests,REP_INDEPENDANT);
      }  

      expect("-",   "-",  newScm(false));
      expect("-asd", "-asd",  newScm(false));
      expect("-",   null);
      expect("-as",SEMANTIC);
      
      {
         final Object[][] tests = { 
            { "(a b c)",          SEMANTIC },
            { "(a (b c))",        SEMANTIC },
            { "((a b) c)",        SEMANTIC },
            { "((a b c))",        SEMANTIC },
            { "((a b) c",          LEXICAL },
            { "((a b c)",          LEXICAL },
         };
         batch(tests,REP_INDEPENDANT);
      }  

      {
         final Object[][] tests = { 
            { "(a b c)",      "(a b c)"    },
            { "(a (b c))",    "(a (b c))"  },
            { "((a b) c)",    "((a b) c)"  },
            { "((a b c))",    "((a b c))"  },
            { "((a)b)",       "((a) b)"    },
            { "((a )b)",      "((a) b)"    },
            { "((a ) b)",     "((a) b)"    },
            { "( (a )b)",     "((a) b)"    },
            { "( (a) b)",     "((a) b)"    },
            { "( (a)b )",     "((a) b)"    },
            { "((a b) c",     LEXICAL      },
            { "((a b c)",     LEXICAL      },
            { "()",           "()"         },
            { "\r(\t)\n",     "()"         },
            { "(",            LEXICAL      },
            { " (  ",         LEXICAL      },
            { ")",            LEXICAL      },
            { "(()())",       "(() ())"    },
            { " ( ( ) ( ) )", "(() ())"    },
            { "(()()))",      LEXICAL      },
            { " ( () ())) ",  LEXICAL      },
            { "((()())",      LEXICAL      },
         };
         batch(tests,RE_INDEPENDANT);
      }  

      // improper list experssions: yay!
      expect("(1 . 2)",      "(1 . 2)",   newScm(false));
      expect("(1 2 . 3)",    "(1 2 . 3)", newScm(false));
      expect("(1 . 2 3)", LEXICAL,  newScm(false));
      expect("( . 2 3)", LEXICAL,   newScm(false));
      expect("(1 . )", LEXICAL,   newScm(false));
      expect("(1 .)", LEXICAL,   newScm(false));
      expect("(1 . 2 3)",LEXICAL);
      expect("( . 2 3)",LEXICAL);
      expect("(1 . )",LEXICAL);
      expect("(1 .)",LEXICAL);

      expect("(1 . ())",     "(1)",       newScm(false));
      expect("(1 .())",      "(1)",       newScm(false));

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
      expect("( . 2 )",    "2",           newScm(false));
      expect("( . 2 )",    "2",           newScm(true));
      expect("( . () )",   "()",          newScm(false));
      expect("( . 2 3 )",LEXICAL);
      expect("(. abc )",   "abc",         newScm(false));

      if ( false )
      {
         // Probably not until I handle floats!
         SILENT = false;
         expect("(1 .2)",       "(1 0.2)",   newScm(false));
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
         expect(expr,                   expr);
         expect(" " + expr,             expr);
         expect(expr + " " ,            expr);
         expect(" " + expr + " ",       expr);
         expect("\n" + expr,            expr);
         expect(expr + "\n" ,           expr);
         expect("\t" + expr + "\t\r\n", expr);
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
         expect(pair[0],             pair[1]);
         expect(" " + pair[0],       pair[1]);
         expect(pair[0] + " " ,      pair[1]);
         expect(" " + pair[0] + " ", pair[1]);
      }
      expect("#",LEXICAL);
      expect("#\\",LEXICAL);
      expect("# ",LEXICAL);
      expect("#\n",LEXICAL);
      if ( false )
      {
         expect("#\\spac",LEXICAL);
         expect("#\\asdf",LEXICAL);
      }

      // string literals expressions are self-evaluating and
      // self-printing.
      final String[] simpleStrs = { 
         "\"\"", "\" \"", "\"\t\"", "\"a\"", "\"Hello, World!\"",
      };
      for ( final String expr : simpleStrs )
      {
         expect(expr,                   expr);
         expect(" " + expr,             expr);
         expect(expr + " " ,            expr);
         expect(" " + expr + " ",       expr);
         expect("\n" + expr,            expr);
         expect(expr + "\n" ,           expr);
         expect("\t" + expr + "\t\r\n", expr);
      }
      expect("\"",LEXICAL);
      expect("\"hello",LEXICAL);

      // Check some basic symbol bindings are preset for us which
      // would enable our upcoming (eval) tests...
      //
      // R5RS and R6RS do not specify how these print - and so far
      // neither do I.  So there's no unit test here, just that they
      // evaluate OK.
      //
      expect("+",       null);
      expect("*",       null);
      expect("cons",    null);
      expect("car",     null);
      expect("cdr",     null);
      expect("list",    null);
      expect("if",      null);
      expect("quote",   null);
      expect("define",  null);
      expect("lambda",  null);

      // Dipping my toe into basic edge cases around implied (eval)
      // and some simple arithmetic - more than testing math.
      //
      expect("(())",SEMANTIC);
      expect("(1)",SEMANTIC);
      expect("(\"a\")",SEMANTIC);
      expect("(#\\a)",SEMANTIC);
      expect("(() 0)",SEMANTIC);
      expect("(1 0)",SEMANTIC);
      expect("(\"a\" 0)",SEMANTIC);
      expect("(#\\a 0)",SEMANTIC);
      expect("(+ 0 0)", "0");
      expect("(+ 0 1)", "1");
      expect("(+ 0 2)", "2");
      expect("(+ 2 0)", "2");
      expect("(+ 2 3)", "5");
      expect("(+ 2 -3)","-1");
      expect("(+ -2 -3)", "-5");
      expect("(- 2  3)","-1");
      expect("(- 2 -3)","5");
      expect("(- -3 2)","-5");
      expect("(+ 100 2)","102");
      expect("(+ 100 -2)","98");
      expect("(* 97 2)","194");
      expect("(* -97 2)","-194");
      expect("(* -97 -2)","194");
      expect("(* 97 -2)","-194");
      expect("(+ a b)",SEMANTIC);

      if ( true )
      {
         // TODO: note, for now + and * are binary only: this will
         // change!
         expect("(+ 0)",SEMANTIC);
         expect("(+)",SEMANTIC);
         expect("(+ 1)",SEMANTIC);
         expect("(* 2 3 5)",SEMANTIC);
         expect("(*)",SEMANTIC);
      }
      else
      {
         expect("(+ 0)","0");
         expect("(+)","0");
         expect("(+ 1)","1");
         expect("(* 2 3 5)","30");
         expect("(*)","1");
      }
      expect("(+0)",      "0");
      expect("(+1 10)",   "10");
      expect("(+3 3 5 7)","15");
      expect("(+0 1)",SEMANTIC);
      expect("(+1)",SEMANTIC);
      expect("(+1 1 2)",SEMANTIC);
      expect("(+3)",SEMANTIC);
      expect("(+3 1 2)",SEMANTIC);
      expect("(+3 1 2 3 4)",SEMANTIC);

      // simple special form
      expect("(quote ())","()");
      expect("(quote (1 2))","(1 2)");
      expect("(quote (a b))","(a b)");
      expect("(+ 1 (quote ()))",SEMANTIC);
      expect("(quote 9)",                "9");
      expect("(quote (quote 9))",        "(quote 9)");
      expect("(quote (quote (quote 9)))","(quote (quote 9))");

      // simple quote sugar
      expect("'()","()");
      expect("'(1 2)","(1 2)");
      expect("'(a b)","(a b)");
      expect("(+ 1 '())",SEMANTIC);
      expect("'9",       "9");
      if ( false )
      {
         // See the quote-quote-quote discussion in DIARY.txt.
         //
         // TODO: the quoted-quote question, and how to print it
         expect("''9",      "(quote 9)");
         expect("'''9",     "(quote (quote 9))");
         expect(" ' ' ' 9 ","(quote (quote 9))");
      }

      expect("(equal? 10  10)","#t");
      expect("(equal? 11  10)","#f");
      expect("(equal? 'a  'a)","#t");
      expect("(equal? 'a  'b)","#f");
      expect("(equal? 10  'b)","#f");
      expect("(<       9  10)","#t");
      expect("(<      10   9)","#f");
      expect("(<      10  10)","#f");
      expect("(<     -10   0)","#t");
      expect("(<      -1  10)","#t");
      expect("(<       0 -10)","#f");
      expect("(< 10 'a)",SEMANTIC);

      // simple conditionals: in Scheme, only #f is false
      // 
      // TODO: test w/ side effects that short-cut semantics work.
      //
      // TODO: when you do mutators, be sure to check that only one
      // alternative in an (if) is executed.
      //
      expect("(if #f 2 5)","5");
      expect("(if #t 2 5)","2");
      expect("(if '() 2 5)","2"); 
      expect("(if 0 2 5)","2");
      expect("(if 0 (+ 2 1) 5)","3");
      expect("(if 0 2 5)","2");
      expect("(if #t (+ 2 1) (+ 4 5))","3");
      expect("(if #f (+ 2 1) (+ 4 5))","9");
      expect("(if (equal? 1 1) 123 321)","123");
      expect("(if (equal? 2 1) 123 321)","321");

      // cons, car, cdr, and list have a particular relationship
      //
      expect("(cons 1 2)","(1 . 2)");
      expect("(car (cons 1 2))","1");
      expect("(cdr (cons 1 2))","2");
      expect("(list)","()");
      expect("(list 1 2)","(1 2)");
      expect("(car 1)",SEMANTIC);
      expect("(car '())",SEMANTIC);
      expect("(cdr 1)",SEMANTIC);
      expect("(cdr '())",SEMANTIC);
      expect("(cons 1 '())","(1)");

      // OUCH! w/ no garbage collection, no proper tail recursion, and
      // a 512 cell heap, this goes OOM.  More than 4 KB to interpret
      // that?
      //
      expect("(cons 1 (cons 2 '()))","(1 2)");

      // (read) and (print) are exposed, though of course print is
      // exposed as (display) via R5RS.  I am displeased, b/c
      // "read-eval-display" loop doesn't have the same robust
      // tradition.  SICP uses (print)...
      //
      expect("(display 5)","5");
      expect("(display 5)2","52");
      expect("(display (+ 3 4))(+ 1 2)","73");
      expect("(display '(+ 3 4))(+ 1 2)","(+ 3 4)3");
      expect("(read)5","5");
      expect("(read)(+ 1 2)","(+ 1 2)");
      
      // defining symbols
      {
         final JhwScm scm = newScm();
         expect("(define a 100)","",   scm);
         expect("a",             "100",scm);
         expect("(define a 100)","",   scm);
         expect("(define b   2)","",   scm);
         expect("(+ a b)",       "102",scm);
         expect("(+ a c)",SEMANTIC,            scm);
      }
      expect("(define a)",SEMANTIC);

      // redefining symbols
      expect("(define a 1)a(define a 2)a","12");

      {
         final JhwScm scm = newScm();
         expect("(define foo +)","",  scm);
         expect("(foo 13 18)",   "31",scm);
         expect("(foo 13 '())",SEMANTIC,      scm);
      }

      // defining functions
      expect("(lambda)",SEMANTIC);
      expect("(lambda ())",SEMANTIC);
      expect("(lambda () 1)","???");
      expect("((lambda () 1))","1");
      expect("((lambda () 1) 10)",SEMANTIC);
      expect("(lambda (a) 1)","???");
      expect("((lambda (a) 1) 10)","1");
      expect("((lambda (a) 1))",SEMANTIC);
      expect("((lambda (a) 1) 10 20)",SEMANTIC);
      expect("(lambda (a b) (* a b))",       "???");
      expect("((lambda (a) (* 3 a)) 13)",    "39");
      expect("((lambda (a b) (* a b)) 13 5)","65");
      {
         final JhwScm scm = newScm();
         expect("(define (foo a b) (+ a b))","",  scm);
         expect("(foo 13 18)",               "31",scm);
         expect("(foo 13 '())",SEMANTIC,                  scm);
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
         expect(fact,        "",       scm);
         expect("fact",      "???",    scm);
         expect("(fact -1)", "1",      scm);
         expect("(fact 0)",  "1",      scm);
         expect("(fact 1)",  "1",      scm);
         expect("(fact 2)",  "2",      scm);
         expect("(fact 3)",  "6",      scm);
         expect("(fact 4)",  "24",     scm);
         expect("(fact 5)",  "120",    scm);
         expect("(fact 6)",  "720",    scm);
         expect("(fact 10)", "3628800",scm);
         //report("fact simple:",scm.local);
      }
      {
         final String help = 
            "(define (help n a) (if (< n 2) a (help (- n 1) (* n a))))";
         final String fact = 
            "(define (fact n) (help n 1))";
         final JhwScm scm = newScm();
         expect(fact,        "",       scm);
         expect(help,        "",       scm); // note, define help 2nd ;)
         expect("fact",      "???",    scm);
         expect("help",      "???",    scm);
         expect("(fact -1)", "1",      scm);
         expect("(fact 0)",  "1",      scm);
         expect("(fact 1)",  "1",      scm);
         expect("(fact 2)",  "2",      scm);
         expect("(fact 3)",  "6",      scm);
         expect("(fact 4)",  "24",     scm);
         expect("(fact 5)",  "120",    scm);
         expect("(fact 6)",  "720",    scm);
         expect("(fact 10)", "3628800",scm);
         //report("fact 2/ help:",scm.local);
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
         expect(fib,"",scm);
         expect("fib","???",scm);
         expect("(fib 0)","0",scm);
         expect("(fib 1)","1",scm);
         expect("(fib 2)","1",scm);
         expect("(fib 3)","2",scm);
         expect("(fib 4)","3",scm);
         expect("(fib 5)","5",scm);
         
         // Before gc, (fib 6) would OOM at heapSize 32 kcells, (fib
         // 10) at 128 kcells.
         //
         // With CLEVER_STACK_RECYCLING, both fit in under 4 kcells.
         //
         // W/ CLEVER_STACK_RECYCLING, (fib 20) shows a maxHeapTop of
         // 400 kwordss, but it burned through 34 mcells on the way
         // there!
         //
         expect("(fib 6)","8",scm);     // OOM at 32 kcells
         expect("(fib 10)","55",scm);   // OOM at 128 kcells
         if ( false )
         {
            // Takes like a minute...
            expect("(fib 20)","6765",scm); // OOM at 256 kcells, unknown
         }
         //report("fib:",scm.local);
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
      expect("268435455", "-1");
      expect("268435456", "0");
      expect("268435457", "1");
      expect("(+ 268435455 0)", "-1");
      expect("(+ 268435456 0)", "0");
      expect("(+ 268435456 1)", "1");
      expect("(+ 268435457 1)", "2");
      expect("(+ 134217728 134217727)", "-1");
      expect("(- 0 268435456)", "0");
      expect("(- 0 268435455)", "1");
      expect("(equal? 0 268435456)", "#t");
      expect("(equal? 0 (* 100 268435456))", "#t");

      // let, local scopes:
      // 
      expect("(let ())",SEMANTIC);
      expect("(let () 32)","32");
      expect("(let()32)","32");
      expect("(let ((a 10)) (+ a 32))","42");
      expect("(let ((a 10) (b 32)) (+ a b))","42");
      expect("(let ((a 10) (b 32)) (+ a c))",SEMANTIC);
      expect("(let ((a 10) (b a)) b)",SEMANTIC);
      expect("(let ((a 10) (b (+ a 1))) b)",SEMANTIC);
      if ( true )
      {
         // Heh, guard that those names stay buried.  
         //
         // An overly simple early form of sub_let pushed frames onto
         // the env, but didn't pop them.
         final JhwScm scm = newScm();
         expect("(let ((a 10)) a)", "10", scm);
         expect("a",SEMANTIC,scm);
      }

      // nested lexical scopes:
      if ( true )
      {
         expect("(let ((a 10)) (let ((b 32)) (+ a b)))","42");
         expect("(let ((a 10)) (let ((b (+ 32 a))) b))","42");
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
         expect(fact,       "",   scm);
         expect("fact",     "???",scm);
         expect("(fact -1)","1",  scm);
         expect("(fact 0)", "1",  scm);
         expect("(fact 1)", "1",  scm);
         if ( true )
         {
            expect("(fact 2)",SEMANTIC); // HAHAHHAHHAHAHA
         }
         else
         {
            // TODO: save it for letrec*
            SILENT = false;
            expect("(fact 2)", "2",  scm); // HAHAHHAHHAHAHA
            expect("(fact 3)", "6",  scm);
            expect("(fact 4)", "24", scm);
            expect("(fact 5)", "120",scm);
            expect("(fact 6)", "720",scm);
         }
      }

      // control special form: (begin)
      //
      // We need user-code-accesible side-effects to detect that
      // (begin) is really doing the earlier items... though in a
      // pinch we can use (define) for that, but it might confound
      // with other syntactic special cases.
      // 
      expect("(begin)",          "");
      expect("(begin 1)",        "1");
      expect("(begin 1 2 3 4 5)","5");

      // control special form: cond 
      //
      // We need user-code-accesible side-effects to detect that (cond)
      // only evaluates the matching clause.
      expect("(cond)","");
      expect("(cond (#f 2))","");
      expect("(cond (#t 1) (#f 2))","1");
      expect("(cond (#f 1) (#t 2))","2");
      expect("(cond (#t 1) (#t 2))","1");
      expect("(cond (#f 1) (#f 2))","");
      expect("(cond ((equal? 3 4) 1) ((equal? 5 (+ 2 3)) 2))","2");
      expect("(cond (#f) (#t 2))","2");
      expect("(cond (#f) (#t))","#t");
      expect("(cond (#f) (7))","7");
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
         expect("(cond ((equal? 3 4) 1) (else 2))","2");
         expect("else",SEMANTIC); // else is *not* just bound to #t!
      }

      // TODO: let*, letrec
      //
      // Totally not fundamental, even less so than let.  Wait.

      // TODO: control special form: case
      //
      // We need user-code-accesible side-effects to detect that (case)
      // only evaluates the matching clause.
      expect("(case)",SEMANTIC);
      expect("(case 1)",SEMANTIC);
      expect("(case 1 1)",SEMANTIC);
      expect("(case 1 (1))",SEMANTIC);
      expect("(case 1 (1 1))",SEMANTIC);
      expect("(case 1 ((1) 1))","1");
      expect("(case 7 ((2 3) 100) ((4 5) 200) ((6 7) 300))","300");
      expect("(case 7 ((2 3) 100) ((6 7) 200) ((4 5) 300))","200");
      expect("(case 7 ((2 3) 100) ((4 5) 200))",            "");
      expect("(case  (+ 3 4) ((2 3) 100) ((6 7      ) 200))","200");
      expect("(case  (+ 3 4) ((2 3) 100) ((6 (+ 3 4)) 200))","");
      expect("(case '(+ 3 4) ((2 3) 100) ((6 (+ 3 4)) 200))","");
      expect("(case 7 (() 10) ((7) 20))","20"); // empty label ok
      expect("(case 7 ((2 3) 100) ((6 7)))",SEMANTIC);  // bad clause
      expect("(case 7 ((2 3)) ((6 7) 1))",SEMANTIC);    // bad clause
      expect("(case 7 (()) ((7) 20))",SEMANTIC);        // bad clause
      expect("(case 7 (10) ((7) 20))",SEMANTIC);        // bad clause
      expect("(case '7 ((2 3) 100) ((6 '7) 200) ((4 5) 300))","");
      expect("(case \"7\" ((2 3) 100) ((6 \"7\") 200) ((4 5) 300))","");
      expect("(case #t ((2 3) 100) ((6 #t) 200) ((4 5) 300))","200");
      expect("(case #f ((2 3) 100) ((6 #f) 200) ((4 5) 300))","200");
      expect("(case #\\a ((2 3) 100) ((6 #\\a) 200) ((4 5) 300))","200");
      if ( false )
      {
         // See "else" rant among (cond) tests.
         expect("(case 7 ((2 3) 100) ((4 5) 200) (else 300))", "300");
      }
      if ( false )
      {
         // TODO: an error if a case label is duplicated!
         expect("(case 7 ((5 5) 100))",SEMANTIC);
         expect("(case 7 ((5 3) 100) ((4 5) 200))",SEMANTIC);
      }

      // variadic (lambda) and (define), inner defines, etc.
      // 
      expect("(lambda () (+ 1 2) 7)","???");
      expect("((lambda () (+ 1 2) 7))","7");
      expect("((lambda () (display (+ 1 2)) 7))","37");
      {
         // Are the nested-define defined symbols in scope of the
         // "real" body?
         final JhwScm scm = newScm();
         expect("(define (a x) (define b 2) (+ x b))", "",    scm);
         expect("(a 10)",                              "12",  scm);
         expect("a",                                   "???", scm);
         expect("b",SEMANTIC,                                         scm);
      }
      {
         // Can we do more than one?
         final JhwScm scm = newScm();
         expect("(define (f) (define a 1) (define b 2) (+ a b))","",scm);
         expect("(f)","3",scm);
      }
      {
         // Can we do it for an inner helper function?
         final JhwScm scm = newScm();
         final String fact = 
            "(define (fact n)"                                            +
            "  (define (help n a) (if (< n 2) a (help (- n 1) (* n a))))" +
            "  (help n 1))";
         expect(fact,       "",   scm);
         expect("fact",     "???",scm);
         expect("(fact -1)","1",  scm);
         expect("(fact 0)", "1",  scm);
         expect("(fact 1)", "1",  scm);
         expect("(fact 2)", "2",  scm);
         expect("(fact 3)", "6",  scm);
         expect("(fact 4)", "24", scm);
         expect("(fact 5)", "120",scm);
         expect("(fact 6)", "720",scm);
      }
      {
         // Do nested defines really act like (begin)?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f) (define a 1) (display 8) (define b 2) (+ a b))";
         expect(def,"",scm);
         expect("(f)","83",scm);
      }
      {
         // Do nested defines really act like (begin) when we have args?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f b) (define a 1) (display 8) (+ a b))";
         expect(def,"",scm);
         expect("(f 4)","85",scm);
         expect("(f 3)","84",scm);
      }
      {
         // Are nested defines in one another's scope, in any order?
         final JhwScm scm = newScm();
         final String F = 
            "(define (F b) (define a 1) (define (g x) (+ a x)) (g b))";
         final String G = 
            "(define (G b) (define (g x) (+ a x)) (define a 1) (g b))";
         expect(F,"",scm);
         expect("(F 4)","5",scm);
         expect(G,"",scm);
         expect("(G 4)","5",scm);
      }
      {
         // What about defines in lambdas?
         final JhwScm scm = newScm();
         final String def = 
            "((lambda (x) (define a 7) (+ x a)) 5)";
         expect(def,"12",scm);
      }
      {
         // What about closures?
         final JhwScm scm = newScm();
         final String def = 
            "(((lambda (x) (lambda (y) (+ x y))) 10) 7)";
         expect(def,"17",scm);
      }
      {
         // What about closures?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f x) (lambda (y) (+ x y)))";
         expect(def,"",scm);
         expect("(f 10)","???",scm);
         expect("((f 10) 7)","17",scm);
      }
      {
         // What about closures?
         final JhwScm scm = newScm();
         final String def = 
            "(define (f x) (define (h y) (+ x y)) h)";
         expect(def,"",scm);
         expect("(f 10)","???",scm);
         expect("((f 10) 7)","17",scm);
      }

      {
         // variadic stress on the body of let
         expect("(let ((a 1)) (define b 2) (+ a b))","3");
         expect("(let ((a 1)) (display 7) (define b 2) (+ a b))","73");
         expect("(let ((a 1)))",SEMANTIC);
      }

      // check that map works w/ both builtins and user-defineds
      {
         final JhwScm scm = newScm();
         final String def = "(define (f x) (+ x 10))";
         expect("(map display '())",      "()");     
         expect("(map display '(1 2 3))", "123(  )");

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

         expect(def,                      "",           scm);
         expect("f",                      "???",        scm);
         expect("(f 13)",                 "23",         scm);
         expect("(map f '())",            "()",         scm);
         expect("(map f '(1 2 3))",       "(11 12 13)", scm);
      }
      
      // TODO: user-level variadics
      if ( false )
      {
         SILENT = false;
         expect("((lambda x x) 3 4 5 6)",              "(3 4 5 6)");
         expect("((lambda ( . x)) 3 4 5 6)",           "(3 4 5 6)");
         expect("((lambda (x y . z) z) 3 4 5 6)",      "(5 6)");
         expect("(define (f x y . z) z)(foo 3 4 5 6)", "(5 6)");
      }

      // TODO: error for names to collide in formals:
      if ( false )
      {
         SILENT = false;
         expect("(lambda (x x) 1)",SEMANTIC);
         expect("(lambda (x a x) 1)",SEMANTIC);
         expect("(lambda (a x b x) 1)",SEMANTIC);
         expect("(lambda (x a x x) 1)",SEMANTIC);
         expect("(define (f x x) 1)",SEMANTIC);
         expect("(define (f x a x) 1)",SEMANTIC);
         expect("(define (f a x b x) 1)",SEMANTIC);
         expect("(define (f x a x x) 1)",SEMANTIC);
         expect("(let ((x 1) (x 2)) 1)",SEMANTIC);
         expect("(let ((x 1) (x 2)) 1)",SEMANTIC);
         expect("(let ((x 1) (a 10) (x 2)) 1)",SEMANTIC);
         expect("(let ((a 10) (x 1) (b 20) (x 2)) 1)",SEMANTIC);
         expect("(let ((x 1) (a 10) (x 2) (b 20)) 1)",SEMANTIC);
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

      report("global:",JhwScm.global);

      log("numExpects: " + numExpects);
      log("  happy:    " + numHappyExpects);
      log("  unhappy:  " + numUnhappyExpects);
      log("numBatches: " + numBatches);
   }

   /**
    * Expects tests to be an array of pairs of arguments suitable for
    * expect(expr,result,scm).
    *
    * Runs the tests in sequence.
    *
    * If reuse is false, runs each test with an independent JhwScm instance.
    *
    * If reuse is true, runs the tests with an single JhwScm instance.
    *
    * If evaluate is true, uses JhwScm instances initialized to the
    * read-eval-print loop.  
    *
    * If evaluate is false, uses instances initialized to just the
    * read-print loop.
    *
    * Special case: if the type.do_rep is false, and a test predicts a
    * SEMANTIC error (which should only arise from evaluation), we
    * instead declare that the test is expected to succeed with
    * unspecified output value.
    *
    * Special cases can be icky: but the special case here is hoped to
    * avoid a ton of special cases and needless distinctions and
    * boilerplate where the actual tests are specified.
    *
    * YIKES, maybe not try to be so clever.  Sometimes inputs can have
    * both semantic errors (in the first expression) *and* lexical
    * errors (in some subsequent expression).
    *
    * Special case cancelled.  TODO: clean up this comment.
    */
   private static void batch ( final Object[][] tests, final BatchType type )
      throws java.io.IOException
   {
      numBatches++;
      JhwScm scm = null;
      for ( int i = 0; i < tests.length; ++i )
      {
         final Object[] test   =         tests[i];
         final String   expr   = (String)test[0];
         final Object   result =         test[1];
         if ( !type.reuse || null == scm )
         {
            scm = newScm(type.do_rep);
         }
         expect(expr,result,scm);
      }
   }


   /**
    * If result is null, we expect driving to succeed but are
    * indifferent to the output.
    *
    * If result is a String, we expect driving to succeed and the
    * output to match result.
    *
    * If result is an Integer, we expect driving to fail with an error
    * equal to result.
    */
   private static void expect ( final String expr, final Object result )
      throws java.io.IOException
   {
      expect(expr,result,null);
   }

   /**
    * If result is null, we expect driving to succeed but are
    * indifferent to the output.
    *
    * If result is a String, we expect driving to succeed and the
    * output to match result.
    *
    * If result is an Integer, we expect driving to fail with an error
    * equal to result.
    */
   private static void expect ( final String expr,
                                final Object result,
                                JhwScm scm )
      throws java.io.IOException
   {
      numExpects++;
      final int    expected_dcode;
      final String expected_output;
      if ( null == result || result instanceof String )
      {
         numHappyExpects++;
         expected_dcode  = JhwScm.SUCCESS;
         expected_output = (String)result;
      }
      else
      {
         numUnhappyExpects++;
         expected_dcode  = ((Integer)result).intValue();
         expected_output = null;
      }
      //
      // I/O are contracted to never fail, provided their args are
      // valid, regardless of how crazy drive() gets.
      //
      if ( null == scm )
      {
         scm = newScm(true);
      }

      final int icode;
      {
         final byte[] buf = expr.toString().getBytes();
         int off  = 0;
         int code = JhwScm.SUCCESS;
         while ( off < buf.length )
         {
            final int len = buf.length - off;
            final int n   = scm.input(buf,off,len);
            if ( n < 0 )
            {
               code = n;
               break;
            }
            off += n;
         }
         icode = code;
      }

      final int dcode = scm.drive(-1);

      final StringBuilder out = new StringBuilder();
      final int ocode;
      {
         final byte[] buf = new byte[1+debugRand.nextInt(10)];
         int code = 0;
         for ( int off = 0; true; )
         {
            final int n = scm.output(buf,off,buf.length-off);
            if ( -1 > n )
            {
               code = n; // error code
               break;
            }
            if ( -1 == n )
            {
               code = JhwScm.SUCCESS;
               break;
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
         ocode = code;
      }

      assertEquals("input failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   icode);
      assertEquals("output failure on \"" + expr + "\":",
                   JhwScm.SUCCESS,
                   ocode);
      assertEquals("drive failure on \"" + expr + "\":",
                   expected_dcode,
                   dcode);
      if ( null != expected_output )
      {
         assertEquals("result failure on \"" + expr + "\":",
                      expected_output,
                      out.toString());
      }
   }

   private static void log ( final Object msg )
   {
      System.out.println(msg);
   }

   private static void report ( final String tag, final JhwScm.Stats stats )
   {
      if ( !JhwScm.PROFILE || !REPORT ) return;
      log(tag);
      log("  numCycles:        " + stats.numCycles);
      log("  numCons:          " + stats.numCons);
      if ( true )
      {
         log("  numInput:         " + stats.numInput);
         log("  numOutput:        " + stats.numOutput);
      }
      log("  reg.numSet:       " + stats.regStats.numSet);
      log("  reg.numGet:       " + stats.regStats.numGet);
      log("  reg.maxAddr:      " + stats.regStats.maxAddr);
      log("  heap.numSet:      " + stats.heapStats.numSet);
      log("  heap.numGet:      " + stats.heapStats.numGet);
      log("  heap.maxAddr:     " + stats.heapStats.maxAddr);
      if ( JhwScm.USE_CACHED_MEM ) 
      {
         log("  cache.numHits:    " + stats.cacheStats.numHits);
         log("  cache.numMisses:  " + stats.cacheStats.numMisses);
         log("  cache.numFlush:   " + stats.cacheStats.numFlush);
         log("  cache.numDrop:    " + stats.cacheStats.numDrop);
         log("  cache.numLoad:    " + stats.cacheStats.numLoad);
         log("  cacheTop.numSet:  " + stats.cacheTopStats.numSet);
         log("  cacheTop.numGet:  " + stats.cacheTopStats.numGet);
         log("  cacheTop.maxAddr: " + stats.cacheTopStats.maxAddr);
      }

      final int regOps   = stats.regStats.numGet + stats.regStats.numSet;
      final int cacheOps = stats.cacheTopStats.numGet + stats.cacheTopStats.numSet;
      final int heapOps  = stats.heapStats.numGet + stats.heapStats.numSet;

      log("  reg   ops:        " + regOps);
      if ( JhwScm.USE_CACHED_MEM ) 
      {
         log("  cache ops:        " + cacheOps);
      }
      log("  heap  ops:        " + heapOps);

      log("  reg   ops/cell:   " + (1.0 * regOps / stats.regStats.maxAddr));
      if ( JhwScm.USE_CACHED_MEM ) 
      {
         log("  cache ops/cell:   " + (1.0 * cacheOps / stats.cacheTopStats.maxAddr));
      }
      log("  heap  ops/cell:   " + (1.0 * heapOps / stats.heapStats.maxAddr));

      if ( JhwScm.USE_CACHED_MEM ) 
      {
         if ( cacheOps > 0 )
         {
            log("  ops   reg/cache:  " + ( 1.0 * regOps / cacheOps));
         }
         if ( heapOps > 0 )
         {
            log("  ops   cache/heap: " + ( 1.0 * cacheOps / heapOps));
         }
      }
      else
      {
         if ( heapOps > 0 )
         {
            log("  ops   reg/heap:   " + ( 1.0 * regOps / heapOps));
         }
      }

      log("  ALG:                       " + MemCached.ALG);
      log("  LINE_SIZE:                 " + JhwScm.LINE_SIZE);
      log("  LINE_COUNT:                " + JhwScm.LINE_COUNT);
      log("  PROPERLY_TAIL_RECURSIVE:   " + JhwScm.PROPERLY_TAIL_RECURSIVE);
      log("  CLEVER_TAIL_CALL_MOD_CONS: " + JhwScm.CLEVER_TAIL_CALL_MOD_CONS);
      log("  CLEVER_STACK_RECYCLING:    " + JhwScm.CLEVER_STACK_RECYCLING);
      log("  CLEVER_STACK_RECYCLING:    " + JhwScm.CLEVER_STACK_RECYCLING);
      if ( JhwScm.USE_CACHED_MEM ) 
      {
         final int hm = stats.cacheStats.numHits + stats.cacheStats.numMisses;
         log("  cache hit/op:              " + ( 1.0 * stats.cacheStats.numHits / hm));
         log("  cache write/miss:          " + ( 1.0 * stats.cacheStats.numFlush / stats.cacheStats.numMisses));
      }
   }
}
