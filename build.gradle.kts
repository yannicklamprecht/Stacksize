plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.3.5-SNAPSHOT"
    id ("systems.manifold.manifold-gradle-plugin") version "0.0.2-alpha"
}

group = "no.hyp.stacksize"
version = "1.1.0"

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

manifold {
    manifoldVersion.set("2022.1.5")
}


repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    paperDevBundle("1.18.1-R0.1-SNAPSHOT")
    testCompileOnly(group= "junit", name= "junit", version= "4.13.1")
    implementation("systems.manifold:manifold-json-rt:${manifold.manifoldVersion.get()}")
    implementation("systems.manifold:manifold-props-rt:${manifold.manifoldVersion.get()}")
    annotationProcessor("systems.manifold:manifold-json:${manifold.manifoldVersion.get()}")
    annotationProcessor("systems.manifold:manifold-props:${manifold.manifoldVersion.get()}")
}
