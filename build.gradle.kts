plugins {
	id("qupath-conventions")
	`maven-publish`
}

qupathExtension {
	name = "qupath-extension-wsinfer"
	version = "0.4.0-SNAPSHOT"
	group = "io.github.qupath"
	description = "QuPath extension to run WSInfer models in QuPath"
	automaticModule = "qupath.extension.py4j"
}

dependencies {

	implementation(libs.bundles.qupath)
	implementation(libs.bundles.logging)
	implementation(libs.qupath.fxtras)
	implementation(libs.bundles.markdown)
	implementation(libs.deepJavaLibrary)

	// For testing
	testImplementation(libs.junit)

}

publishing {
	repositories {
		maven {
			name = "SciJava"
			val releasesRepoUrl = uri("https://maven.scijava.org/content/repositories/releases")
			val snapshotsRepoUrl = uri("https://maven.scijava.org/content/repositories/snapshots")
			// Use gradle -Prelease publish
			url = if (project.hasProperty("release")) releasesRepoUrl else snapshotsRepoUrl
			credentials {
				username = System.getenv("MAVEN_USER")
				password = System.getenv("MAVEN_PASS")
			}
		}
	}

	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
			pom {
				licenses {
					license {
						name = "Apache License v2.0"
						url = "https://www.apache.org/licenses/LICENSE-2.0"
					}
				}
			}
		}
	}
}
