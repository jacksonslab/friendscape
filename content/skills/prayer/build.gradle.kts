plugins {
    id("base-conventions")
    id("integration-test-suite")
    id("game-cache-test-conventions")

}

dependencies {
    integrationImplementation(projects.api.generated)
    implementation(projects.api.areaChecker)
    implementation(projects.api.invtx)
    implementation(projects.api.death)
    implementation(projects.api.player)
    implementation(projects.api.script)
    implementation(projects.api.objCharges)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.content.skills.utils)
    implementation(projects.engine.events)
    implementation(projects.engine.utilsBits)
}
