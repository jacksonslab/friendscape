plugins {
    id("base-conventions")
    id("integration-test-suite")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    integrationImplementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.registry)
    integrationImplementation(projects.api.utils.utilsSkills)
}
