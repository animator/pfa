package test.scala.makedocs

import scala.language.postfixOps
import scala.collection.mutable

import org.junit.runner.RunWith

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import org.scoringengine.pfa.signature.Sig
import org.scoringengine.pfa.signature.Sigs
import org.scoringengine.pfa.signature.toLaTeX
import org.scoringengine.pfa.lib1
import test.scala._

@RunWith(classOf[JUnitRunner])
class MakeDocsSuite extends FlatSpec with Matchers {
  val libfcn =
    lib1.array.provides ++
    lib1.bytes.provides ++
    lib1.core.provides ++
    lib1.enum.provides ++
    lib1.fixed.provides ++
    lib1.impute.provides ++
    lib1.map.provides ++
    lib1.math.provides ++
    lib1.record.provides ++
    lib1.string.provides ++
    lib1.stat.sample.provides ++
    lib1.model.tree.provides

  def xmlToLaTeX(node: scala.xml.Node): String = node.child.map(x => x.label match {
    case "#PCDATA" => x.text
    case "#ENTITY" => x.text
    case "p" => s"{\\PFAp ${x.text}}"
    case "c" => s"{\\PFAc ${x.text}}"
    case "t" => s"{\\PFAt ${x.text}}"
    case "tp" => s"{\\PFAtp ${x.text}}"
    case "pf" => s"{\\PFApf ${x.text}}"
    case "m" => s"$$${x.text}$$"
    case "f" => s"{\\PFAf \\hyperlink{${x.text}}${x.text}}"
    case "paramField" => ""
    case y => throw new Exception(s"Match error node = $node x = $x x.label = ${x.label} x.text = ${x.text}")
  }).mkString("").replace("%", "\\%").replace("\n", " ").replaceAll("\"([^\"]*)\"", "``$1''")

  "LaTeX generator" must "generate LaTeX" taggedAs(MakeDocsLatex) in {
    val outputFile = new java.io.PrintWriter(new java.io.File("doc/spec/libfcns.tex"))

    outputFile.println("\\" + """usepackage{xstring}

\newcommand{\libfcn}[1]{%
    \par\noindent%
    \IfEqCase*{#1}{%""")

    for ((n, f) <- libfcn) {
      val sanitized = f.name.replace("%", "\\%").replace("&", "\\&").replace("^", "\\^{}")

      val asname = sanitized.replace("~", "TILDE")
      val quoted = "\"" + sanitized.replace("~", "\\textasciitilde{}") + "\""

      outputFile.print(s"    {$asname}{\\hypertarget{$asname}{\\noindent \\mbox{\\hspace{0.015\\linewidth}} {\\bf Signature:} ")

      var where = Seq[String]()

      f.sig match {
        case Sig(params, ret) => {
          val names = params map {case (n, p) => n} mkString(", ")
          outputFile.print(s"\\mbox{\\PFAc \\{$quoted:$$\\!$$ [$names]\\} \\vspace{0.2 cm} \\\\")

          val alreadyLabeled = mutable.Set[String]()
          for ((n, p) <- params) {
            val pp = toLaTeX(p, alreadyLabeled)
            where = where :+ s" & \\PFAc $n \\rm & $pp \\\\"
          }
          val rr = toLaTeX(ret, alreadyLabeled)
          where = where :+ s" & {\\it (returns)} & $rr \\\\"
        }
        case Sigs(sigs) => {
          outputFile.print(s"\\mbox{\\PFAc")

          val possibilities =
            for (Sig(params, ret) <- sigs) yield {
              val names = params map {case (n, p) => n} mkString(", ")

              s"\\{$quoted:$$\\!$$ [$names]\\}"
            }

          outputFile.print(possibilities.distinct.mkString(" \\rm or \\PFAc "))

          val newwhere =
            (for (Sig(params, ret) <- sigs) yield {
              val alreadyLabeled = mutable.Set[String]()
              var out =
                (for ((n, p) <- params) yield {
                  val pp = toLaTeX(p, alreadyLabeled)
                  s" & \\PFAc $n \\rm & $pp \\\\"
                }).mkString(" ")
              val rr = toLaTeX(ret, alreadyLabeled)
              out = out + s" & {\\it (returns)} & $rr \\\\"
              out
            }).mkString(" \\end{tabular} \\vspace{0.2 cm} \\\\ or \\vspace{0.2 cm} \\\\ \\begin{tabular}{p{0.01\\linewidth} l p{0.8\\linewidth}}")
          where = where :+ newwhere
        }
      }

      outputFile.print("} \\vspace{0.2 cm} \\\\ ")

      if (!where.isEmpty) {
        outputFile.print("\\rm \\begin{tabular}{p{0.01\\linewidth} l p{0.8\\linewidth}}" + where.mkString(" ") + " \\end{tabular} \\vspace{0.3 cm} \\\\ ")
      }

      val desc = xmlToLaTeX(f.doc \ "desc" head)
      outputFile.print(s"\\mbox{\\hspace{0.015\\linewidth}} {\\bf Description:} $desc \\vspace{0.2 cm} \\\\ ")

      val params = f.doc \ "param"
      val rets = f.doc \ "ret"
      if (!params.isEmpty || !rets.isEmpty) {
        outputFile.print(s"\\mbox{\\hspace{0.015\\linewidth}} {\\bf Parameters:} \\vspace{0.2 cm} \\\\ \\begin{tabular}{p{0.01\\linewidth} l p{0.8\\linewidth}} ")

        for (param <- params) {
          val name = param \ "@name"

          var fields = ""
          val paramFields = param \ "paramField"
          if (!paramFields.isEmpty) {
            fields = fields + "\\begin{description*}"
            for (field <- paramFields) {
              val fieldName = field \ "@name"
              fields = fields + s"\\item[\\PFAc $fieldName:] ${xmlToLaTeX(field)} "
            }
            fields = fields + "\\end{description*}"
          }

          outputFile.print(s" & \\PFAc $name \\rm & ${xmlToLaTeX(param)} $fields \\\\ ")
        }

        for (ret <- rets) {
          outputFile.print(s" & {\\it (return value)} \\rm & ${xmlToLaTeX(ret)} \\\\ ")
        }

        outputFile.print("\\end{tabular} \\vspace{0.2 cm} \\\\ ")
      }

      val details = f.doc \ "detail"
      if (!details.isEmpty) {
        outputFile.print("\\mbox{\\hspace{0.015\\linewidth}} {\\bf Details:} \\vspace{0.2 cm} \\\\ \\mbox{\\hspace{0.045\\linewidth}} \\begin{minipage}{0.935\\linewidth}")
        outputFile.print(details.map(xmlToLaTeX).mkString(" \\vspace{0.1 cm} \\\\ "))
        outputFile.print("\\end{minipage} \\vspace{0.2 cm} \\vspace{0.2 cm} \\\\ ")
      }

      val errors = f.doc \ "error"
      if (!errors.isEmpty) {
        outputFile.print("\\mbox{\\hspace{0.015\\linewidth}} {\\bf Runtime Errors:} \\vspace{0.2 cm} \\\\ \\mbox{\\hspace{0.045\\linewidth}} \\begin{minipage}{0.935\\linewidth}")
        outputFile.print(errors.map(xmlToLaTeX).mkString(" \\vspace{0.1 cm} \\\\ "))
        outputFile.print("\\end{minipage} \\vspace{0.2 cm} \\vspace{0.2 cm} \\\\ ")
      }

      outputFile.println("}}%")
    }

    outputFile.println("""    }[{\bf FIXME: LaTeX error: wrong libfcn name!}]%
}%""")
    outputFile.close()

  }

}
