plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(libs.rsprot.api)
    testImplementation(projects.api.invStorage)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.playerOutput)
    implementation(projects.api.config)
    implementation(projects.content.generic.genericLocs)
    integrationImplementation(projects.content.generic.genericLocs)
    implementation(projects.content.interfaces.settings)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.interfaces.gameframe)
    implementation(projects.content.skills.smithing)
    integrationImplementation(projects.content.skills.smithing)
    implementation(projects.content.skills.cooking)
}

tasks.withType<Test>().configureEach {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
