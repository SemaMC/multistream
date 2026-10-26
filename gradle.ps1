# Wrapper script for Gradle that ensures correct Java version
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
& "$PSScriptRoot\gradlew.bat" @args
