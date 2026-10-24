plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.api.config)
    implementation(projects.api.instances)
    implementation(projects.api.invtx)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.player)
    implementation(projects.api.registry)
    implementation(projects.api.repo)
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.fastutil)
}
