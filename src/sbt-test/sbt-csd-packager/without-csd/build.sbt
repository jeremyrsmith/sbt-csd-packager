name := "test-without-csd"
organization := "csd-packager-test"
version := "0.1.0"
scalaVersion := "2.11.8"

val checkNotPublished = taskKey[Unit]("Check if the CSD was not published")
checkNotPublished := {
  if((Path.userHome/".ivy2"/"local"/"csd-packager-test"/"test-without-csd_2.11"/"0.1.0"/"jars"/"TEST_WITHOUT_CSD-0.1.0_2.11-csd.jar").exists())
    throw new Exception("CSD JAR was published to local repo")
}