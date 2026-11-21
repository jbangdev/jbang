package dev.jbang.spi;

import static dev.jbang.util.JsonFieldsAssert.assertJsonFieldsMatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ReflectionAccessFilter.FilterResult;

import dev.jbang.util.PathTypeAdapter;

class TestIntegrationJson {
	// Same setup as IntegrationManager, but Gson may not touch the fields of
	// IntegrationInput/IntegrationResult reflectively (Java 26+ warns about, and
	// will block, setting final fields that way).
	private final Gson writer = new GsonBuilder()
		.registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
		.create();
	private final Gson gson = writer.newBuilder()
		.addReflectionAccessFilter(type -> type == IntegrationInput.class || type == IntegrationResult.class
				? FilterResult.BLOCK_ALL
				: FilterResult.INDECISIVE)
		.create();

	@Test
	void readsIntegrationInputWithoutReflectiveAccess() {
		IntegrationInput in = new IntegrationInput("my.Integration", Paths.get("src.java"), Paths.get("classes"),
				Paths.get("pom.xml"), Collections.singletonMap("central", "https://repo"),
				Collections.singletonMap("g:a:1", Paths.get("a.jar")), Collections.singletonList("//DEPS x"), true,
				true);
		IntegrationInput out = gson.fromJson(writer.toJson(in), IntegrationInput.class);
		assertEquals("my.Integration", out.integrationClassName);
		assertEquals(Paths.get("classes"), out.classes);
		assertEquals(Paths.get("a.jar"), out.dependencies.get("g:a:1"));
		assertEquals("//DEPS x", out.comments.get(0));
		assertTrue(out.nativeRequested);
		assertTrue(out.verbose);
	}

	@Test
	void readsIntegrationResultWithoutReflectiveAccess() {
		IntegrationResult out = gson.fromJson(
				"{\"nativeImagePath\":\"app\",\"mainClass\":\"my.Main\",\"javaArgs\":[\"-ea\"]}",
				IntegrationResult.class);
		assertEquals(Paths.get("app"), out.nativeImagePath);
		assertEquals("my.Main", out.mainClass);
		assertEquals("-ea", out.javaArgs.get(0));
	}

	@Test
	void jsonFieldsMatchTheirIntegrationTypes() throws ClassNotFoundException {
		assertJsonFieldsMatch(IntegrationInput.class, gson.fieldNamingStrategy());
		assertJsonFieldsMatch(IntegrationResult.class, gson.fieldNamingStrategy());
	}
}
