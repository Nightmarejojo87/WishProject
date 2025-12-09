plugins {
    id("com.android.application") version "8.13.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // La version de KSP doit commencer par 1.9.22 pour matcher la ligne du dessus !
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    alias(libs.plugins.google.gms.google.services) apply false
}