name := "test-with-csd"
organization := "csd-packager-test"
version := "0.1.0"
scalaVersion := "2.11.8"
buildCsd := true

val checkPublished = taskKey[Unit]("Check if the CSD was published")
checkPublished := {
  if(!(Path.userHome/".ivy2"/"local"/"csd-packager-test"/"test-with-csd_2.11"/"0.1.0"/"jars"/"TEST_WITH_CSD-0.1.0_2.11-csd.jar").exists())
    throw new Exception("CSD JAR did not exist in local repo")
}