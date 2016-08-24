package eu.stratosphere.emma
package compiler.lang.core

import api.DataBag
import compiler.BaseCompilerSpec
import compiler.ir.ComprehensionSyntax._
import testschema.Marketing._

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import java.time.Instant

/** A spec for the `LNF.cse` transformation. */
@RunWith(classOf[JUnitRunner])
class PrettyPrintSpec extends BaseCompilerSpec {

  import compiler._
  import Core.{Lang => core}

  val liftPipeline: u.Expr[Any] => u.Tree =
    compiler.pipeline(typeCheck = true)(
      Core.lnf,
      Core.inlineLetExprs
    ) andThen unQualifyStaticModules compose (_.tree)

  val prettyPrint: u.Tree => String =
    tree => time(Core.prettyPrint(tree), "pretty print")

  "Atomics:" - {

    "Lit" in {
      val acts = idPipeline(u.reify(
        42, 42L, 3.14, 3.14F, .1e6, 'c', "string"
      )) collect {
        case act@core.Lit(_) => prettyPrint(act)
      }

      val exps = Seq(
        "42", "42L", "3.14", "3.14F", "100000.0", "'c'", "\"string\""
      )

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }

    "Ref" in {
      val acts = idPipeline(u.reify {
        val x = 1
        val y = 2
        val * = 3
        val `p$^s` = 4
        val ⋈ = 5
        val `foo and bar` = 6
        x * y * `*` * `p$^s` * ⋈ * `foo and bar`
      }) collect {
        case act@core.Ref(_) => prettyPrint(act)
      }

      val exps = Seq(
        "x", "y", "`*`", "`p$^s`", "`⋈`", "`foo and bar`"
      )

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }

    "This" in {

      val acts = idPipeline(u.reify {
        class Unqualified {
          println(this)
        }
        class Qualified {
          println(Qualified.this)
        }
        /*class Outer { FIXME
          println(PrettyPrintSpec.this)
        }*/
        object Module {
          println(this)
        }
      }) collect {
        case u.Apply(_, (act@core.This(_)) :: Nil) =>
          prettyPrint(act)
      }

      val exps = Seq(
        "Unqualified.this", "Qualified.this", /*"PrettyPrintSpec.this" FIXME ,*/ "Module.this"
      )

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }
  }

  "Definitions:" - {

    "DefDef" in {

      val acts = idPipeline(u.reify {
        def fn1(needle: Char, haystack: String): Int = {
          val z = needle.toInt
          haystack indexOf z
        }
        ()
      }) match {
        case u.Block(stats, _) => stats map prettyPrint
      }

      val exps = Seq(
        s"""
           |def fn1(needle: Char, haystack: String): Int = {
           |  val z = needle.toInt
           |  haystack indexOf z
           |}
         """.stripMargin.trim
      )

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }
  }

  "Other:" - {

    "TypeAscr" in {

      val pi = 3.14

      val acts = (idPipeline(u.reify {
        val x = 42: Int // literal
        val y = pi: Double // reference
        val u = "string": CharSequence // upcast
        val v = 42: Long // coercion
        ()
      }) collect {
        case u.Block(stats, _) => stats collect {
          case u.ValDef(_, _, _, rhs) => prettyPrint(rhs)
        }
      }).flatten

      val exps =
        s"""
           |42: Int
           |pi: Double
           |"string": CharSequence
           |42L: Long
         """.stripMargin.trim.split('\n')

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }

    "DefCall" in {

      val n = 42
      val list = List(1, 2, 3)
      implicit val pair = 3.14 -> "pi"
      val c = clicks.fetch().head
      val a = ads.fetch().head

      val acts = (idPipeline(u.reify {
        def summon[A] = implicitly[(Double, String)]
        //@formatter:off
        val x$01 = Predef.println("string")                  // literal
        val x$02 = n - 2                                     // reference in target position
        val x$03 = 2 - n                                     // reference in argument position
        val x$04 = -n                                        // unary operator
        val x$05 = Seq.fill(n)('!')                          // multiple parameter lists
        val x$06 = 3.14.toString                             // 0-arg method
        val x$07 = scala.collection.Set.empty[(String, Int)] // type-args only, with target
        val x$08 = summon[(String, Int)]                     // type-args only, no target
        val x$09 = Predef.implicitly[(Double, String)]       // implicit args
        val x$10 = (c.time, a.`class`)                       // Tuple constructor, keywords
        // this.wait(5)                                      // `this` reference FIXME: does not work
        ()
        //@formatter:on
      }) collect {
        case u.Block(stats, _) => stats.tail collect {
          case u.ValDef(_, _, _, rhs) => prettyPrint(rhs)
        }
      }).flatten

      val exps =
        s"""
           |Predef println "string"
           |n - 2
           |2 - n
           |-n
           |Seq.fill(n)('!')
           |3.14.toString()
           |Set.empty[(String, Int)]
           |summon[(String, Int)]
           |Predef implicitly pair
           |(c.time, a.`class`)
           |this wait 5L
         """.stripMargin.trim.split('\n')

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }

    "Lambda" in {

      val acts = idPipeline(u.reify {
        val fn1 = (needle: Char, haystack: String) => {
          val z = needle.toInt
          haystack indexOf z
        }
        val fn2 = (xs: DataBag[(Int, Int)], fn: ((Int, Int)) => Int) => {
          val ys = xs map fn
          ys
        }
        ()
      }) match {
        case u.Block(stats, _) => for (u.ValDef(_, _, _, rhs) <- stats) yield prettyPrint(rhs)
      }

      val exps = Seq(
        s"""
           |(needle: Char, haystack: String) => {
           |  val z = needle.toInt
           |  haystack indexOf z
           |}
         """,
        s"""
           |(xs: DataBag[(Int, Int)], fn: ((Int, Int)) => Int) => {
           |  val ys = xs map fn
           |  ys
           |}
         """
      ).map(_.stripMargin.trim)

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }

    "Inst" in {

      val services = AdClass.SERVICES

      val acts = idPipeline(u.reify {
        //@formatter:off
        new Ad(1L, "Uber AD", services)                 // args
        new Tuple2(3.14, "pi")                          // type-args and args
        new scala.collection.mutable.ListBuffer[String] // type-args only
        new Object                                      // no-args
        ()
        //@formatter:on
      }) match {
        case u.Block(stats, _) => stats map Pickle.prettyPrint
      }

      val exps =
        s"""
           |new Ad(1L, "Uber AD", services)
           |new Tuple2(3.14, "pi")
           |new ListBuffer[String]()
           |new Object()
         """.stripMargin.trim.split("\n")

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }

    "Branch" in {

      val pi = 3.14

      val acts = idPipeline(u.reify {
        //@formatter:off
        def then$1(x: Int, y: Double) = 2 * x * y
        def else$1(x: Int, y: Double) = 2 * x * y
        if (pi == 3.14) then$1(1, 16.0) else else$1(3, pi)
        ()
        //@formatter:on
      }) match {
        case u.Block(stats, _) => for (branch@core.Branch(_, _, _) <- stats) yield prettyPrint(branch)
      }

      val exps =
        s"""
           |if (pi == 3.14) then$$1(1, 16.0) else else$$1(3, pi)
         """.stripMargin.trim.split("\n")

      (acts zip exps) foreach { case (act, exp) =>
        act shouldEqual exp
      }
    }

    "Let" in {

      val act = prettyPrint(idPipeline(u.reify {
        val x = 15
        val y = {
          val a = 15
          val z = 3
          a - z
        }
        def thn(x1: Int, y1: Int): Int = {
          val r1 = x1 * 7
          r1 * 42
        }
        def els(x2: Int, y2: Int): Int = {
          val r2 = x2 * 2
          r2 * 24
        }
        if (x > 0) thn(x, y) else els(x, y)
      }))

      val exp =
        s"""{
            |  val x = 15
            |  val y = {
            |    val a = 15
            |    val z = 3
            |    a - z
            |  }
            |  def thn(x1: Int, y1: Int): Int = {
            |    val r1 = x1 * 7
            |    r1 * 42
            |  }
            |  def els(x2: Int, y2: Int): Int = {
            |    val r2 = x2 * 2
            |    r2 * 24
            |  }
            |  if (x > 0) thn(x, y) else els(x, y)
            |}
         """.stripMargin.trim

      act shouldEqual exp
    }
  }

  "Comprehensions:" - {

    "with three generators and two interleaved filters" in {

      val act = prettyPrint(liftPipeline(u.reify {
        val clicks$1 = clicks
        val users$1 = users
        val ads$1 = ads

        comprehension[(Instant, AdClass.Value), DataBag] {
          val c = generator(clicks$1)
          val u = generator(users$1)
          guard {
            val id$1 = u.id
            val userID$1 = c.userID
            id$1 == userID$1
          }
          val a = generator(ads$1)
          guard {
            val id$2 = a.id
            val adID$1 = c.adID
            id$2 == adID$1
          }
          head {
            val time$1 = c.time
            val class$1 = a.`class`
            (time$1, class$1)
          }
        }
      }))
      
      val exp =
        s"""
           |{
           |  val clicks$$1 = Marketing.clicks
           |  val users$$1 = Marketing.users
           |  val ads$$1 = Marketing.ads
           |  for {
           |    c <- {
           |      clicks$$1
           |    }
           |    u <- {
           |      users$$1
           |    }
           |    if {
           |      val id$$1 = u.id
           |      val userID$$1 = c.userID
           |      id$$1 == userID$$1
           |    }
           |    a <- {
           |      ads$$1
           |    }
           |    if {
           |      val id$$2 = a.id
           |      val adID$$1 = c.adID
           |      id$$2 == adID$$1
           |    }
           |  } yield {
           |    val time$$1 = c.time
           |    val class$$1 = a.`class`
           |    (time$$1, class$$1)
           |  }
           |}
         """.stripMargin.trim

      act shouldEqual exp
    }
  }
}
