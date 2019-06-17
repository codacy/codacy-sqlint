package codacy.sqlint

import com.codacy.plugins.api.results.Result.Issue
import com.codacy.plugins.api.{Options, Source}
import com.codacy.plugins.api.results.{Pattern, Result, Tool}
import com.codacy.tools.scala.seed.utils.CommandRunner
import com.codacy.tools.scala.seed.utils.ToolHelper._

import scala.util.{Failure, Properties, Success, Try}

object SQLint extends Tool {

  def apply(source: Source.Directory,
            configuration: Option[List[Pattern.Definition]],
            files: Option[Set[Source.File]],
            options: Map[Options.Key, Options.Value])(implicit specification: Tool.Specification): Try[List[Result]] = {
    Try {
      val dockerResults = for {
        patterns <- configuration.withDefaultParameters(specification)
        //This tool returns all messages mapped to only one pattern
        pattern <- patterns.headOption
      } yield {
        val path = new java.io.File(source.path)
        val filesToLint: Set[String] = files.fold(Set(source.path))(_.map(_.path))

        val command = List("sqlint") ++ filesToLint

        CommandRunner.exec(command, Some(path)) match {
          case Right(resultFromTool) if resultFromTool.exitCode < 2 =>
            Success(parseToolResult(resultFromTool.stdout, pattern))
          case Right(resultFromTool) =>
            val msg =
              s"""
                 |SQLint exited with code ${resultFromTool.exitCode}
                 |stdout: ${resultFromTool.stdout.mkString(Properties.lineSeparator)}
                 |stderr: ${resultFromTool.stderr.mkString(Properties.lineSeparator)}
                """.stripMargin
            Failure(new Exception(msg))
          case Left(e) =>
            Failure(e)
        }
      }
      dockerResults.getOrElse(Success(List.empty[Result]))
    }.flatten
  }

  private def parseToolResult(outputLines: List[String], pattern: Pattern.Definition): List[Result] = {
    outputLines.flatMap(parseLineResult(_, pattern))
  }

  private def parseLineResult(resultLine: String, pattern: Pattern.Definition): Option[Result] = {
    val LineRegex = """(.*?):([0-9]+):[0-9]*:?(ERROR|WARNING)\s*(.*)""".r

    Option(resultLine).collect { case LineRegex(filename, lineNumber, _, message) =>
      Issue(
        Source.File(filename),
        Result.Message(message),
        pattern.patternId,
        Source.Line(lineNumber.toInt)
      )
    }
  }
}
