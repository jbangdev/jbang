package dev.jbang.docs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts type information from JAR bytecode using {@code javap}. Used as a
 * fallback when no javadoc JAR is available.
 */
public class JavapExtractor {

	/**
	 * Extract types from a JAR using javap.
	 *
	 * @param jarPath path to the JAR file
	 * @return list of types (signatures only, no descriptions)
	 */
	public static List<DocsType> extract(Path jarPath) throws IOException {
		List<String> classNames = listClassNames(jarPath);
		List<DocsType> types = new ArrayList<>();
		for (String className : classNames) {
			String output = runJavap(jarPath, className);
			if (output != null) {
				DocsType type = parseJavapOutput(output);
				if (type != null) {
					types.add(type);
				}
			}
		}
		return types;
	}

	/**
	 * List dotted class names from a JAR, skipping inner classes, module-info, and
	 * package-info.
	 */
	static List<String> listClassNames(Path jarPath) throws IOException {
		List<String> names = new ArrayList<>();
		try (ZipFile zip = new ZipFile(jarPath.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.endsWith(".class"))
					continue;
				if (name.contains("$"))
					continue;
				// Strip the path prefix added by some JARs
				String simple = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
				if (simple.equals("module-info.class") || simple.equals("package-info.class"))
					continue;
				// Convert path to dotted name
				names.add(name.replaceAll("\\.class$", "").replace('/', '.'));
			}
		}
		return names;
	}

	/**
	 * Run {@code javap -p -classpath <jar> <className>} and return stdout, or null
	 * on failure.
	 */
	private static String runJavap(Path jarPath, String className) {
		try {
			ProcessBuilder pb = new ProcessBuilder("javap", "-p", "-classpath",
					jarPath.toAbsolutePath().toString(), className);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			try (InputStream is = process.getInputStream()) {
				String output = new String(readAllBytes(is), StandardCharsets.UTF_8);
				process.waitFor();
				return output;
			}
		} catch (IOException | InterruptedException e) {
			return null;
		}
	}

	/**
	 * Parse javap output (result of {@code javap -p}) and return a DocsType, or
	 * null if the output doesn't contain a recognisable type declaration.
	 *
	 * <p>
	 * This method is package-private so it can be tested directly.
	 */
	static DocsType parseJavapOutput(String output) {
		// Collect signature lines
		List<String> sigLines = new ArrayList<>();
		for (String raw : output.split("\\R")) {
			String line = raw.trim();
			if (line.isEmpty())
				continue;
			// Keep type-declaration lines and member lines
			boolean isTypeLine = line.contains(" class ") || line.contains(" interface ")
					|| line.contains(" enum ") || line.contains(" record ");
			boolean isMemberLine = (line.endsWith(";") || line.endsWith("{"))
					&& !line.startsWith("descriptor:")
					&& !line.startsWith("flags:")
					&& !line.startsWith("#")
					&& !line.startsWith("Classfile ")
					&& !line.startsWith("Last modified ")
					&& !line.startsWith("SHA-256 ")
					&& !line.startsWith("Compiled from ");
			if (isTypeLine || isMemberLine) {
				sigLines.add(line);
			}
		}

		// Find the type declaration line
		String typeLine = null;
		for (String line : sigLines) {
			if (line.contains(" class ") || line.contains(" interface ") || line.contains(" enum ")
					|| line.contains(" record ")) {
				typeLine = line;
				break;
			}
		}
		if (typeLine == null)
			return null;

		// Parse type header (strip trailing '{')
		String header = typeLine.endsWith("{") ? typeLine.substring(0, typeLine.length() - 1).trim()
				: typeLine;
		DocsType type = JavaSourceParser.parseTypeDeclaration(header, "", Collections.emptyList());
		if (type == null)
			return null;

		// Derive package from qualified name in the header (javap uses FQNs)
		String qualifiedName = deriveQualifiedName(header);
		if (qualifiedName != null) {
			String pkg = qualifiedName.contains(".")
					? qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))
					: "";
			String simpleName = qualifiedName.contains(".")
					? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
					: qualifiedName;
			// Strip generic type params from simple name
			int lt = simpleName.indexOf('<');
			if (lt >= 0)
				simpleName = simpleName.substring(0, lt);
			type.setQualifiedName(qualifiedName.contains("<")
					? qualifiedName.substring(0, qualifiedName.indexOf('<'))
					: qualifiedName)
				.setName(simpleName)
				.setPackageName(pkg);
		}

		// Parse parameter names from verbose output (only present with -v)
		Map<String, List<String>> paramNames = javapParameterNames(output);

		// Parse member lines
		String typeHeader = typeLine;
		for (String line : sigLines) {
			if (line.equals(typeHeader) || line.endsWith("{"))
				continue;
			String decl = line.replaceAll(";$", "").trim();
			if (decl.isEmpty())
				continue;

			String pkg = type.getPackageName();
			String simpleName = type.getName();
			// Javap uses FQNs; strip package prefix so parseMemberDeclaration
			// can match constructor names (e.g. com.example.Widget → Widget)
			String normalizedDecl = stripPackagePrefix(decl, pkg);
			DocsMember member = JavaSourceParser.parseMemberDeclaration(normalizedDecl, pkg, simpleName,
					Collections.emptyList());
			if (member == null)
				continue;

			// Apply parameter names from verbose output if available
			if ((member.getKind() == DocsMember.Kind.METHOD
					|| member.getKind() == DocsMember.Kind.CONSTRUCTOR)
					&& member.getName() != null) {
				List<String> names = paramNames.get(member.getName());
				if (names != null) {
					applyParameterNames(member, names);
				}
			}

			switch (member.getKind()) {
			case FIELD:
				type.getFields().add(member);
				break;
			case CONSTRUCTOR:
				type.getConstructors().add(member);
				break;
			case METHOD:
				type.getMethods().add(member);
				break;
			}
		}

		return type;
	}

	/**
	 * Extract the fully-qualified class name from a javap type declaration line.
	 * Example: {@code "public class com.example.Widget<T> extends Base {"} →
	 * {@code "com.example.Widget<T>"}
	 */
	private static String deriveQualifiedName(String header) {
		String[] keywords = { "class ", "interface ", "enum ", "record " };
		for (String kw : keywords) {
			int idx = header.indexOf(kw);
			if (idx >= 0) {
				String rest = header.substring(idx + kw.length()).trim();
				// Take up to 'extends', 'implements', or end-of-string
				for (String stop : new String[] { " extends ", " implements ", " permits " }) {
					int s = rest.indexOf(stop);
					if (s >= 0)
						rest = rest.substring(0, s);
				}
				return rest.trim();
			}
		}
		return null;
	}

	/**
	 * Extract parameter names from {@code javap -v} MethodParameters sections. Maps
	 * method-name → ordered list of parameter names.
	 */
	static Map<String, List<String>> javapParameterNames(String output) {
		String[] lines = output.split("\\R");
		Map<String, List<String>> result = new HashMap<>();
		String currentMethod = null;
		int i = 0;
		while (i < lines.length) {
			String line = lines[i].trim();
			if (line.endsWith(";") && line.contains("(") && !line.startsWith("descriptor:")
					&& !line.startsWith("#")) {
				// Extract method name from the declaration
				String before = line.substring(0, line.indexOf('(')).trim();
				String[] words = before.split("\\s+");
				if (words.length > 0) {
					currentMethod = words[words.length - 1];
				}
			} else if (line.equals("MethodParameters:") && currentMethod != null) {
				List<String> names = new ArrayList<>();
				i += 2; // skip header row
				while (i < lines.length) {
					String candidate = lines[i].trim();
					if (candidate.isEmpty() || candidate.endsWith(":") || candidate.contains(";")
							|| candidate.equals("}"))
						break;
					String[] parts = candidate.split("\\s+");
					if (parts.length > 0)
						names.add(parts[0]);
					i++;
				}
				result.put(currentMethod, names);
				currentMethod = null;
				continue;
			}
			i++;
		}
		return result;
	}

	/**
	 * Replace occurrences of {@code pkg.ClassName} (where ClassName contains no
	 * dot) with just {@code ClassName} so that javap's FQN-based output is
	 * simplified for the generic member parser.
	 */
	static String stripPackagePrefix(String decl, String pkg) {
		if (pkg == null || pkg.isEmpty())
			return decl;
		return decl.replace(pkg + ".", "");
	}

	private static byte[] readAllBytes(InputStream in) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int n;
		while ((n = in.read(chunk)) != -1) {
			buf.write(chunk, 0, n);
		}
		return buf.toByteArray();
	}

	private static void applyParameterNames(DocsMember member, List<String> names) {
		List<DocsType.DocsParameter> params = member.getParameters();
		for (int i = 0; i < Math.min(params.size(), names.size()); i++) {
			params.get(i).setName(names.get(i));
		}
	}
}
