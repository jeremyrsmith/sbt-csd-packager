
ScriptedPlugin.scriptedSettings

lazy val scriptedDebugPort = settingKey[Option[Int]]("A debug port for the scripted SBT session")
lazy val scriptedDebugSuspend = settingKey[Boolean]("Whether to suspend and wait for a debugger to connect when running scripted tests")

scriptedDebugPort := None
scriptedDebugSuspend := true

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value) ++
  scriptedDebugPort.value.map(i => s"-agentlib:jdwp=transport=dt_socket,server=y,address=$i,suspend=${if(scriptedDebugSuspend.value) "y" else "n"}").toSeq
}

scriptedBufferLog := false