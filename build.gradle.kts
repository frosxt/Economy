plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.6"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.github.frosxt"
version = "v1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

val prisonCoreVersion: String = (findProperty("prisonCoreVersion") as String?) ?: "main-SNAPSHOT"

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.mongodb:mongodb-driver-sync:4.11.1")

    compileOnly("com.github.frosxt.Prisons:platform-api:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-spi:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-commons:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-command:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-menu:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-message:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-placeholder:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-player:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-storage:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-kernel:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-config:$prisonCoreVersion")
    compileOnly("com.github.frosxt.Prisons:platform-scheduler:$prisonCoreVersion")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("economy-${project.version}.jar")
    manifest {
        attributes["PrisonCore-Bootstrap"] = "com.github.frosxt.economy.bootstrap.EconomyBootstrap"
    }
}

tasks.jar {
    // The shadow jar replaces the standard jar as the runtime artifact.
    // Disable the empty default jar so it doesn't collide with shadowJar.
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
            artifact(tasks.named("sourcesJar"))
            artifactId = "Economy"
            groupId = "com.github.frosxt"
            version = project.version.toString()
        }
    }
}

tasks {
    runServer {
        minecraftVersion("1.20.1")
    }
}
