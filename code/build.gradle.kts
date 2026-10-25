plugins {
    java
}

group = "solrbook"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val solrVersion = "10.0.0"
// The Lucene release Solr 10.0.0 is built on — §1.5 uses Lucene directly.
val luceneVersion = "10.3.2"
val testcontainersVersion = "1.21.4"

dependencies {
    // SolrJ core (HttpJdkSolrClient lives here; no extra deps needed)
    implementation("org.apache.solr:solr-solrj:$solrVersion")
    // Jetty-based clients: HttpJettySolrClient, ConcurrentUpdateJettySolrClient,
    // and the transport CloudSolrClient prefers when Jetty is on the classpath.
    implementation("org.apache.solr:solr-solrj-jetty:$solrVersion")

    // Plain Lucene, no Solr: the embedded-library examples in §1.5.
    implementation("org.apache.lucene:lucene-core:$luceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$luceneVersion")
    implementation("org.apache.lucene:lucene-queryparser:$luceneVersion")

    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:solr")

    // The OpenAI-compatible embedding stub builds JSON with Jackson, which SolrJ only
    // exposes at runtime.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // Integration tests (Testcontainers + Docker) run only via :integrationTest
        if (name == "test") {
            excludeTags("integration")
        }
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs Testcontainers-based integration tests against a real Solr (requires Docker)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}
