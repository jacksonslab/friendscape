plugins {
    id("base-conventions")
    id("integration-test-suite")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(libs.fastutil)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
}
