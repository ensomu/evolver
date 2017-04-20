package evolver

import java.io.{File, FilenameFilter}

import com.datastax.driver.core.Cluster
import com.typesafe.scalalogging.slf4j.LazyLogging

import scala.io.Source

object Evolver extends App {

  val parser = new scopt.OptionParser[Config]("evolver") {

    opt[String]("host").abbr("h").optional().action( (x, c) =>
      c.copy(host = x)).text("cassandra host, defaults to localhost")

    opt[Int]("port").abbr("p").optional().action( (x, c) =>
      c.copy(port = x) ).text("cassandra port, defaults to 9042")

    opt[String]("keyspace").abbr("k").required().action( (x, c) =>
      c.copy(keyspace = x) ).text("cassandra keyspace, required")

    opt[String]("version").abbr("v").optional().action( (x, c) =>
      c.copy(version = Some(x)) ).text("version to evolve to")

    cmd("init").action( (_, c) => c.copy(command = "init") )
      .text("init - initialises cassandra for Evolver. No data migrations are run.")

    cmd("evolve").action( (_, c) => c.copy(command = "evolve") ).text("evolve - Evolves the keyspace to the given version.").
      children(
        opt[String]("cqlfolder").abbr("c").action( (x, c) =>
          c.copy(cqlFolder = Some(new File(x)))).text("folder containing cql migration scripts"),
        checkConfig( c =>
          if (c.keyspace=="") failure("cassandra keyspace not set")
          else if (c.command=="evolve" && (c.cqlFolder.isEmpty || !c.cqlFolder.get.exists() || !c.cqlFolder.get.isDirectory))
            failure("cqlfolder invalid") else success)
      )
  }

  parser.parse(args, Config()).foreach{ c=>
    val evolver = new Evolver(c.host, c.port, c.keyspace)
    c.command match {
      case "init" => evolver.init(c.version)
      case "evolve" => evolver.evolve(c.cqlFolder.get, c.version)
    }
    evolver.stop()
  }
}

class Evolver(host: String, port: Int, keyspace: String) extends LazyLogging {

  val cluster = createCluster()
  val session = cluster.connect(keyspace)

  lazy val insertIntoEvolverLog = session.prepare("INSERT INTO evolver_log (key, version, time) VALUES ('evolver', ?, toTimestamp(now()));")
  lazy val selectEvolvedVersion = session.prepare("SELECT version FROM evolver_log WHERE key='evolver' LIMIT 1;")

  implicit def stringToString(s: String): String2 = new String2(s)

  def init(version: Option[String] = None): Unit = {

    require(cluster.getMetadata.getKeyspace(keyspace).getTable("evolver_log")==null,
      s"Evolver was already initialized for keyspace $keyspace on $host:$port.")

    val source = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("evolver.cql"))

    readCqlStmts(source).foreach(session.execute)

    val initVersion = version.getOrElse("\u0000\u0000\u00000.0.0")
    logger.info(s"Initializing to version $initVersion")
    session.execute(insertIntoEvolverLog.bind(initVersion))

    logger.info(s"Keyspace $keyspace on $host:$port is now at version $initVersion")
  }

  def evolve(cqlFolder: File, version: Option[String] = None): Unit = {
    require(cluster.getMetadata.getKeyspace(keyspace).getTable("evolver_log")!=null,
      s"Evolver is not initialized for keyspace $keyspace on $host:$port.")

    val currentVersion = session.execute(selectEvolvedVersion.bind()).one().getString("version")

    val cqlFiles = cqlFolder.listFiles(new FilenameFilter() {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".cql") &&
        version.map(_.compareTo(name.stripCqlSuffix) > 0).getOrElse(true) &&
        currentVersion.compareTo(name.stripCqlSuffix) < 0
    }).sortBy(_.getName)

    if (cqlFiles.isEmpty) {
      logger.info("Nothing to evolve, up to date.")
      return
    }

    cqlFiles.foreach { f =>
      logger.info(s"Evolving to version ${f.getName.stripCqlSuffix}")
      readCqlStmts(Source.fromFile(f)).filter(_.trim.length>0).foreach(session.execute)
      session.execute(insertIntoEvolverLog.bind(f.getName.stripCqlSuffix))
    }

    logger.info(s"Keyspace $keyspace on $host:$port is now at version ${cqlFiles.last.getName.stripCqlSuffix}")
  }

  def readCqlStmts(source: Source) =
    try source.mkString.split("\n").filterNot(_.startsWith("--")).mkString("\n").split(";") finally source.close()

  def createCluster() = Cluster.builder.addContactPoints(host).withPort(port).build

  def stop() = {
    logger.info("Shutting down.")
    session.close()
    cluster.close()
  }
}

case class Config(command: String = "", host:String = "localhost", port:Int = 9042, keyspace:String = "", cqlFolder:Option[File] = None, version: Option[String] = None)

class String2(val s: String) {
  def stripCqlSuffix = s.stripSuffix(".cql")
}