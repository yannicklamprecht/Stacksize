plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.3.5"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "no.hyp.stacksize"
version = "1.2.0"

tasks {
    // Run reobfJar on build
    build {
        dependsOn(reobfJar)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }
}


repositories {
    mavenCentral()
    mavenLocal()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    paperDevBundle("1.18.2-R0.1-SNAPSHOT")
    implementation("xyz.jpenilla", "reflection-remapper", "0.1.0-SNAPSHOT")
    testCompileOnly(group= "junit", name= "junit", version= "4.13.1")
}
