dependencies {
    implementation(project(":common"))
    compileOnly("dev.arbjerg:lavaplayer:2.2.6")

    testImplementation("dev.arbjerg:lavaplayer:2.2.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
