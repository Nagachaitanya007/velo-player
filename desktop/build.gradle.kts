import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.compose") version "1.9.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("uk.co.caprica:vlcj:4.11.0")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "app.velo.MainKt"
        jvmArgs += listOf("-Xmx1536m")
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "Velo"
            packageVersion = "1.2.0"
            description = "Velo"
            vendor = "Velo"
            includeAllModules = true
            appResourcesRootDir.set(project.layout.projectDirectory.dir("vlc-bundle"))
            windows {
                menuGroup = "Velo"
                upgradeUuid = "6f1c2a40-7a1e-4d4a-9c1e-4b6a9d0e2f11"
            }
            macOS {
                bundleID = "app.velo.player"
            }
        }
    }
}
