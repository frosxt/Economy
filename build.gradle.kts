plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.github.frosxt"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

// PrisonCore platform JARs are consumed as pre-built files from the sibling
// Prisons project. Build Prisons first with `./gradlew build` in that directory.
val prisonsLibs = rootDir.resolve("../Prisons")

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("org.mongodb:mongodb-driver-sync:4.11.1")

    compileOnly(files(prisonsLibs.resolve("platform-api/build/libs/platform-api-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-spi/build/libs/platform-spi-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-commons/build/libs/platform-commons-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-command/build/libs/platform-command-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-menu/build/libs/platform-menu-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-message/build/libs/platform-message-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-placeholder/build/libs/platform-placeholder-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-player/build/libs/platform-player-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-storage/build/libs/platform-storage-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-kernel/build/libs/platform-kernel-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-config/build/libs/platform-config-1.0.0-SNAPSHOT.jar")))
    compileOnly(files(prisonsLibs.resolve("platform-scheduler/build/libs/platform-scheduler-1.0.0-SNAPSHOT.jar")))
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("economy-${project.version}.jar")
    manifest {
        attributes["PrisonCore-Bootstrap"] = "com.github.frosxt.economy.bootstrap.EconomyBootstrap"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks {
    runServer {
        minecraftVersion("1.20.1")
    }
}
