plugins {
    kotlin("multiplatform") version "1.9.21"
}

kotlin {
    js {
        browser {
        }
        binaries.executable()
    }
}
