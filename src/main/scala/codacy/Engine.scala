package codacy

import codacy.sqlint.SQLint
import com.codacy.tools.scala.seed.DockerEngine

object Engine extends DockerEngine(SQLint)()
