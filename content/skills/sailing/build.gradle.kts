plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.api.utils.utilsSkills)
    implementation(libs.or2.all.cache)
    implementation(libs.netty.buffer)
    testImplementation(libs.fastutil)
    testImplementation(libs.netty.buffer)
    testImplementation(libs.or2.all.cache)
}
