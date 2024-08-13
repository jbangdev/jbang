///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS com.github.zafarkhaja:java-semver:0.9.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2
//DEPS com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.15.2

import com.fasterxml.jackson.databind.JsonNode
import com.github.zafarkhaja.semver.Version
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import java.util.stream.StreamSupport
import java.util.Spliterators

fun main() {
// props to Chris Dellaway for the pointer to this
    val url = "https://oss.sonatype.org/content/repositories/snapshots/org/mongodb/mongodb-driver-sync/maven-metadata.xml"
    val mapper = XmlMapper()
    val min: String = System.getenv().getOrDefault("DRIVER_MIN", "5.0.0")
    val driverMinimum = Version.valueOf(min)
    var document = mapper.readTree(java.net.URL(url))
    val versions: JsonNode = document
        .get("versioning")
        .get("versions")
        .get("version")

    val result = versions.elements()
        .asSequence()
        .map{ it.asText() }
        .map { Version.valueOf(it) }
        .filter { it.greaterThanOrEqualTo(driverMinimum) }
        .sorted()
        .toList()

    println(result.last())
    println("SUCCESS!")
}
