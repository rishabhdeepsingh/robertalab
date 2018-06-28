#!/usr/bin/env bash
echo "Script to run the Desktop app and Server"
cd OpenRobertaParent/OpenRobertaDesktopApp
echo "Change Directory to OpenRobertaDesktopApp"
java -cp '../OpenRobertaServer/target/resources/*:target/dependency/*:target/OpenRobertaDesktopApp-2.7.2-SNAPSHOT.jar' de.fhg.iais.DesktopApp
