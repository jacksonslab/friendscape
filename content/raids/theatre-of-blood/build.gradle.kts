plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.bossHpBarPlugin)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.api.instances)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.content.interfaces.collectionLog)
    implementation(projects.content.other.pets)
}
