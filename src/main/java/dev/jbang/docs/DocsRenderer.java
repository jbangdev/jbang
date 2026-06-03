package dev.jbang.docs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Renders DocsType data as terminal Markdown or structured JSON.
 */
public class DocsRenderer {

	private static final Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

	/**
	 * Render as Markdown for terminal display.
	 */
	public static String toMarkdown(String title, String artifact, List<DocsType> types) {
		StringBuilder out = new StringBuilder();
		out.append("# ").append(title).append("\n\n");
		if (artifact != null && !artifact.isEmpty()) {
			out.append("Artifact: `").append(artifact).append("`\n\n");
		}
		for (DocsType type : types) {
			if (type.getQualifiedName() != null) {
				out.append("## `").append(type.getQualifiedName()).append("`\n\n");
			}
			if (type.getKind() != null) {
				out.append("Kind: ").append(type.getKind().name().toLowerCase()).append("\n\n");
			}
			if (type.getDescription() != null && !type.getDescription().isEmpty()) {
				out.append(type.getDescription()).append("\n\n");
			}
			renderExamplesSection(out, type.getExamples());
			renderMemberSection(out, "Fields", type.getFields(), DocsRenderer::renderFieldSignature);
			renderMemberSection(out, "Constructors", type.getConstructors(),
					DocsRenderer::renderConstructorSignature);
			renderMemberSection(out, "Methods", type.getMethods(), DocsRenderer::renderMethodSignature);
		}
		return out.toString();
	}

	/**
	 * Render as JSON with schema.
	 */
	public static String toJson(String target, String artifact, List<DocsType> types, String generatedFrom) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("schema", "https://jbang.dev/schemas/jbang-docs/v1.json");
		root.put("target", target);
		if (artifact != null && !artifact.isEmpty()) {
			root.put("artifact", parseArtifactCoordinate(artifact));
		}
		root.put("types", types);
		Map<String, Object> generatedFromMap = new LinkedHashMap<>();
		generatedFromMap.put("source", generatedFrom != null ? generatedFrom : "source");
		generatedFromMap.put("jbangVersion", getJbangVersion());
		root.put("generatedFrom", generatedFromMap);
		return GSON.toJson(root) + "\n";
	}

	private static Map<String, Object> parseArtifactCoordinate(String coordinate) {
		Map<String, Object> map = new LinkedHashMap<>();
		String[] parts = coordinate.split(":");
		if (parts.length >= 3) {
			map.put("group", parts[0]);
			map.put("id", parts[1]);
			map.put("version", parts[2]);
			map.put("coordinate", parts[0] + ":" + parts[1] + ":" + parts[2]);
		} else if (parts.length == 2) {
			map.put("group", parts[0]);
			map.put("id", parts[1]);
			map.put("coordinate", coordinate);
		} else {
			map.put("coordinate", coordinate);
		}
		return map;
	}

	private static String getJbangVersion() {
		String version = DocsRenderer.class.getPackage() != null
				? DocsRenderer.class.getPackage().getImplementationVersion()
				: null;
		return version != null ? version : "dev";
	}

	private static void renderExamplesSection(StringBuilder out, List<String> examples) {
		if (examples == null || examples.isEmpty()) {
			return;
		}
		List<String> nonEmpty = examples.stream()
			.filter(e -> e != null && !e.trim().isEmpty())
			.collect(Collectors.toList());
		if (nonEmpty.isEmpty()) {
			return;
		}
		out.append("### Examples\n\n");
		for (String example : nonEmpty) {
			out.append("```java\n");
			out.append(example.trim());
			out.append("\n```\n\n");
		}
	}

	@FunctionalInterface
	private interface SignatureRenderer {
		String render(DocsMember member);
	}

	private static void renderMemberSection(StringBuilder out, String title, List<DocsMember> members,
			SignatureRenderer renderer) {
		if (members == null || members.isEmpty()) {
			return;
		}
		// Check if any member produces a signature
		boolean anySignature = members.stream().anyMatch(m -> renderer.render(m) != null);
		if (!anySignature) {
			return;
		}
		out.append("### ").append(title).append("\n\n");
		for (DocsMember member : members) {
			String signature = renderer.render(member);
			if (signature == null) {
				continue;
			}
			out.append("- `").append(signature).append("`");
			if (member.getDescription() != null && !member.getDescription().trim().isEmpty()) {
				out.append(" — ").append(inlineMarkdown(member.getDescription()));
			}
			out.append("\n");
			renderParameterDescriptions(out, member);
			if (member.getReturnDescription() != null && !member.getReturnDescription().trim().isEmpty()) {
				out.append("  - Returns: ").append(inlineMarkdown(member.getReturnDescription())).append("\n");
			}
		}
		out.append("\n");
	}

	private static void renderParameterDescriptions(StringBuilder out, DocsMember member) {
		if (member.getParameters() == null) {
			return;
		}
		for (DocsType.DocsParameter param : member.getParameters()) {
			if (param.getDescription() == null || param.getDescription().trim().isEmpty()) {
				continue;
			}
			String name = param.getName() != null ? param.getName() : "parameter";
			out.append("  - `")
				.append(name)
				.append("`: ")
				.append(inlineMarkdown(param.getDescription()))
				.append("\n");
		}
	}

	private static String renderFieldSignature(DocsMember field) {
		if (field.getName() == null || field.getType() == null) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		String mods = memberModifiers(field);
		if (!mods.isEmpty()) {
			parts.add(mods);
		}
		parts.add(field.getType());
		parts.add(field.getName());
		return String.join(" ", parts);
	}

	private static String renderConstructorSignature(DocsMember ctor) {
		if (ctor.getName() == null) {
			return null;
		}
		return renderCallableSignature(null, ctor.getName(), ctor);
	}

	private static String renderMethodSignature(DocsMember method) {
		if (method.getName() == null) {
			return null;
		}
		String returnType = method.getType() != null ? method.getType() : "void";
		return renderCallableSignature(returnType, method.getName(), method);
	}

	private static String renderCallableSignature(String returnType, String name, DocsMember member) {
		List<String> head = new ArrayList<>();
		if (returnType != null) {
			head.add(returnType);
		}
		head.add(name + "(" + renderParameters(member) + ")");
		StringBuilder sig = new StringBuilder(String.join(" ", head));
		if (member.getThrowsList() != null && !member.getThrowsList().isEmpty()) {
			sig.append(" throws ").append(String.join(", ", member.getThrowsList()));
		}
		return sig.toString();
	}

	private static String renderParameters(DocsMember member) {
		if (member.getParameters() == null || member.getParameters().isEmpty()) {
			return "";
		}
		return member.getParameters()
			.stream()
			.filter(p -> p.getType() != null && p.getName() != null)
			.map(p -> p.getType() + " " + p.getName())
			.collect(Collectors.joining(", "));
	}

	private static String memberModifiers(DocsMember member) {
		if (member.getModifiers() == null || member.getModifiers().isEmpty()) {
			return "";
		}
		return String.join(" ", member.getModifiers());
	}

	private static String inlineMarkdown(String markdown) {
		if (markdown == null) {
			return "";
		}
		return String.join(" ", markdown.split("\\s+"));
	}
}
