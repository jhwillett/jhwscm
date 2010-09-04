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
      expectSuccess("(+ 100 2)","102");
      expectSuccess("(+ 100 -2)","98");
      expectSuccess("(* 97 2)","194");
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
         // TODO: the quoted-quote question, and how to print it
         expectSuccess("''9",      "(quote 9)");
         expectSuccess("'''9",     "(quote (quote 9))");
         expectSuccess(" ' ' ' 9 ","(quote (quote 9))");

         // Trouble!
         //
         //   jwillett@little-man ~/jhwscm $ scsh
         //   Welcome to scsh 0.6.7 (R6RS)
         //   Type ,? for help.
         //   > ''1
         //   ''1
         //   > (quote 1)
         //   1
         //   > '1
         //   1
         //   > ' '1
         //   ''1
         //   > (quote '1)
         //   ''1
         //   > 
         //   Exit Scsh? (y/n)? y
         //   jwillett@little-man ~/jhwscm $ guile
         //   guile> ''1
         //   (quote 1)
         //   guile> (quote 1)
         //   1
         //   guile> '1
         //   1
         //   guile> ' '1
         //   (quote 1)
         //   guile> (quote '1)
         //   (quote 1)
         //   guile> 
         //   
         // OK, so scsh makes the decision to print quote as ' instead
         // of as (quote) - no biggie.  But notice the last thing:
         //   
         //   [scsh]> (quote '1)
         //   ''1
         //   
         //   guile> (quote '1)
         //   (quote 1)
         //   
         // Scsh comes back with two levels of quoting, Guile with
         // one.  I'm gonna have to see if this is something clarified
         // by R6RS (noting that Scsh calls R6RS and knowing Guile
         // defaults to around R5RSish), or if it is a bug in one of
         // the two, or if it remains an open design decision.
         //   
         // Damn, had a real problem getting any other Schemes to work
         // in Gentoo.
         //   
         // Aha!  With less weight as evidence perhaps, but I can try
         // the same thing in various non-Scheme LISPs!
         //   
         // From GNU Emacs (duh, it was right there all along!):
         //   
         //   (quote 1)                 
         //   ==> 1
         //   '1                        
         //   ==> 1
         //   (quote (quote 1))
         //   ==> (quote 1)
         //   ''1
         //   ==> (quote 1)
         //   (quote (quote (quote 1)))
         //   ==> (quote (quote 1))
         //   '''1
         //   ==> (quote (quote 1))
         //   (quote '1)
         //   ==> (quote 1)
         //   
         // From GNU CLISP:
         //   
         //   [1]> '1
         //   1
         //   [2]> ''1
         //   '1
         //   [3]> '''1
         //   ''1
         //   [4]> (quote 1)
         //   1
         //   [5]> (quote (quote 1))
         //   '1
         //   [6]> (quote (quote (quote 1)))
         //   ''1
         //   [7]> (quote '1)
         //   '1
         //   
         // OK, so we have:  
         //   
         //   1. Scsh and CLISP print quote in the ' form.
         //   
         //   2. Guile and Emacs print quote in the (quote) form.
         //   
         //   3. Scsh and CLISP print quote in the ' form.
         //   
         //   4. Scsh interprets (quote '1) as two levels of
         //   quotation.
         //   
         //   5. Guile, CLISP, and Emacs, interpret (quote '1) as a
         //   single level of quotation.
         //   
         // No consensus on how to print - fine, I am comfortable
         // making up my own mind on that.
         //
         // For the (quote '1) problem, the vote is leaning toward a
         // single level of quotation.  Mind you, that's how it is
         // printed, not what it *is*.  I think of the print operation
         // in this survey as "stripping off one level of quote".
         // 
         // With that in mind, I'd like to see all ofboth (quote
         // (quote 1)), (quote '1), '(quote 1), and ''1 print the
         // same, as either '1 or (quote 1).
         // 
         // Guile, Emacs, and CLISP all do this.  Scsh is treats ' and
         // quote consistently, but it breaks the "stripping off one
         // level of quote" rule by it printing ''1 for ''1 but 1 for
         // '1.
         // 
         // So I'm going with the striping off one level of quote rule
         // in guiding how print works.  The question is open whether
         // I want to print the long form or the apostrophe form.
         //
         // I am likely to go with apostrophe for the sorterness of
         // it, even though I look to Guile in other matters.  The
         // apostrophe, being a lexical token, can't be redefined the
         // way "quote" can and I don't want redefinitions of quote
         // breaking homoiconicity.
         //
         // Follow-on observation: what happens to apostrophe when you
         // redefine quote? For the following input:
         //
         //   (define quote 1)
         //   '3
         //
         // Both Guile and Scsh fail, saying more or less that I tried
         // to apply 1 as a function to 3, and I can't do that.
         //
         // So... both of them have 'X expand to (quote X) via the
         // *symbol* "quote", not the builtin standard value of
         // "quote".  Interesting.
         //
         // In the LISP-2s I don't know quite what to expect, but
         // whatever happens I do not think it is applicable, since
         // LISP-2's have complex symbols and, if I recall, a
         // different slot in the symbols for each of values,
         // procedures, macros, and special forms (among other
         // things).  From CLISP:
         //
         //   [1]> (defvar quote 1)
         //   QUOTE
         //   [2]> (quote 1)
         //   1
         //   [3]> (defun quote () 1)
         //   
         //   *** - DEFUN/DEFMACRO: QUOTE is a special operator and
         //         may not be redefined.
         //   
         // So I can't redefine "quote", so I can't see what effect
         // that has on apostrophe.
         //   
         // Interesting!  I'm gonna have to mine R5RS and R6RS on this
         // one.
      }

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

      // redefining symbols
      expectSuccess("(define a 1)a(define a 2)a","12");

      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define foo +)","",  scm);
         expectSuccess("(foo 13 18)",   "31",scm);
         expectSemantic("(foo 13 '())",      scm);
         selfTest(scm);
      }

      JhwScm.SILENT = false;

      // defining functions
      expectSuccess("(lambda (a b) (* a b))",       "???");
      expectSuccess("((lambda (a b) (* a b)) 13 5)","65");
      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define (foo a b) (+ a b))","",  scm);
         expectSuccess("(foo 13 18)",               "31",scm);
         expectSemantic("(foo 13 '())",                  scm);
         selfTest(scm);
      }
      
      // nested defines
      {
         final JhwScm scm = new JhwScm();
         expectSuccess("(define (a x) (define b 2) (+ x b))", "",   scm);
         expectSuccess("(a 10)",                              "12", scm);
         expectSuccess("a",                                   "???", scm);
         expectSemantic("b",                                         scm);
         selfTest(scm);
      }

      // ambition: nontrivial user-defined recursive function
      // 
      // This one, the Fibonacci Sequence, is not tail-recursive and
      // will eat O(n) stack space and runs in something awful like
      // O(n*n) or worse - making it also a good stressor for garbage
      // collection.
      // 
      // Fib can be made half-tail-recursive though...
      // 
      // Later, there exists a good memoized dynamic programming
      // version which would be good for comparison.
      //
      {
         final String fib = 
            "(define (fib n) (if (< n 2) n (+ (fib (- n 1)) (fib (- n 2)))))";
         final JhwScm scm = new JhwScm();
         expectSuccess(fib,"???",scm);
         expectSuccess("(fib -1)","-1",scm);
         expectSuccess("(fib 0)","0",scm);
         expectSuccess("(fib 1)","1",scm);
         expectSuccess("(fib 2)","1",scm);
         expectSuccess("(fib 3)","2",scm);
         expectSuccess("(fib 4)","3",scm);
         expectSuccess("(fib 5)","5",scm);
         expectSuccess("(fib 6)","8",scm);
         expectSuccess("(fib 10)","55",scm);
         expectSuccess("(fib 20)","6565",scm);
         selfTest(scm);
      }

      // ambition: nontrivial user-defined recursive function
      // 
      // This one, Factorial, is good for playing w/ tail-recursion.
      // The naive form is not tail recursive and consumes O(n) stack
      // space - but the tail-recursive function is super simple.
      //
      // TODO: demonstrate non-tail-recursive OOMs at a certain scale,
      // but its tail-recursive twin runs just fine to vastly larger
      // scale.
      {
         final String fac1 = 
            "(define (fac1 n) (if (< n 2) 1 (* n (fac1 (- n 1)))))";
         final JhwScm scm = new JhwScm();
         expectSuccess(fac1,"???",scm);
         expectSuccess("(fac1 -1)","1",scm);
         expectSuccess("(fac1 0)","1",scm);
         expectSuccess("(fac1 1)","1",scm);
         expectSuccess("(fac1 2)","2",scm);
         expectSuccess("(fac1 3)","6",scm);
         expectSuccess("(fac1 4)","24",scm);
         expectSuccess("(fac1 5)","120",scm);
         expectSuccess("(fac1 6)","720",scm);
         selfTest(scm);
      }
      {
         final String helper = 
            "(define (helper n a) (if (< n 2) a (helper (- n 1) (* n a))))";
         final String fac2 = 
            "(define (fac2 n) (helper n 1))";
         final JhwScm scm = new JhwScm();
         expectSuccess(helper,"???",scm);
         expectSuccess(fac2,"???",scm);
         expectSuccess("(fac2 -1)","1",scm);
         expectSuccess("(fac2 0)","1",scm);
         expectSuccess("(fac2 1)","1",scm);
         expectSuccess("(fac2 2)","2",scm);
         expectSuccess("(fac2 3)","6",scm);
         expectSuccess("(fac2 4)","24",scm);
         expectSuccess("(fac2 5)","120",scm);
         expectSuccess("(fac2 6)","720",scm);
         selfTest(scm);
      }

      // TODO: test min, max, bounds, 2s-complement nature of fixints?

      // TODO: nested lexical scopes


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
