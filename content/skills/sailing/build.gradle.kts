plugins {
    id("base-conventions")
    id("integration-test-suite")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.api.death)
    implementation(projects.api.utils.utilsSkills)
    implementation(projects.content.skills.utils)
    implementation(projects.content.quest)
    implementation(libs.or2.all.cache)
    implementation(libs.netty.buffer)
    testImplementation(libs.fastutil)
    integrationImplementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.registry)
    integrationImplementation(projects.content.skills.smithing)
}
