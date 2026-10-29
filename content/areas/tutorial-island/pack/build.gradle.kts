plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.fastutil)
    implementation(libs.or2.tools)
    implementation(projects.engine.map)
}
