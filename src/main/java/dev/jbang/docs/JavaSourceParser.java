package dev.jbang.docs;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts type and member declarations from Java source code using a
 * lightweight line-by-line state machine. Does NOT extract javadoc comments
 * (descriptions are always null). Matches jbx behavior.
 */
public class JavaSourceParser {

	/**
	 * Parse Java source content and extract type declarations.
	 *
	 * @param content full source file content
	 * @return list of extracted types (without descriptions)
	 */
	public static List<DocsType> parse(String content) {
		String packageName = parsePackage(content);
		List<DocsType> types = new ArrayList<>();
		List<DocsType.DocsAnnotation> pendingAnnotations = new ArrayList<>();
		DocsType current = null;
		int braceDepth = 0;

		for (String rawLine : content.split("\\R")) {
			String line = rawLine.trim();
			// Skip empty lines, single-line comments, block comments
			if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*")
					|| line.startsWith("*")) {
				continue;
			}
			// Collect annotations
			if (line.startsWith("@")) {
				pendingAnnotations.add(parseAnnotation(line, packageName));
				continue;
			}
			if (current == null) {
				// Try to parse a type declaration
				DocsType parsed = parseTypeDeclaration(line, packageName, pendingAnnotations);
				if (parsed != null) {
					current = parsed;
					braceDepth += countChar(line, '{') - countChar(line, '}');
					pendingAnnotations = new ArrayList<>();
				} else {
					pendingAnnotations.clear();
				}
				continue;
			}
			// Inside a type — track braces and parse members
			braceDepth += countChar(line, '{') - countChar(line, '}');
			DocsMember member = parseMemberDeclaration(line, packageName, current.getName(),
					pendingAnnotations);
			if (member != null) {
				pushMember(current, member);
				pendingAnnotations = new ArrayList<>();
			} else if (!line.startsWith("@")) {
				pendingAnnotations.clear();
			}
			if (braceDepth <= 0) {
				types.add(current);
				current = null;
				pendingAnnotations.clear();
				braceDepth = 0;
			}
		}
		if (current != null) {
			types.add(current);
		}
		return types;
	}

	private static void pushMember(DocsType type, DocsMember member) {
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

	static String parsePackage(String content) {
		for (String rawLine : content.split("\\R")) {
			String line = rawLine.trim();
			if (line.startsWith("package ") && line.endsWith(";")) {
				return line.substring("package ".length(), line.length() - 1).trim();
			}
		}
		return "";
	}

	static DocsType parseTypeDeclaration(String line, String packageName,
			List<DocsType.DocsAnnotation> annotations) {
		String header = line.contains("{") ? line.substring(0, line.indexOf('{')).trim() : line;
		List<String> tokens = splitJavaWords(header);
		int kindIndex = -1;
		for (int i = 0; i < tokens.size(); i++) {
			String t = tokens.get(i);
			if (t.equals("class") || t.equals("interface") || t.equals("enum")
					|| t.equals("record")) {
				kindIndex = i;
				break;
			}
		}
		if (kindIndex < 0 || kindIndex + 1 >= tokens.size()) {
			return null;
		}
		String kindStr = tokens.get(kindIndex);
		DocsType.Kind kind;
		switch (kindStr) {
		case "class":
			kind = DocsType.Kind.CLASS;
			break;
		case "interface":
			kind = DocsType.Kind.INTERFACE;
			break;
		case "enum":
			kind = DocsType.Kind.ENUM;
			break;
		case "record":
			kind = DocsType.Kind.RECORD;
			break;
		default:
			return null;
		}
		String name = tokens.get(kindIndex + 1).replaceAll("\\(.*", "").trim();
		String visibility = parseVisibility(tokens);
		List<String> modifiers = parseModifiers(tokens);
		String qualifiedName = qualifyName(packageName, name);

		String extendsType = null;
		int extendsIdx = tokens.indexOf("extends");
		if (extendsIdx >= 0 && extendsIdx + 1 < tokens.size()) {
			extendsType = qualifyType(packageName,
					tokens.get(extendsIdx + 1).replaceAll(",$", ""));
		}

		List<String> implementsTypes = new ArrayList<>();
		int implementsIdx = tokens.indexOf("implements");
		if (implementsIdx >= 0) {
			for (int i = implementsIdx + 1; i < tokens.size(); i++) {
				String token = tokens.get(i).replaceAll(",$", "");
				if (token.isEmpty())
					continue;
				implementsTypes.add(qualifyType(packageName, token));
			}
		}

		return new DocsType()
			.setKind(kind)
			.setName(name)
			.setQualifiedName(qualifiedName)
			.setPackageName(packageName)
			.setVisibility(visibility)
			.setModifiers(modifiers)
			.setAnnotations(new ArrayList<>(annotations))
			.setExtendsType(extendsType)
			.setImplementsTypes(implementsTypes);
	}

	public static DocsMember parseMemberDeclaration(String line, String packageName,
			String typeName, List<DocsType.DocsAnnotation> annotations) {
		// Strip to declaration only: remove body and field initializers
		String declaration = line;
		if (declaration.contains("{")) {
			declaration = declaration.substring(0, declaration.indexOf('{'));
		}
		if (declaration.contains("=")) {
			declaration = declaration.substring(0, declaration.indexOf('='));
		}
		declaration = declaration.trim().replaceAll(";$", "").trim();

		if (declaration.isEmpty() || declaration.startsWith("return ")) {
			return null;
		}

		if (declaration.contains("(") && declaration.contains(")")) {
			return parseMethodOrConstructor(declaration, packageName, typeName, annotations);
		} else {
			DocsMember field = parseField(declaration, packageName, typeName, annotations);
			return field;
		}
	}

	private static DocsMember parseField(String declaration, String packageName, String typeName,
			List<DocsType.DocsAnnotation> annotations) {
		String[] parts = splitTypeAndName(declaration);
		if (parts == null)
			return null;
		String head = parts[0];
		String name = parts[1];
		String typeToken = stripLeadingModifiers(head);
		List<String> tokens = splitJavaWords(declaration);
		String visibility = parseVisibility(tokens);
		List<String> modifiers = parseModifiers(tokens);
		return new DocsMember()
			.setKind(DocsMember.Kind.FIELD)
			.setName(name)
			.setQualifiedName(packageName + "." + typeName + "." + name)
			.setVisibility(visibility)
			.setModifiers(modifiers)
			.setAnnotations(new ArrayList<>(annotations))
			.setType(qualifyType(packageName, typeToken));
	}

	private static DocsMember parseMethodOrConstructor(String declaration, String packageName,
			String typeName, List<DocsType.DocsAnnotation> annotations) {
		int open = declaration.indexOf('(');
		int close = declaration.lastIndexOf(')');
		if (open < 0 || close < 0)
			return null;

		String before = declaration.substring(0, open).trim();
		String params = declaration.substring(open + 1, close);
		String after = declaration.substring(close + 1).trim();
		// Strip leading "throws " prefix from after
		if (after.startsWith("throws ")) {
			after = after.substring("throws ".length());
		}

		String[] parts = splitTypeAndName(before);
		if (parts == null)
			return null;
		String head = parts[0];
		String name = parts[1];
		List<String> tokens = splitJavaWords(before);
		String visibility = parseVisibility(tokens);
		List<String> modifiers = parseModifiers(tokens);
		List<DocsType.DocsParameter> parameters = parseParameters(params, packageName);
		List<String> throwsList = parseThrows(after, packageName);

		if (name.equals(typeName)) {
			// Constructor
			return new DocsMember()
				.setKind(DocsMember.Kind.CONSTRUCTOR)
				.setName(name)
				.setQualifiedName(packageName + "." + typeName + "." + name)
				.setVisibility(visibility)
				.setModifiers(modifiers)
				.setAnnotations(new ArrayList<>(annotations))
				.setParameters(parameters)
				.setThrowsList(throwsList);
		}

		String returnType = stripMethodTypeParameters(stripLeadingModifiers(head));
		return new DocsMember()
			.setKind(DocsMember.Kind.METHOD)
			.setName(name)
			.setQualifiedName(packageName + "." + typeName + "." + name)
			.setVisibility(visibility)
			.setModifiers(modifiers)
			.setAnnotations(new ArrayList<>(annotations))
			.setParameters(parameters)
			.setType(qualifyType(packageName, returnType))
			.setThrowsList(throwsList);
	}

	static List<DocsType.DocsParameter> parseParameters(String params, String packageName) {
		List<DocsType.DocsParameter> result = new ArrayList<>();
		if (params.trim().isEmpty())
			return result;
		List<String> parts = splitJavaCommas(params);
		for (int i = 0; i < parts.size(); i++) {
			String param = parts.get(i).trim();
			if (param.isEmpty())
				continue;
			String[] typeAndName = splitParameterTypeAndName(param);
			String typeName, paramName;
			if (typeAndName != null) {
				typeName = typeAndName[0];
				paramName = typeAndName[1];
			} else {
				typeName = param;
				paramName = "arg" + i;
			}
			result.add(new DocsType.DocsParameter()
				.setName(paramName)
				.setType(qualifyType(packageName, typeName)));
		}
		return result;
	}

	private static List<String> parseThrows(String after, String packageName) {
		List<String> result = new ArrayList<>();
		if (after.isEmpty())
			return result;
		// after has already had "throws " stripped in caller
		// But we keep this simple: just split by comma
		for (String part : splitJavaCommas(after)) {
			String name = part.trim();
			if (!name.isEmpty()) {
				result.add(qualifyType(packageName, name));
			}
		}
		return result;
	}

	static DocsType.DocsAnnotation parseAnnotation(String line, String packageName) {
		String body = line.trim().replaceAll("^@", "");
		String name = body.split("[( \\t]")[0].trim();
		String qualifiedName;
		if (name.contains(".")) {
			qualifiedName = name;
		} else if (isJavaLangType(name)) {
			qualifiedName = "java.lang." + name;
		} else {
			qualifiedName = qualifyName(packageName, name);
		}
		return new DocsType.DocsAnnotation(qualifiedName);
	}

	// ---- Helpers ----

	private static List<String> splitJavaWords(String input) {
		List<String> tokens = new ArrayList<>();
		for (String token : input.split("\\s+")) {
			String t = token.replaceAll(",$", "");
			if (!t.isEmpty())
				tokens.add(t);
		}
		return tokens;
	}

	static List<String> splitJavaCommas(String input) {
		List<String> parts = new ArrayList<>();
		int start = 0;
		int angleDepth = 0;
		int parenDepth = 0;
		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);
			switch (ch) {
			case '<':
				angleDepth++;
				break;
			case '>':
				if (angleDepth > 0)
					angleDepth--;
				break;
			case '(':
				parenDepth++;
				break;
			case ')':
				if (parenDepth > 0)
					parenDepth--;
				break;
			case ',':
				if (angleDepth == 0 && parenDepth == 0) {
					String part = input.substring(start, i).trim();
					if (!part.isEmpty())
						parts.add(part);
					start = i + 1;
				}
				break;
			}
		}
		String last = input.substring(start).trim();
		if (!last.isEmpty())
			parts.add(last);
		return parts;
	}

	private static String parseVisibility(List<String> tokens) {
		if (tokens.contains("public"))
			return "public";
		if (tokens.contains("protected"))
			return "protected";
		if (tokens.contains("private"))
			return "private";
		return "package";
	}

	private static List<String> parseModifiers(List<String> tokens) {
		List<String> result = new ArrayList<>();
		for (String token : tokens) {
			if (isJavaModifier(token))
				result.add(token);
		}
		return result;
	}

	private static boolean isJavaModifier(String token) {
		switch (token) {
		case "public":
		case "protected":
		case "private":
		case "static":
		case "final":
		case "abstract":
		case "default":
		case "sealed":
		case "non-sealed":
		case "synchronized":
		case "native":
		case "strictfp":
			return true;
		default:
			return false;
		}
	}

	static String qualifyName(String packageName, String name) {
		if (packageName.isEmpty() || name.contains(".")) {
			return name;
		}
		return packageName + "." + name;
	}

	static String qualifyType(String packageName, String name) {
		name = normalizeJavaTypeSpacing(name.trim().replaceAll(",$", ""));
		if (name.startsWith("java.lang.")) {
			return name.substring("java.lang.".length());
		}
		String base = name.replaceAll("\\.\\.\\.", "")
			.split("[<\\[]")[0]
			.trim();
		if (name.isEmpty() || name.equals("void") || isTypeVariable(name) || isPrimitiveType(base)
				|| isJavaLangType(base) || isCommonJdkSimpleType(base)
				|| isUnqualifiedExceptionOrError(base) || name.contains(".")
				|| name.contains("<")) {
			return name;
		}
		return qualifyName(packageName, name);
	}

	private static String normalizeJavaTypeSpacing(String input) {
		StringBuilder out = new StringBuilder();
		boolean previousWasSpace = false;
		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);
			if (ch == '<' || ch == '>' || ch == '[' || ch == ']') {
				while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
					out.deleteCharAt(out.length() - 1);
				}
				out.append(ch);
				previousWasSpace = false;
			} else if (ch == ',') {
				while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
					out.deleteCharAt(out.length() - 1);
				}
				out.append(", ");
				previousWasSpace = true;
			} else if (Character.isWhitespace(ch)) {
				if (out.length() > 0 && !previousWasSpace && out.charAt(out.length() - 1) != '<'
						&& out.charAt(out.length() - 1) != '[') {
					out.append(' ');
					previousWasSpace = true;
				}
			} else {
				out.append(ch);
				previousWasSpace = false;
			}
		}
		return out.toString().trim();
	}

	private static boolean isTypeVariable(String name) {
		return name.length() == 1 && Character.isUpperCase(name.charAt(0));
	}

	private static boolean isUnqualifiedExceptionOrError(String name) {
		return !name.contains(".")
				&& (name.endsWith("Exception") || name.endsWith("Error"));
	}

	private static boolean isCommonJdkSimpleType(String name) {
		switch (name) {
		case "File":
		case "InputStream":
		case "OutputStream":
		case "Reader":
		case "Writer":
		case "DataInput":
		case "DataOutput":
		case "IOException":
		case "URL":
		case "URI":
		case "List":
		case "Set":
		case "Map":
		case "Collection":
		case "Iterable":
		case "Iterator":
		case "ConcurrentHashMap":
			return true;
		default:
			return false;
		}
	}

	private static boolean isPrimitiveType(String name) {
		switch (name) {
		case "boolean":
		case "byte":
		case "char":
		case "short":
		case "int":
		case "long":
		case "float":
		case "double":
			return true;
		default:
			return false;
		}
	}

	static boolean isJavaLangType(String name) {
		switch (name) {
		case "String":
		case "Object":
		case "Class":
		case "Integer":
		case "Long":
		case "Boolean":
		case "Double":
		case "Float":
		case "Short":
		case "Byte":
		case "Character":
		case "ClassLoader":
		case "Throwable":
		case "Exception":
		case "RuntimeException":
		case "IllegalArgumentException":
		case "Deprecated":
		case "Override":
		case "SuppressWarnings":
		case "FunctionalInterface":
			return true;
		default:
			return false;
		}
	}

	/**
	 * Split "modifiers type name" into ["modifiers type", "name"] by last
	 * whitespace.
	 */
	private static String[] splitTypeAndName(String input) {
		int splitAt = -1;
		for (int i = input.length() - 1; i >= 0; i--) {
			if (Character.isWhitespace(input.charAt(i))) {
				splitAt = i;
				break;
			}
		}
		if (splitAt < 0)
			return null;
		String head = input.substring(0, splitAt).trim();
		String name = input.substring(splitAt).trim();
		if (head.isEmpty() || name.isEmpty())
			return null;
		return new String[] { head, name };
	}

	/** Strip leading modifier tokens from a type declaration fragment. */
	private static String stripLeadingModifiers(String input) {
		String rest = input.trim();
		while (true) {
			int spaceIdx = -1;
			for (int i = 0; i < rest.length(); i++) {
				if (Character.isWhitespace(rest.charAt(i))) {
					spaceIdx = i;
					break;
				}
			}
			if (spaceIdx < 0)
				break;
			String candidate = rest.substring(0, spaceIdx);
			if (!isJavaModifier(candidate))
				break;
			rest = rest.substring(spaceIdx).trim();
		}
		return rest;
	}

	/**
	 * Strip leading generic type parameters like {@code <T extends Foo>} from a
	 * type string.
	 */
	private static String stripMethodTypeParameters(String input) {
		input = input.trim();
		if (!input.startsWith("<"))
			return input;
		int depth = 0;
		for (int i = 0; i < input.length(); i++) {
			char ch = input.charAt(i);
			if (ch == '<')
				depth++;
			else if (ch == '>') {
				depth--;
				if (depth == 0) {
					return input.substring(i + 1).trim();
				}
			}
		}
		return input;
	}

	/** Split parameter declaration into [type, name]. */
	private static String[] splitParameterTypeAndName(String param) {
		int splitAt = -1;
		for (int i = param.length() - 1; i >= 0; i--) {
			if (Character.isWhitespace(param.charAt(i))) {
				splitAt = i;
				break;
			}
		}
		if (splitAt < 0)
			return null;
		String typeName = param.substring(0, splitAt).trim();
		String name = param.substring(splitAt).trim();
		if (typeName.isEmpty() || name.isEmpty() || name.contains("."))
			return null;
		if (typeName.startsWith("final "))
			typeName = typeName.substring("final ".length()).trim();
		return new String[] { typeName, name };
	}

	private static int countChar(String input, char needle) {
		int count = 0;
		for (int i = 0; i < input.length(); i++) {
			if (input.charAt(i) == needle)
				count++;
		}
		return count;
	}
}
