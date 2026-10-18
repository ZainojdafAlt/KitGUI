plugins { java }

group = "dev.kitgui"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // If this fails to resolve, check https://papermc.io/downloads/paper for the exact 26.2 API version
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
