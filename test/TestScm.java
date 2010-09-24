/**
 * Test harness for JhwScm.
 *
 * @author Jesse H. Willett
 * @copyright (c) 2010 Jesse H. Willett
 * All rights reserved.
 */

import java.util.Random;

public class TestScm extends Util
{
   private static final boolean verbose = false;

   private static final Random debugRand = new Random(1234);

   private static final int LEXICAL  = Firmware.ERROR_FAILURE_LEXICAL;
   private static final int SEMANTIC = Firmware.ERROR_FAILURE_SEMANTIC;
   private static final int BLOCKED  = Firmware.ERROR_BLOCKED;

   private static class Batch
   {
      final boolean reuse;
      final boolean do_rep;
      final int     sizeIn;
      final int     sizeOut;

      Batch ( final boolean reuse, 
              final boolean do_rep,
              final int     sizeIn,
              final int     sizeOut )
      {
         this.reuse   = reuse;
         this.do_rep  = do_rep;
         this.sizeIn  = sizeIn;
         this.sizeOut = sizeOut;
      }
   }
   private static final Batch REP_DEP    = new Batch(true,  true,  1024, 1024);
   private static final Batch REP_IND    = new Batch(false, true,  1024, 1024);
   private static final Batch RE_DEP     = new Batch(true,  false, 1024, 1024);
   private static final Batch RE_IND     = new Batch(false, false, 1024, 1024);
   private static final Batch STRESS_IN  = new Batch(true,  true,     1, 1024);
   private static final Batch STRESS_OUT = new Batch(true,  true,  1024,    1);
   private static final Batch STRESS_IO  = new Batch(true,  true,     1,    1);

   private static boolean REPORT  = true;
   private static boolean PROFILE = true;
   private static boolean VERBOSE = false;
   private static boolean DEBUG   = true;

   private static int numVMs            = 0;
   private static int numScmNoEvals     = 0;
   private static int numScmFulls       = 0;
   private static int numMetaBatches    = 0;
   private static int numBatches        = 0;
   private static int numExpects        = 0;
   private static int numHappyExpects   = 0;
   private static int numUnhappyExpects = 0;

   private static Computer scmNoEval ()
   {
      numScmNoEvals++;
      return newScm(RE_DEP);
   }

   private static Computer scmFull ()
   {
      numScmFulls++;
      return newScm(REP_DEP);
   }

   private static Computer newScm ( final Batch batch )
   {
      numVMs++;
      final Machine  mach = new Machine(PROFILE,
                                        VERBOSE,
                                        true,
                                        batch.sizeIn,
                                        batch.sizeOut);
      final JhwScm   firm = new JhwScm(batch.do_rep,
                                       PROFILE,
                                       VERBOSE,
                                       DEBUG);
      final Computer comp = new Computer(mach,
                                         firm,
                                         PROFILE,
                                         VERBOSE,
                                         DEBUG);
      return comp;
   }

   public static void main ( final String[] argv )
   {
      expect("","");

      // first content: simple integer expressions are self-evaluating
      // and self-printing.
      final String[] simpleInts = { 
         "0", "1", "97", "1234", "-1", "-4321", "10", "1001", 
      };
      for ( final String expr : simpleInts )
      {
         final Object[][] tests = { 
            { expr,                   expr },
            { " " + expr,             expr },
            { expr + " ",             expr },
            { " " + expr + " ",       expr },
            { "\n" + expr,            expr },
            { expr + "\n",            expr },
            { "\t" + expr + "\t\r\n", expr },
         };
         final Batch[] batches = { 
            RE_IND,
            RE_DEP,
            REP_IND,
            REP_DEP,
            STRESS_OUT,
         };
         metabatch(tests,batches);
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
            { pair[0] + " ",       pair[1] },
            { " " + pair[0] + " ", pair[1] },
         };
         final Batch[] batches = { 
            RE_IND,
            RE_DEP,
            REP_IND,
            REP_DEP,
         };
         metabatch(tests,batches);
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
         final Batch[] batches = { 
            RE_IND,
            RE_DEP,
            REP_IND,
            REP_DEP,
         };
         metabatch(tests,batches);
      }

      // variables are self-reading and self-printing, but unbound
      // variables fail to evaluate
      {
         final Object[][] tests = { 
            { "a",       "a"       },
            { "a1",      "a1"      },
            { "a_0-b.c", "a_0-b.c" },
         };
         final Batch[] batches = { 
            RE_IND,
            RE_DEP,
         };
         metabatch(tests,batches);
      }      
      {
         final Object[][] tests = { 
            { "a",  SEMANTIC },
            { "a1", SEMANTIC },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
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
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }  

      expect("-",    "-",    scmNoEval());
      expect("-asd", "-asd", scmNoEval());
      expect("-",    null);
      expect("-as",  SEMANTIC);
      
      {
         final Object[][] tests = { 
            { "(a b c)",          SEMANTIC },
            { "(a (b c))",        SEMANTIC },
            { "((a b) c)",        SEMANTIC },
            { "((a b c))",        SEMANTIC },
            { "((a b) c",          LEXICAL },
            { "((a b c)",          LEXICAL },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
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
         final Batch[] batches = { 
            RE_IND,
         };
         metabatch(tests,batches);
      }  

      // improper list experssions: yay!
      expect("(1 . 2)",      "(1 . 2)",   scmNoEval());
      expect("(1 2 . 3)",    "(1 2 . 3)", scmNoEval());
      expect("(1 . 2 3)",    LEXICAL,     scmNoEval());
      expect("( . 2 3)",     LEXICAL,     scmNoEval());
      expect("(1 . )",       LEXICAL,     scmNoEval());
      expect("(1 .)",        LEXICAL,     scmNoEval());
      expect("(1 . 2 3)",    LEXICAL);
      expect("( . 2 3)",     LEXICAL);
      expect("(1 . )",       LEXICAL);
      expect("(1 .)",        LEXICAL);

      expect("(1 . ())",     "(1)",       scmNoEval());
      expect("(1 .())",      "(1)",       scmNoEval());

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
      expect("( . 2 )",    "2",           scmNoEval());
      expect("( . 2 )",    "2",           scmFull());
      expect("( . () )",   "()",          scmNoEval());
      expect("( . 2 3 )",LEXICAL);
      expect("(. abc )",   "abc",         scmNoEval());

      if ( false )
      {
         // Probably not until I handle floats!
         expect("(1 .2)",       "(1 0.2)",   scmNoEval());
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
         expect(expr + " ",             expr);
         expect(" " + expr + " ",       expr);
         expect("\n" + expr,            expr);
         expect(expr + "\n",            expr);
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
         expect(pair[0] + " ",       pair[1]);
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
         expect(expr + " ",             expr);
         expect(" " + expr + " ",       expr);
         expect("\n" + expr,            expr);
         expect(expr + "\n",            expr);
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
      {
         final Object[][] tests = { 
            { "(())",         SEMANTIC },
            { "(1)",          SEMANTIC },
            { "(\"a\")",      SEMANTIC },
            { "(#\\a)",       SEMANTIC },
            { "(() 0)",       SEMANTIC },
            { "(1 0)",        SEMANTIC },
            { "(\"a\" 0)",    SEMANTIC },
            { "(#\\a 0)",     SEMANTIC },
            { "(+ 0 0)",       "0"     },
            { "(+ 0 1)",       "1"     },
            { "(+ 0 2)",       "2"     },
            { "(+ 2 0)",       "2"     },
            { "(+ 2 3)",       "5"     },
            { "(+ 2 -3)",     "-1"     },
            { "(+ -2 -3)",     "-5"    },
            { "(- 2  3)",     "-1"     },
            { "(- 2 -3)",     "5"      },
            { "(- -3 2)",     "-5"     },
            { "(+ 100 2)",    "102"    },
            { "(+ 100 -2)",   "98"     },
            { "(* 97 2)",     "194"    },
            { "(* -97 2)",    "-194"   },
            { "(* -97 -2)",   "194"    },
            { "(* 97 -2)",    "-194"   },
            { "(+ a b)",      SEMANTIC },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }


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
      {
         final Object[][] tests = { 
            { "(+0)",                         "0"      },
            { "(+1 10)",                      "10"     },
            { "(+3 3 5 7)",                   "15"     },
            { "(+0 1)",                       SEMANTIC },
            { "(+1)",                         SEMANTIC },
            { "(+1 1 2)",                     SEMANTIC },
            { "(+3)",                         SEMANTIC },
            { "(+3 1 2)",                     SEMANTIC },
            { "(+3 1 2 3 4)",                 SEMANTIC },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }

      // simple special form
      {
         final Object[][] tests = { 
            { "(quote ())",                   "()"                },
            { "(quote (1 2))",                "(1 2)"             },
            { "(quote (a b))",                "(a b)"             },
            { "(+ 1 (quote ()))",             SEMANTIC            },
            { "(quote 9)",                    "9"                 },
            { "(quote (quote 9))",            "(quote 9)"         },
            { "(quote (quote (quote 9)))",    "(quote (quote 9))" },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }

      // simple quote sugar
      {
         final Object[][] tests = { 
            { "'()",                          "()"     },
            { "'(1 2)",                       "(1 2)"  },
            { "'(a b)",                       "(a b)"  },
            { "(+ 1 '())",                    SEMANTIC },
            { "'9",                           "9"      },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }
      if ( false )
      {
         // See the quote-quote-quote discussion in DIARY.txt.
         //
         // TODO: the quoted-quote question, and how to print it
         expect("''9",      "(quote 9)");
         expect("'''9",     "(quote (quote 9))");
         expect(" ' ' ' 9 ","(quote (quote 9))");
      }

      {
         final Object[][] tests = { 
            { "(equal? 10  10)",              "#t"     },
            { "(equal? 11  10)",              "#f"     },
            { "(equal? 'a  'a)",              "#t"     },
            { "(equal? 'a  'b)",              "#f"     },
            { "(equal? 10  'b)",              "#f"     },
            { "(<       9  10)",              "#t"     },
            { "(<      10   9)",              "#f"     },
            { "(<      10  10)",              "#f"     },
            { "(<     -10   0)",              "#t"     },
            { "(<      -1  10)",              "#t"     },
            { "(<       0 -10)",              "#f"     },
            { "(< 10 'a)",                    SEMANTIC },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }

      // simple conditionals: in Scheme, only #f is false
      // 
      // TODO: test w/ side effects that short-cut semantics work.
      //
      // TODO: when you do mutators, be sure to check that only one
      // alternative in an (if) is executed.
      //
      {
         final Object[][] tests = { 
            { "(if #f 2 5)",                  "5"      },
            { "(if #t 2 5)",                  "2"      },
            { "(if '() 2 5)",                 "2"      },
            { "(if 0 2 5)",                   "2"      },
            { "(if 0 (+ 2 1) 5)",             "3"      },
            { "(if 0 2 5)",                   "2"      },
            { "(if #t (+ 2 1) (+ 4 5))",      "3"      },
            { "(if #f (+ 2 1) (+ 4 5))",      "9"      },
            { "(if (equal? 1 1) 123 321)",    "123"    },
            { "(if (equal? 2 1) 123 321)",    "321"    },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }

      // cons, car, cdr, and list have a particular relationship
      //
      {
         final Object[][] tests = { 
            { "(cons 1 2)",                   "(1 . 2)" },
            { "(car (cons 1 2))",             "1"       },
            { "(cdr (cons 1 2))",             "2"       },
            { "(list)",                       "()"      },
            { "(list 1 2)",                   "(1 2)"   },
            { "(car 1)",                      SEMANTIC  },
            { "(car '())",                    SEMANTIC  },
            { "(cdr 1)",                      SEMANTIC  },
            { "(cdr '())",                    SEMANTIC  },
            { "(cons 1 '())",                 "(1)"     },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }

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
      {
         final Object[][] tests = { 
            { "(display 5)",                  "5"        },
            { "(display 5)2",                 "52"       },
            { "(display (+ 3 4))(+ 1 2)",     "73"       },
            { "(display '(+ 3 4))(+ 1 2)",    "(+ 3 4)3" },
            { "(read)5",                      "5"        },
            { "(read)(+ 1 2)",                "(+ 1 2)"  },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }
      
      // defining symbols
      {
         final Object[][] tests = { 
            { "(define a 100)",           ""          },
            { "a",                        "100"       },
            { "(define a 100)",           ""          },
            { "(define b   2)",           ""          },
            { "(+ a b)",                  "102"       },
            { "(+ a c)",                  SEMANTIC    },
         };
         final Batch[] batches = { 
            REP_DEP,
         };
         metabatch(tests,batches);
      }
      expect("(define a)",SEMANTIC);

      // redefining symbols
      expect("(define a 1)a(define a 2)a","12");

      {
         final Object[][] tests = { 
            { "(define foo +)",           ""          },
            { "(foo 13 18)",              "31"        },
            { "(foo 13 '())",             SEMANTIC    },
         };
         final Batch[] batches = { 
            REP_DEP,
         };
         metabatch(tests,batches);
      }

      // defining functions
      {
         final Object[][] tests = { 
            { "(lambda)",                     SEMANTIC },
            { "(lambda ())",                  SEMANTIC },
            { "(lambda () 1)",                "???"    },
            { "((lambda () 1))",              "1"      },
            { "((lambda () 1) 10)",           SEMANTIC },
            { "(lambda (a) 1)",               "???"    },
            { "((lambda (a) 1) 10)",          "1"      },
            { "((lambda (a) 1))",             SEMANTIC },
            { "((lambda (a) 1) 10 20)",       SEMANTIC },
            { "(lambda (a b) (* a b))",       "???"    },
            { "((lambda (a) (* 3 a)) 13)",    "39"     },
            { "((lambda (a b) (* a b)) 13 5)","65"     },
         };
         final Batch[] batches = { 
            REP_IND,
         };
         metabatch(tests,batches);
      }
      {
         final Object[][] tests = { 
            { "(define (foo a b) (+ a b))", ""          },
            { "(foo 13 18)",                "31"        },
            { "(foo 13 '())",               SEMANTIC    },
         };
         final Batch[] batches = { 
            REP_DEP,
         };
         metabatch(tests,batches);
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
         final String fact = 
            "(define (fact n) (if (< n 2) 1 (* n (fact (- n 1)))))";
         final Object[][] tests = { 
            { fact,                       ""          },
            { "fact",                     "???"       },
            { "(fact -1)",                "1"         },
            { "(fact 0)",                 "1"         },
            { "(fact 1)",                 "1"         },
            { "(fact 2)",                 "2"         },
            { "(fact 3)",                 "6"         },
            { "(fact 4)",                 "24"        },
            { "(fact 5)",                 "120"       },
            { "(fact 6)",                 "720"       },
            { "(fact 10)",                "3628800"   },
         };
         final Batch[] batches = { 
            REP_DEP,
         };
         metabatch(tests,batches);
      }
      {
         final String help = 
            "(define (help n a) (if (< n 2) a (help (- n 1) (* n a))))";
         final String fact = 
            "(define (fact n) (help n 1))";
         final Object[][] tests = { 
            { fact,                       ""          },
            { help,                       ""          },// note, help 2nd ;)
            { "fact",                     "???"       },
            { "help",                     "???"       },
            { "(fact -1)",                "1"         },
            { "(fact 0)",                 "1"         },
            { "(fact 1)",                 "1"         },
            { "(fact 2)",                 "2"         },
            { "(fact 3)",                 "6"         },
            { "(fact 4)",                 "24"        },
            { "(fact 5)",                 "120"       },
            { "(fact 6)",                 "720"       },
            { "(fact 10)",                "3628800"   },
         };
         final Batch[] batches = { 
            REP_DEP,
         };
         metabatch(tests,batches);
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
         final Computer scm = scmFull();
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
         report("fib:",scm);
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
      {
         final Object[][] tests = { 
            { "268435455",                    "-1" },
            { "268435456",                    "0"  },
            { "268435457",                    "1"  },
            { "(+ 268435455 0)",              "-1" },
            { "(+ 268435456 0)",              "0"  },
            { "(+ 268435456 1)",              "1"  },
            { "(+ 268435457 1)",              "2"  },
            { "(+ 134217728 134217727)",      "-1" },
            { "(- 0 268435456)",              "0"  },
            { "(- 0 268435455)",              "1"  },
            { "(equal? 0 268435456)",         "#t" },
            { "(equal? 0 (* 100 268435456))", "#t" },
         };
         final Batch[] batches = { 
            REP_DEP,
         };
         metabatch(tests,batches);
      }

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
         final Computer scm = scmFull();
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
         final Computer scm = scmFull();
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
         expect("(cond ((equal? 3 4) 1) (else 2))","2");
         expect("else",SEMANTIC); // else is *not* just bound to #t!
      }

      // TODO: let*, letrec
      //
      // Totally not fundamental, even less so than let.  Wait.

      // TODO: control special form: case
      //
      // We need user-code-accesible side-effects to detect that
      // (case) only evaluates the matching clause.
      //
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
         final Computer scm = scmFull();
         expect("(define (a x) (define b 2) (+ x b))", "",    scm);
         expect("(a 10)",                              "12",  scm);
         expect("a",                                   "???", scm);
         expect("b",SEMANTIC,                                         scm);
      }
      {
         // Can we do more than one?
         final Computer scm = scmFull();
         expect("(define (f) (define a 1) (define b 2) (+ a b))","",scm);
         expect("(f)","3",scm);
      }
      {
         // Can we do it for an inner helper function?
         final Computer scm = scmFull();
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
         final Computer scm = scmFull();
         final String def = 
            "(define (f) (define a 1) (display 8) (define b 2) (+ a b))";
         expect(def,"",scm);
         expect("(f)","83",scm);
      }
      {
         // Do nested defines really act like (begin) when we have args?
         final Computer scm = scmFull();
         final String def = 
            "(define (f b) (define a 1) (display 8) (+ a b))";
         expect(def,"",scm);
         expect("(f 4)","85",scm);
         expect("(f 3)","84",scm);
      }
      {
         // Are nested defines in one another's scope, in any order?
         final Computer scm = scmFull();
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
         final Computer scm = scmFull();
         final String def = 
            "((lambda (x) (define a 7) (+ x a)) 5)";
         expect(def,"12",scm);
      }
      {
         // What about closures?
         final Computer scm = scmFull();
         final String def = 
            "(((lambda (x) (lambda (y) (+ x y))) 10) 7)";
         expect(def,"17",scm);
      }
      {
         // What about closures?
         final Computer scm = scmFull();
         final String def = 
            "(define (f x) (lambda (y) (+ x y)))";
         expect(def,"",scm);
         expect("(f 10)","???",scm);
         expect("((f 10) 7)","17",scm);
      }
      {
         // What about closures?
         final Computer scm = scmFull();
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
         final Computer scm = scmFull();
         final String def = "(define (f x) (+ x 10))";
         expect("(map1 display '())",      "()");     
         expect("(map1 display '(1 2 3))", "123(  )");

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
         expect("(map1 f '())",            "()",         scm);
         expect("(map1 f '(1 2 3))",       "(11 12 13)", scm);
      }
      
      // TODO: user-level variadics
      if ( false )
      {
         expect("((lambda x x) 3 4 5 6)",              "(3 4 5 6)");
         expect("((lambda ( . x)) 3 4 5 6)",           "(3 4 5 6)");
         expect("((lambda (x y . z) z) 3 4 5 6)",      "(5 6)");
         expect("(define (f x y . z) z)(foo 3 4 5 6)", "(5 6)");
      }

      // TODO: error for names to collide in formals:
      if ( false )
      {
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

      reportGlobal();

      log("numExpects:     " + numExpects);
      log("  happy:        " + numHappyExpects);
      log("  unhappy:      " + numUnhappyExpects);
      log("numBatches:     " + numBatches);
      log("numMetaBatches: " + numMetaBatches);
      log("numVMs:         " + numVMs);
      log("numScmNoEvals:  " + numScmNoEvals);
      log("numScmFulls:    " + numScmFulls);
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
    * I am considering a special case: if the type.do_rep is false,
    * and a test predicts a SEMANTIC error (which should only arise
    * from evaluation), we instead declare that the test is expected
    * to succeed with unspecified output value.
    *
    * Special cases can be icky: but the special case here is hoped to
    * avoid a ton of special cases and needless distinctions and
    * boilerplate where the actual tests are specified.
    *
    * I tried it, but YIKES!  Maybe not try to be so clever.
    * Sometimes inputs can have both semantic errors (in the first
    * expression) *and* lexical errors (in some subsequent
    * expression).
    *
    * Special case cancelled.
    */
   private static void batch ( final Object[][] tests, final Batch type )
   {
      final boolean oldVerbose = VERBOSE;
      if ( STRESS_IO == type || STRESS_IN == type/* || STRESS_OUT == type*/ )
      {
         VERBOSE = true;
      }
      numBatches++;
      Computer scm = null;
      for ( int i = 0; i < tests.length; ++i )
      {
         final Object[] test   =         tests[i];
         final String   expr   = (String)test[0];
         final Object   result =         test[1];
         if ( !type.reuse || null == scm )
         {
            scm = newScm(type);
         }
         expect(expr,result,scm);
      }
      VERBOSE = oldVerbose;
   }

   private static void metabatch ( final Object[][] tests, final Batch[] types )
   {
      numMetaBatches++;
      for ( int i = 0; i < types.length; ++i )
      {
         batch(tests,types[i]);
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
                                Computer     scm )
   {
      numExpects++;
      final int    expected_dcode;
      final String expected_output;
      if ( null == result || result instanceof String )
      {
         numHappyExpects++;
         expected_dcode  = Firmware.ERROR_COMPLETE;
         expected_output = (String)result;
      }
      else
      {
         numUnhappyExpects++;
         expected_dcode  = ((Integer)result).intValue();
         expected_output = null;
      }

      if ( null == scm )
      {
         scm = scmFull();
      }
      final Machine  machine = scm.machine;
      final IOBuffer bufIn   = machine.ioBuf(0);
      final IOBuffer bufOut  = machine.ioBuf(1);

      {
         final byte[] input_buf = expr.toString().getBytes();
         int input_off = 0;
         while ( input_off < input_buf.length )
         {
            final int input_len = input_buf.length - input_off;
            final int code = bufIn.input(input_buf, input_off, input_len);
            if ( 0 <= code )
            {
               input_off += code;
            }
            else
            {
               throw new RuntimeException("input() out of spec: " + code);
            }
         }
      }

      // I/O are contracted to never fail, provided their args are
      // valid, regardless of how crazy drive() gets.
      //
      // So we don't check dcode until after slurping output().
      //
      final StringBuilder out = new StringBuilder();
      int dcode = Firmware.ERROR_INCOMPLETE;
      do
      {
         dcode = scm.drive(debugRand.nextInt(10));

         final byte[] output_buf = new byte[1+debugRand.nextInt(10)];
         int output_off = 0;
         for ( int off = 0; true; )
         {
            final int output_len = output_buf.length - output_off;
            final int num = bufOut.output(output_buf, output_off, output_len);
            if ( 0 > num )
            {
               throw new RuntimeException("output() out of spec: " + num);
            }
            if ( 0 == num )
            {
               break;
            }
            for ( int i = output_off; i < output_off + num; ++i )
            {
               out.append((char)output_buf[i]);
            }
            output_off += num;
            if ( output_off >= output_buf.length )
            {
               output_off = 0;
            }
         }
      }
      while ( Firmware.ERROR_INCOMPLETE == dcode ||
              Firmware.ERROR_BLOCKED    == dcode || 
              !bufOut.isEmpty() );

      if ( VERBOSE )
      {
         log("dcode:            " + dcode);
         log("bufIn.isEmpty():  " + bufIn.isEmpty());
         log("bufOut.isEmpty(): " + bufOut.isEmpty());
      }

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

   private static void reportGlobal ()
   {
      report("global:",
             Computer.global,
             JhwScm.global,
             Machine.global,
             Machine.global.ioStats);
   }

   private static void report ( final String tag, final Computer scm )
   {
      report(tag,
             scm.local,
             ((JhwScm)scm.firmware).local,
             scm.machine.local,
             scm.machine.local.ioStats);
   }

   private static void report ( final String           tag, 
                                final Computer.Stats   cs,
                                final JhwScm.Stats     ss,
                                final Machine.Stats    ms,
                                final IOBuffer.Stats[] is )
   {
      if ( !PROFILE || !REPORT ) return;
      log(tag);
      log("  numCycles:        " + cs.numCycles);
      log("  numCons:          " + ss.numCons);
      if ( true )
      {
         for ( int i = 0; i < is.length; ++i )
         {
            log("  iobuf:            " + i);
            final IOBuffer.Stats stats = is[i];
            log("    numInput:       " + stats.numInput);
            log("    numOutput:      " + stats.numOutput);
            log("    numPeek:        " + stats.numPeek);
            log("    numPop:         " + stats.numPop);
            log("    numPush:        " + stats.numPush);
         }
      }
      log("  reg.numSet:       " + ms.regStats.numSet);
      log("  reg.numGet:       " + ms.regStats.numGet);
      log("  reg.maxAddr:      " + ms.regStats.maxAddr);
      log("  heap.numSet:      " + ms.heapStats.numSet);
      log("  heap.numGet:      " + ms.heapStats.numGet);
      log("  heap.maxAddr:     " + ms.heapStats.maxAddr);
      if ( Machine.USE_CACHED_MEM ) 
      {
         log("  cache.numHits:    " + ms.cacheStats.numHits);
         log("  cache.numMisses:  " + ms.cacheStats.numMisses);
         log("  cache.numFlush:   " + ms.cacheStats.numFlush);
         log("  cache.numDrop:    " + ms.cacheStats.numDrop);
         log("  cache.numLoad:    " + ms.cacheStats.numLoad);
         log("  cacheTop.numSet:  " + ms.cacheTopStats.numSet);
         log("  cacheTop.numGet:  " + ms.cacheTopStats.numGet);
         log("  cacheTop.maxAddr: " + ms.cacheTopStats.maxAddr);
      }

      final int regOps   = ms.regStats.numGet + ms.regStats.numSet;
      final int cacheOps = ms.cacheTopStats.numGet + ms.cacheTopStats.numSet;
      final int heapOps  = ms.heapStats.numGet + ms.heapStats.numSet;

      log("  reg   ops:        " + regOps);
      if ( Machine.USE_CACHED_MEM ) 
      {
         log("  cache ops:        " + cacheOps);
      }
      log("  heap  ops:        " + heapOps);

      log("  reg   ops/cell:   " + (1.0 * regOps / ms.regStats.maxAddr));
      if ( Machine.USE_CACHED_MEM ) 
      {
         log("  cache ops/cell:   " + (1.0 * cacheOps / ms.cacheTopStats.maxAddr));
      }
      log("  heap  ops/cell:   " + (1.0 * heapOps / ms.heapStats.maxAddr));

      if ( Machine.USE_CACHED_MEM ) 
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
      log("  LINE_SIZE:                 " + Machine.LINE_SIZE);
      log("  LINE_COUNT:                " + Machine.LINE_COUNT);
      log("  PROPERLY_TAIL_RECURSIVE:   " + JhwScm.PROPERLY_TAIL_RECURSIVE);
      log("  CLEVER_TAIL_CALL_MOD_CONS: " + JhwScm.CLEVER_TAIL_CALL_MOD_CONS);
      log("  CLEVER_STACK_RECYCLING:    " + JhwScm.CLEVER_STACK_RECYCLING);
      log("  CLEVER_STACK_RECYCLING:    " + JhwScm.CLEVER_STACK_RECYCLING);
      if ( Machine.USE_CACHED_MEM ) 
      {
         final int hm = ms.cacheStats.numHits + ms.cacheStats.numMisses;
         log("  cache hit/op:              " + 
             ( 1.0 * ms.cacheStats.numHits / hm));
         log("  cache write/miss:          " + 
             ( 1.0 * ms.cacheStats.numFlush / ms.cacheStats.numMisses));
      }
   }
}
