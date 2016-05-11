package sbtcsdpackager

import java.nio.file.{StandardCopyOption, StandardOpenOption}

import csdbase.descriptor._
import csdbase.descriptor.parameter.Parameter
import csdbase.descriptor.role.{ConfigWriter, Gateway, Generator, Role}
import sbt._
import sbt.Keys._
import cats.data.Xor
import Xor._
import io.circe.{Printer, parser}
import io.circe.syntax._

object Plugin extends AutoPlugin {

  implicit class EitherOption[T,U](val either: Either[T,U]) extends AnyVal {
    def toOption = either.fold(
      fail => None,
      succ => Some(succ)
    )
  }

  def copyRecursive(source: File, dest: File): Unit = if(!source.name.startsWith(".")) {
    if(source.isDirectory) {
      if (!dest.exists)
        dest.mkdirs()

      (source * "*").get foreach {
        file =>
          val destFile = dest / file.name
          if (file.isDirectory)
            copyRecursive(file, destFile)
          else
            java.nio.file.Files.copy(file.toPath, destFile.toPath, StandardCopyOption.REPLACE_EXISTING)
      }
    } else {
      java.nio.file.Files.copy(source.toPath, dest.toPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private val sdlPrinter = Printer.spaces2.copy(dropNullKeys = true)

  val csdServiceSdl = settingKey[File]("Filename of CSD service.sdl")
  val validatedSdl = taskKey[Option[CSDescriptorData]]("Validated service.sdl document")
  val generatedSdl = taskKey[Option[CSDescriptorData]]("Generated service.sdl document")
  val generateServiceParameters = taskKey[Seq[Parameter]]("Generate service parameters from program's configuration")
  val csdArtifact = taskKey[Artifact]("Artifact for CSD file")

  def mkOptional[A](seq: Seq[A]) = seq.headOption.map(_ => seq)
  def mkOptionalBoolean(boolean: Boolean, default: Boolean) = if(boolean != default) Some(boolean) else None

  object autoImport {

    val csd = taskKey[File]("Build a Custom Service Descriptor artifact for the project")
    val csdAddScripts = taskKey[Seq[(File, String)]]("Files to add to the CSD Scripts directory")
    val csdAddAux = taskKey[Seq[(File, String)]]("Libraries to add to the CSD aux directory")
    val csdRoot = settingKey[File]("Base directory for CSD files")
    val csdIncludeArtifact = taskKey[Option[(File, String)]]("Artifact to include in the CSD scripts directory")
    val csdGenerateSdl = settingKey[Boolean]("Whether to generate the service.sdl file, rather than use a supplied one")
    val buildCsd = settingKey[Boolean]("Whether to build a CSD for the project")

    object csdSettings {
      val label = settingKey[String]("Label for CSD")
      val runAs = settingKey[UserGroup]("User and group to run as.  Defaults to nobody/nobody.")
      val serviceInit = settingKey[Option[ServiceInit]]("Service Initialization specifier. Defaults to None.")
      val inExpressWizard = settingKey[Boolean]("Whether to show the service in the CM express wizard.  Defaults to false.")
      val maxInstances = settingKey[Option[Int]]("Maximum number of service instances. Defaults to None.")
      val icon = settingKey[Option[String]]("Icon to use in Cloudera Manager. Defaults to None (the default icon).")
      val compatibility = settingKey[Option[Compatibility]]("Compatibility descriptor. Defaults to None.")
      val csdParcel = settingKey[Option[Parcel]]("Parcel repository to add to CM for this service. Defaults to None.")
      val serviceDependencies = settingKey[Seq[ServiceDependency]]("Services on which this service depends. Defaults to None.")
      val rolesWithExternalLinks = settingKey[Seq[String]]("List of roles which have external links. By default is computed from specified roles.")
      val hdfsDirs = settingKey[Seq[HDFSDir]]("List of HDFS directories for the service")
      val serviceCommands = settingKey[Seq[ServiceCommand]]("List of commands for the overall service")
      val serviceParameters = settingKey[Seq[Parameter]]("List of parameters for the overall service")
      val externalKerberosPrincipals = settingKey[Seq[KerberosPrincipal]]("List of external kerberos principals for the service")
      val kerberos = settingKey[Boolean]("Whether the service can be enabled for kerberos")
      val providesKms = settingKey[Option[ProvidesKms]]("Optional KMS provider descriptor for the service")
      val rollingRestart = settingKey[Option[RollingRestart]]("Optional rolling restart descriptor for the service")
      val roles = settingKey[Seq[Role]]("List of Roles for the service")
      val gateway = settingKey[Option[Gateway]]("Gateway role for the service; provides client configuration to cluster nodes")
    }

    import csdSettings._


    lazy val csdBaseSettings : Seq[sbt.Def.Setting[_]] = Seq(
      csdIncludeArtifact <<= sbt.Keys.`package`.map(pf => Some(pf -> pf.name)),
      artifact in csd := (if(buildCsd.value)
          Artifact(s"${(name in csd).value}-${(version in csd).value}", "jar", "jar", (artifactClassifier in csd).value.getOrElse("csd"))
        else
          (artifact in compile).value),
      publishArtifact in csd := true,
      sbt.Keys.`package` in csd := csd.value,
      csdAddScripts := csdIncludeArtifact.value.toSeq,
      csdAddAux := Seq(),
      csdRoot := (sourceDirectory in Compile).value / "csd",
      csdServiceSdl := csdRoot.value / "descriptor" / "service.sdl",
      name in csd := name.value.toUpperCase.replaceAll("[^A-Z0-9_]+", "_"),
      csdSettings.label in csd := name.value,
      version in csd := version.value,
      description in csd := description.value,
      runAs := UserGroup(user = "nobody", group = "nobody"),
      serviceInit := None,
      inExpressWizard  := false,
      maxInstances := None,
      icon := None,
      compatibility := None,
      csdParcel := None,
      serviceDependencies := Seq(),
      roles in Global  := Seq(),
      rolesWithExternalLinks  := roles.value
        .filter(role => role.externalLink.nonEmpty || role.additionalExternalLinks.exists(_.nonEmpty))
          .map(_.name),
      hdfsDirs  := Seq(),
      serviceCommands  := Seq(),
      serviceParameters  := Seq(),
      externalKerberosPrincipals  := Seq(),
      kerberos  := false,
      providesKms  := None,
      rollingRestart  := None,
      csdGenerateSdl  := false,
      gateway := None,
      generateServiceParameters  := Seq(), //TODO
      generatedSdl := {
        if(csdGenerateSdl.value) {
          Some(CSDescriptorData(
            name = (name in csd).value,
            label = (csdSettings.label in csd).value,
            version = (version in csd).value,
            description = (description in csd).value,
            runAs = runAs.value,
            serviceInit = serviceInit.value,
            inExpressWizard = mkOptionalBoolean(inExpressWizard.value, default = false),
            maxInstances = maxInstances.value,
            icon = icon.value,
            compatibility = compatibility.value,
            parcel = csdParcel.value,
            serviceDependencies = mkOptional(serviceDependencies.value),
            rolesWithExternalLinks = mkOptional(rolesWithExternalLinks.value),
            hdfsDirs = mkOptional(hdfsDirs.value),
            commands = mkOptional(serviceCommands.value),
            parameters = mkOptional(serviceParameters.value),
            externalKerberosPrincipals = mkOptional(externalKerberosPrincipals.value),
            kerberos = mkOptionalBoolean(kerberos.value, default = false),
            providesKms = providesKms.value,
            rollingRestart = rollingRestart.value,
            roles = mkOptional(roles.value),
            gateway = gateway.value
          ))
        } else None
      },
      validatedSdl  := {
        generatedSdl.value match {
          case Some(data) => Some(data)
          case None =>
            val file = csdServiceSdl.value
            if(file.exists()) {
              val jsonString = new String(java.nio.file.Files.readAllBytes(file.toPath))
              parser.parse(jsonString).flatMap(_.as[CSDescriptorData]) match {
                case Right(data) => Some(data)
                case Left(err) => throw new Exception("CSD could not be validated", err)
              }
            } else {
              streams.value.log.warn(s"Descriptor file $file does not exist; skipping CSD build")
              None
            }
        }
      },
      packagedArtifact in csd <<= (
        streams,
        name,
        artifact in csd,
        packagedArtifact in (Compile,packageBin),
        csdRoot,
        crossTarget,
        csdAddScripts,
        csdAddAux,
        validatedSdl,
        buildCsd,
        csdServiceSdl
      ).map {
        (streams, projName, art, compileArt, root, crossTarget, addScripts, addAux, sdl, buildCsd, serviceSdl) =>

          if(sdl.isEmpty && buildCsd) {
            throw new RuntimeException(
              s"""
                |CSD could not be built, because no content is available for service.sdl.  Either:
                |1. Provide a service.sdl file in $root/descriptor/service.sdl, or
                |2. Set csdGenerateSdl := true, and provide the necessary settings to generate it.
              """.stripMargin)
          }
          val jarFile = crossTarget / s"${art.name}.${art.extension}"

          if(sdl.isEmpty) {
            streams.log.info(s"No SDL found in $projName; skipping CSD build")
            compileArt._1 -> compileArt._2

          } else {

            val targetRoot = crossTarget / art.name
            if (root.exists)
              copyRecursive(root, targetRoot)



            val scriptsRoot = targetRoot / "scripts"
            if (!scriptsRoot.exists)
              scriptsRoot.mkdir()

            addScripts foreach {
              case (file, fileTargetName) => copyRecursive(file, scriptsRoot / fileTargetName)
            }

            if (addAux.nonEmpty) {
              val auxDir = targetRoot / "aux"
              if (!auxDir.exists())
                auxDir.mkdir()

              addAux foreach {
                case (file, fileTargetName) => copyRecursive(file, auxDir / fileTargetName)
              }
            }

            val csdFiles = targetRoot.***.get map {
              file => (file, targetRoot.toPath.relativize(file.toPath).toString)
            }

            val descriptorDir = targetRoot / "descriptor"
            if (!descriptorDir.exists)
              descriptorDir.mkdir()

            val descriptorFile = descriptorDir / "service.sdl"
            sdl.filterNot(_ => serviceSdl.exists()) foreach {
              data =>
                val json = data.asJson.pretty(sdlPrinter)
                java.nio.file.Files.write(descriptorFile.toPath, json.getBytes("UTF-8"), StandardOpenOption.CREATE)
            }

            val log = streams.log
            val manifest = new java.util.jar.Manifest

            Package.makeJar(csdFiles, jarFile, manifest, log)

            art -> jarFile
          }
      }
    )

  }

  import autoImport._
  override def requires = sbt.plugins.JvmPlugin && sbt.plugins.IvyPlugin

  // This plugin is automatically enabled for projects which are JvmModules.
  override def trigger = allRequirements

  // a group of settings that are automatically added to projects.
  override val projectSettings = inConfig(Compile)(csdBaseSettings) ++
    Seq(csd := (packagedArtifact in (Compile,csd)).value._2) ++
    Seq(buildCsd in Global := false) ++
    addArtifact(artifact in (Compile, csd), csd)

}
