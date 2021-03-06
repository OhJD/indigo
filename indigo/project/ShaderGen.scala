import sbt._
import scala.sys.process._

object ShaderGen {

  val extensions: List[String] =
    List(".vert", ".frag")

  val fileFilter: String => Boolean =
    name => extensions.exists(e => name.endsWith(e))

  val tripleQuotes: String = "\"\"\""

  def template(name: String, vextexContents: String, fragmentContents: String): String =
    s"""package indigo.platform.shaders
    |
    |import indigo.shared.display.Shader
    |
    |object $name extends Shader {
    |  val vertex: String =
    |    ${tripleQuotes}${vextexContents}${tripleQuotes}
    |
    |  val fragment: String =
    |    ${tripleQuotes}${fragmentContents}${tripleQuotes}
    |}
    """.stripMargin

  def splitAndPair(remaining: Seq[String], name: String, file: File): Option[ShaderDetails] =
    remaining match {
      case Nil =>
        None

      case ext :: exts if name.endsWith(ext) =>
        Some(ShaderDetails(name.substring(0, name.indexOf(ext)).capitalize, name, ext, IO.read(file)))

      case _ :: exts =>
        splitAndPair(exts, name, file)
    }

  def makeShader(files: Set[File], sourceManagedDir: File): Seq[File] = {
    println("Generating Indigo Shader Classes...")

    val shaderFiles: Seq[File] =
      files.filter(f => fileFilter(f.name)).toSeq

    val glslValidatorExitCode = "glslangValidator -v" !

    println("***************")
    println("GLSL Validation")
    println("***************")

    if (glslValidatorExitCode == 0) {
      shaderFiles.foreach { f =>
        val exit = ("glslangValidator " + f.getCanonicalPath) !

        if (exit != 0) {
          throw new Exception("GLSL Validation Error in: " + f.getName)
        } else {
          println(f.getName + " [valid]")
        }
      }
    } else {
      println("**WARNING**: GLSL Validator not installed, shader code not checked.")
    }

    val dict: Map[String, Seq[ShaderDetails]] =
      shaderFiles
        .map(f => splitAndPair(extensions, f.name, f))
        .collect { case Some(s) => s }
        .groupBy(_.newName)

    dict.toSeq.map {
      case (newName, subShaders: Seq[ShaderDetails]) if subShaders.length != 2 =>
        throw new Exception("Shader called '" + newName + "' did not appear to be a pair of shaders .vert and .frag")

      case (newName, subShaders: Seq[ShaderDetails]) if !subShaders.exists(_.ext == ".vert") =>
        throw new Exception("Shader called '" + newName + "' is missing a .vert shader")

      case (newName, subShaders: Seq[ShaderDetails]) if !subShaders.exists(_.ext == ".frag") =>
        throw new Exception("Shader called '" + newName + "' is missing a .frag shader")

      case (newName, subShaders: Seq[ShaderDetails]) =>
        val vert = subShaders.find(_.ext == ".vert").map(_.shaderCode)
        val frag = subShaders.find(_.ext == ".frag").map(_.shaderCode)

        val originalName: String =
          subShaders.headOption.map(_.originalName).getOrElse("<missing name... bad news.>")

        (vert, frag) match {
          case (Some(v), Some(f)) =>
            println("> " + originalName + " --> " + newName + ".scala")

            val file: File =
              sourceManagedDir / "indigo" / "platform" / "shaders" / (newName + ".scala")

            val newContents: String =
              template(newName, v, f)

            IO.write(file, newContents)

            println("Written: " + file.getCanonicalPath)

            file

          case (None, _) =>
            throw new Exception("Couldn't find vertex shader details")

          case (_, None) =>
            throw new Exception("Couldn't find fragment shader details")

          case _ =>
            throw new Exception("Couldn't find shader details for reasons that are unclear...")
        }

    }
  }

}

case class ShaderDetails(newName: String, originalName: String, ext: String, shaderCode: String)
