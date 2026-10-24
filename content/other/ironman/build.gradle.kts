plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.account)
    implementation(projects.api.db)
    implementation(projects.api.serverConfig)
    implementation(projects.content.interfaces.bank)
    implementation(libs.rsprot.api)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.embedded.postgres)
    testImplementation(libs.jackson.module.kotlin)
    implementation(projects.api.attr)
    implementation(projects.api.death)
    implementation(projects.api.invtx)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
}

tasks.withType<Test>().configureEach {
    workingDir(rootProject.projectDir)
    inputs.dir(rootProject.file(".data/cache/SERVER"))
    inputs.file(rootProject.file(".data/gamevals-binary/gamevals.dat"))
}
