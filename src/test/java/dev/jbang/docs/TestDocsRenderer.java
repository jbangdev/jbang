package dev.jbang.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

class TestDocsRenderer {

	private DocsType buildWidget() {
		DocsType.DocsParameter nameParam = new DocsType.DocsParameter()
			.setName("name")
			.setType("String")
			.setDescription("the widget name");

		DocsType.DocsParameter prefixParam = new DocsType.DocsParameter()
			.setName("prefix")
			.setType("String")
			.setDescription("the prefix to filter by");

		DocsType.DocsParameter limitParam = new DocsType.DocsParameter()
			.setName("limit")
			.setType("int");

		DocsMember kindField = new DocsMember()
			.setKind(DocsMember.Kind.FIELD)
			.setName("KIND")
			.setType("String")
			.setDescription("the kind constant");

		DocsMember ctor = new DocsMember()
			.setKind(DocsMember.Kind.CONSTRUCTOR)
			.setName("Widget")
			.setParameters(Collections.singletonList(nameParam));

		DocsMember namesMethod = new DocsMember()
			.setKind(DocsMember.Kind.METHOD)
			.setName("names")
			.setType("List<String>")
			.setParameters(Arrays.asList(prefixParam, limitParam))
			.setDescription("returns matching names")
			.setReturnDescription("matched names");

		return new DocsType()
			.setKind(DocsType.Kind.CLASS)
			.setName("Widget")
			.setQualifiedName("dev.example.Widget")
			.setDescription("A widget class.")
			.setFields(Collections.singletonList(kindField))
			.setConstructors(Collections.singletonList(ctor))
			.setMethods(Collections.singletonList(namesMethod));
	}

	@Test
	void markdownBasicStructure() {
		DocsType type = buildWidget();
		String md = DocsRenderer.toMarkdown("Widget", null, Collections.singletonList(type));

		assertThat(md).startsWith("# Widget\n\n");
		assertThat(md).contains("## `dev.example.Widget`\n\n");
		assertThat(md).contains("Kind: class\n\n");
		assertThat(md).contains("A widget class.\n\n");
		assertThat(md).contains("### Fields\n\n");
		assertThat(md).contains("- `String KIND` — the kind constant");
		assertThat(md).contains("### Constructors\n\n");
		assertThat(md).contains("- `Widget(String name)`");
		assertThat(md).contains("### Methods\n\n");
		assertThat(md).contains("- `List<String> names(String prefix, int limit)` — returns matching names");
		assertThat(md).contains("  - `prefix`: the prefix to filter by");
		assertThat(md).contains("  - Returns: matched names");
	}

	@Test
	void markdownWithArtifactCoordinate() {
		DocsType type = buildWidget();
		String md = DocsRenderer.toMarkdown("guava", "com.google.guava:guava:33.0.0-jre",
				Collections.singletonList(type));

		assertThat(md).contains("Artifact: `com.google.guava:guava:33.0.0-jre`\n\n");
	}

	@Test
	void markdownWithoutArtifactOmitsArtifactLine() {
		DocsType type = buildWidget();
		String md = DocsRenderer.toMarkdown("Widget", null, Collections.singletonList(type));
		assertThat(md).doesNotContain("Artifact:");
	}

	@Test
	void markdownEmptyTypes() {
		String md = DocsRenderer.toMarkdown("Nothing", null, Collections.emptyList());
		assertThat(md).isEqualTo("# Nothing\n\n");
	}

	@Test
	void markdownMethodWithoutDescription() {
		DocsMember method = new DocsMember()
			.setKind(DocsMember.Kind.METHOD)
			.setName("run")
			.setType("void");

		DocsType type = new DocsType()
			.setKind(DocsType.Kind.CLASS)
			.setName("Runner")
			.setQualifiedName("Runner")
			.setMethods(Collections.singletonList(method));

		String md = DocsRenderer.toMarkdown("Runner", null, Collections.singletonList(type));
		assertThat(md).contains("- `void run()`");
		assertThat(md).doesNotContain(" — ");
		assertThat(md).doesNotContain("Returns:");
	}

	@Test
	void jsonStructure() {
		DocsType type = buildWidget();
		String json = DocsRenderer.toJson("Widget.java", "com.example:widget:1.0", Collections.singletonList(type),
				"source");

		Gson gson = new Gson();
		@SuppressWarnings("unchecked")
		Map<String, Object> root = gson.fromJson(json, Map.class);

		assertThat(root.get("schema")).isEqualTo("https://jbx.telegraphic.dev/schemas/jbx-docs/v1.json");
		assertThat(root.get("target")).isEqualTo("Widget.java");
		assertThat(root.get("types")).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<Object> types = (List<Object>) root.get("types");
		assertThat(types).hasSize(1);
		assertThat(root.get("generatedFrom")).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> genFrom = (Map<String, Object>) root.get("generatedFrom");
		assertThat(genFrom.get("source")).isEqualTo("source");
		assertThat(root.get("artifact")).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> artifact = (Map<String, Object>) root.get("artifact");
		assertThat(artifact.get("group")).isEqualTo("com.example");
		assertThat(artifact.get("id")).isEqualTo("widget");
		assertThat(artifact.get("version")).isEqualTo("1.0");
	}

	@Test
	void jsonEmptyTypes() {
		String json = DocsRenderer.toJson("Nothing.java", null, Collections.emptyList(), "source");
		Gson gson = new Gson();
		@SuppressWarnings("unchecked")
		Map<String, Object> root = gson.fromJson(json, Map.class);
		assertThat(root.get("schema")).isEqualTo("https://jbx.telegraphic.dev/schemas/jbx-docs/v1.json");
		assertThat(root.get("types")).isInstanceOf(List.class);
		@SuppressWarnings("unchecked")
		List<Object> types = (List<Object>) root.get("types");
		assertThat(types).isEmpty();
		assertThat(root.containsKey("artifact")).isFalse();
	}

	@Test
	void markdownExamplesSection() {
		DocsType type = new DocsType()
			.setKind(DocsType.Kind.CLASS)
			.setName("Widget")
			.setQualifiedName("Widget")
			.setExamples(Arrays.asList("Widget w = new Widget(\"hi\");", "w.run();"));

		String md = DocsRenderer.toMarkdown("Widget", null, Collections.singletonList(type));
		assertThat(md).contains("### Examples\n\n");
		assertThat(md).contains("```java\nWidget w = new Widget(\"hi\");\n```");
	}
}
