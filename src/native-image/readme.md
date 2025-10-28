# Native Image Build Configuration for JBang

This folder contains the necessary native-image metadata configuration to make JBang buildable using GraalVM native-image.

## Location Rationale

Typically, this metadata would be stored in `src/main/resources/META-INF/native-image/dev.jbang/jbang/`, but this would cause it to be included in the `jbang.jar` published to Maven Central. Currently, this metadata declares configurations that may not be appropriate for projects importing JBang as a dependency. Therefore, we keep the metadata separate as a precautionary measure.

### Future Plans

Once we have a reliable, minimal set of metadata, we can reconsider moving it into the JAR itself.

## Why not Quarkus?

Quarkus would make this much easier, but JBang still needs to be buildable and target Java 8/11 in JAR mode.

In addition, Quarkus itself uses JBang, and we could end up in a murky situation of dependency alignment or circular dependencies that would not benefit either project.

Thus, for now I don't see a way to do this reliably, but would have loved to as it would have happened much sooner.

A side-effect of not using Quarkus is that this is not a highly tuned  native-image; very little if any build-time optimizatons are (for now) being left on the table.

## Why not the Gradle Native Image Plugin?

I tried it, but kept bumping into version alignment issues due to Java 8 vs Java 25, and some problems with how the native image plugin assumes environment variables and project layout exist on disk. All probably fixable issues, but given that native-image itself is now fairly easy to run and GenAI managed to make it work on the first go, simplicity wins for now.
