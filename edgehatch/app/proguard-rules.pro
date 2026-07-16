# EdgeHatch — keep the Application, launcher Activity, service and receiver
# entry points referenced only from the manifest.
-keep class app.edgehatch.launcher.SidePanelApp
-keep class app.edgehatch.launcher.MainActivity
-keep class app.edgehatch.launcher.EdgeOverlayService
-keep class app.edgehatch.launcher.BootReceiver
