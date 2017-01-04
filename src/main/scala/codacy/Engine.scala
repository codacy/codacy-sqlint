package codacy

import codacy.dockerApi.DockerEngine
import codacy.sqlint.SQLint

object Engine extends DockerEngine(SQLint)
