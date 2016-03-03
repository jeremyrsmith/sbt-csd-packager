package sbtcsdpackager

import java.nio.file.{StandardCopyOption, StandardOpenOption}

import csdbase.descriptor._
import csdbase.descriptor.parameter.Parameter
import csdbase.descriptor.role.{Generator, ConfigWriter, Gateway, Role}
import sbt._, sbt.Keys._
import cats.data.Xor, Xor._

import io.circe.parser, io.circe.syntax._

object Plugin extends AutoPlugin {


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

  val csdNameVersion = settingKey[(String,String)]("Name and version of CSD (read from service.sdl)")
  val csdJarName = settingKey[String]("Name of CSD Jar")
  val csdServiceSdl = settingKey[File]("Filename of CSD service.sdl")
  val validatedSdl = settingKey[CSDescriptorData]("Validated service.sdl document")
  val generatedSdl = settingKey[Option[CSDescriptorData]]("Generated service.sdl document")
  val generateServiceParameters = taskKey[Seq[Parameter]]("Generate service parameters from program's configuration")

  def mkOptional[A](seq: Seq[A]) = seq.headOption.map(_ => seq)
  def mkOptionalBoolean(boolean: Boolean, default: Boolean) = if(boolean != default) Some(boolean) else None

  object autoImport {

    val csd = taskKey[File]("Build a Custom Service Descriptor")
    val csdAddScripts = taskKey[Seq[(File, String)]]("Files to add to the CSD Scripts directory")
    val csdRoot = settingKey[File]("Base directory for CSD files")
    val csdClassifier = settingKey[String]("Classifier for CSD artifact")
    val csdGenerateSdl = settingKey[Boolean]("Whether to generate the service.sdl file, rather than use a supplied one")

    object csdSettings {
      val csdName = settingKey[String]("Name of CSD. By convention, is all uppercase. Can only contain alphanumeric and underscore. By default, is derived from the name of the build.")
      val csdVersion = settingKey[String]("Version of CSD.  Default is the version of the build.")
      val csdDescription = settingKey[String]("Description of CSD.  Default is the description of the build.")
      val runAs = settingKey[UserGroup]("User and group to run as.  Defaults to nobody/nobody.")
      val serviceInit = settingKey[Option[ServiceInit]]("Service Initialization specifier. Defaults to None.")
      val inExpressWizard = settingKey[Boolean]("Whether to show the service in the CM express wizard.  Defaults to false.")
      val maxInstances = settingKey[Option[Int]]("Maximum number of service instances. Defaults to None.")
      val icon = settingKey[Option[String]]("Icon to use in Cloudera Manager. Defaults to None (the default icon).")
      val compatibility = settingKey[Option[Compatibility]]("Compatibility descriptor. Defaults to None.")
      val parcel = settingKey[Option[Parcel]]("Parcel repository to add to CM for this service. Defaults to None.")
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
      csdAddScripts := Seq((sbt.Keys.`package` in Compile).value -> (sbt.Keys.`package` in Compile).value.name),
      csdRoot := (sourceDirectory in Compile).value / "csd",
      csdServiceSdl := csdRoot.value / "descriptor" / "service.sdl",
      csdName := (name in Compile).value.toUpperCase.replaceAll("[^A-Z0-9_]+", "_"),
      csdVersion := (version in Compile).value,
      csdDescription := (description in Compile).value,
      runAs := UserGroup(user = "nobody", group = "nobody"),
      serviceInit := None,
      inExpressWizard := false,
      maxInstances := None,
      icon := None,
      compatibility := None,
      parcel := None,
      serviceDependencies := Seq(),
      roles := Seq(),
      rolesWithExternalLinks := roles.value
        .filter(role => role.externalLink.nonEmpty || role.additionalExternalLinks.exists(_.nonEmpty))
          .map(_.name),
      hdfsDirs := Seq(),
      serviceCommands := Seq(),
      serviceParameters := Seq(),
      externalKerberosPrincipals := Seq(),
      kerberos := false,
      providesKms := None,
      rollingRestart := None,
      csdGenerateSdl := false,
      gateway := None,
      generateServiceParameters := Seq(), //TODO
      generatedSdl := {
        if(csdGenerateSdl.value) {
          Some(CSDescriptorData(
            name = csdName.value,
            version = csdVersion.value,
            description = csdDescription.value,
            runAs = runAs.value,
            serviceInit = serviceInit.value,
            inExpressWizard = mkOptionalBoolean(inExpressWizard.value, default = false),
            maxInstances = maxInstances.value,
            icon = icon.value,
            compatibility = compatibility.value,
            parcel = parcel.value,
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
      validatedSdl := {
        generatedSdl.value match {
          case Some(data) => data
          case None =>
            val file = csdServiceSdl.value
            val jsonString = new String(java.nio.file.Files.readAllBytes(file.toPath))
            parser.parse(jsonString).flatMap(_.as[CSDescriptorData]) match {
              case Right(data) => data
              case Left(err) => throw new Exception("CSD could not be validated", err)
            }
        }
      },
      csdNameVersion := {
        val validated = validatedSdl.value
        (validated.name, validated.version)
      },
      csdJarName := {
        val (name, version) = csdNameVersion.value
        s"$name-$version"
      },
      csdClassifier := "csd",
      artifact in (Compile, csd) := Artifact(name = csdNameVersion.value._1, classifier = "csd"),
      csd := {
        val root = csdRoot.value
        val jarName = csdJarName.value
        val targetRoot = crossTarget.value / jarName
        copyRecursive(root, targetRoot)

        val scriptsRoot = targetRoot / "scripts"
        if(!scriptsRoot.exists)
          scriptsRoot.mkdir()

        val addScripts = csdAddScripts.value
        addScripts foreach {
          case (file, fileTargetName) => copyRecursive(file, scriptsRoot / fileTargetName)
        }

        val csdFiles = targetRoot.***.get map {
          file => (file, targetRoot.toPath.relativize(file.toPath).toString)
        }

        val descriptorDir = targetRoot / "descriptor"
        if(!descriptorDir.exists)
          descriptorDir.mkdir()

        val descriptorFile = descriptorDir / "service.sdl"
        generatedSdl.value foreach {
          data =>
            val json = data.asJson.toString()
            java.nio.file.Files.write(descriptorFile.toPath, json.getBytes("UTF-8"), StandardOpenOption.CREATE)
        }

        val jarFile = crossTarget.value / s"$jarName.jar"

        val log = streams.value.log
        val manifest = new java.util.jar.Manifest

        Package.makeJar(csdFiles, jarFile, manifest, log)

        jarFile
      }
    )

  }

  import autoImport._
  override def requires = sbt.plugins.JvmPlugin && sbt.plugins.IvyPlugin

  // This plugin is automatically enabled for projects which are JvmModules.
  override def trigger = allRequirements

  // a group of settings that are automatically added to projects.
  override val projectSettings = csdBaseSettings ++ addArtifact(artifact in (Compile, csd), csd).settings

}
