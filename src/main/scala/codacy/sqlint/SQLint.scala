package codacy.sqlint

import codacy.docker.api.Result.Issue
import codacy.docker.api.utils.ToolHelper
import codacy.docker.api.{Pattern, Result, Source, Tool}
import codacy.dockerApi.utils.CommandRunner

import scala.util.{Failure, Properties, Success, Try}

object SQLint extends Tool {

  override def apply(source: Source.Directory, configuration: Option[List[Pattern.Definition]], files: Option[Set[Source.File]])
                    (implicit specification: Tool.Specification): Try[List[Result]] = {
    Try {
      ToolHelper.patternsToLint(configuration).collect { case patterns if patterns.nonEmpty =>
        //This tool returns all messages mapped to only one pattern
        implicit val godPattern = patterns.head
        val path = new java.io.File(source.path)
        val filesToLint: Set[String] = ToolHelper.filesToLint(source, files)

        val command = List("sqlint") ++ filesToLint

        CommandRunner.exec(command, Some(path)) match {
          case Right(resultFromTool) if resultFromTool.exitCode < 2 =>
            Success(parseToolResult(resultFromTool.stdout))
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
      }.getOrElse(Success(List.empty[Result]))
    }.flatten
  }

  private def parseToolResult(outputLines: List[String])(implicit godPattern: Pattern.Definition): List[Result] = {
    outputLines.flatMap(parseLineResult(_))
  }

  private def parseLineResult(resultLine: String)(implicit godPattern: Pattern.Definition): Option[Result] = {
    val LineRegex = """(.*?):([0-9]+):[0-9]*:?(ERROR|WARNING)\s*(.*)""".r

    Option(resultLine).collect { case LineRegex(filename, lineNumber, _, message) =>
      Issue(
        Source.File(filename),
        Result.Message(message),
        godPattern.patternId,
        Source.Line(lineNumber.toInt)
      )
    }
  }
}
