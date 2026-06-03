package dev.jbang.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TestJavaSourceParser {

	@Test
	void simpleClassWithFieldsAndMethods() {
		String src = "package com.example;\n"
				+ "public class Widget {\n"
				+ "    public int count;\n"
				+ "    public Widget() {}\n"
				+ "    public void reset() {}\n"
				+ "    public int getCount() { return count; }\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		DocsType type = types.get(0);
		assertThat(type.getName()).isEqualTo("Widget");
		assertThat(type.getQualifiedName()).isEqualTo("com.example.Widget");
		assertThat(type.getPackageName()).isEqualTo("com.example");
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.CLASS);
		assertThat(type.getVisibility()).isEqualTo("public");

		assertThat(type.getFields()).hasSize(1);
		assertThat(type.getFields().get(0).getName()).isEqualTo("count");
		assertThat(type.getFields().get(0).getType()).isEqualTo("int");

		assertThat(type.getConstructors()).hasSize(1);
		assertThat(type.getConstructors().get(0).getName()).isEqualTo("Widget");
		assertThat(type.getConstructors().get(0).getKind()).isEqualTo(DocsMember.Kind.CONSTRUCTOR);

		assertThat(type.getMethods()).hasSize(2);
		assertThat(type.getMethods().get(0).getName()).isEqualTo("reset");
		assertThat(type.getMethods().get(0).getType()).isEqualTo("void");
		assertThat(type.getMethods().get(1).getName()).isEqualTo("getCount");
		assertThat(type.getMethods().get(1).getType()).isEqualTo("int");
	}

	@Test
	void interfaceDeclaration() {
		String src = "package com.example;\n"
				+ "public interface Runnable {\n"
				+ "    void run();\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		assertThat(types.get(0).getKind()).isEqualTo(DocsType.Kind.INTERFACE);
		assertThat(types.get(0).getName()).isEqualTo("Runnable");
		assertThat(types.get(0).getMethods()).hasSize(1);
		assertThat(types.get(0).getMethods().get(0).getName()).isEqualTo("run");
	}

	@Test
	void enumDeclaration() {
		String src = "package com.example;\n"
				+ "public enum Color {\n"
				+ "    RED, GREEN, BLUE;\n"
				+ "    public boolean isWarm() { return this == RED; }\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		assertThat(types.get(0).getKind()).isEqualTo(DocsType.Kind.ENUM);
		assertThat(types.get(0).getName()).isEqualTo("Color");
	}

	@Test
	void recordDeclaration() {
		String src = "package com.example;\n"
				+ "public record Point(int x, int y) {\n"
				+ "    public double distance() { return Math.sqrt(x * x + y * y); }\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		assertThat(types.get(0).getKind()).isEqualTo(DocsType.Kind.RECORD);
		assertThat(types.get(0).getName()).isEqualTo("Point");
	}

	@Test
	void annotationsOnTypesAndMembers() {
		String src = "package com.example;\n"
				+ "@Deprecated\n"
				+ "public class OldWidget {\n"
				+ "    @Override\n"
				+ "    public String toString() { return \"old\"; }\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		DocsType type = types.get(0);
		assertThat(type.getAnnotations()).hasSize(1);
		assertThat(type.getAnnotations().get(0).getQualifiedName()).isEqualTo("java.lang.Deprecated");

		assertThat(type.getMethods()).hasSize(1);
		assertThat(type.getMethods().get(0).getAnnotations()).hasSize(1);
		assertThat(type.getMethods().get(0).getAnnotations().get(0).getQualifiedName())
			.isEqualTo(
					"java.lang.Override");
	}

	@Test
	void extendsAndImplements() {
		String src = "package com.example;\n"
				+ "public class MyList extends AbstractList implements Serializable, Cloneable {\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		DocsType type = types.get(0);
		assertThat(type.getExtendsType()).isEqualTo("com.example.AbstractList");
		assertThat(type.getImplementsTypes()).containsExactly(
				"com.example.Serializable", "com.example.Cloneable");
	}

	@Test
	void constructorVsMethod() {
		String src = "package com.example;\n"
				+ "public class Foo {\n"
				+ "    public Foo(int x) {}\n"
				+ "    public String bar(int x) { return \"\"; }\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		assertThat(types.get(0).getConstructors()).hasSize(1);
		assertThat(types.get(0).getConstructors().get(0).getKind())
			.isEqualTo(
					DocsMember.Kind.CONSTRUCTOR);
		assertThat(types.get(0).getMethods()).hasSize(1);
		assertThat(types.get(0).getMethods().get(0).getName()).isEqualTo("bar");
		assertThat(types.get(0).getMethods().get(0).getType()).isEqualTo("String");
	}

	@Test
	void genericReturnType() {
		String src = "package com.example;\n"
				+ "public class Repo {\n"
				+ "    public List<String> findAll() { return null; }\n"
				+ "    public <T> T find(Class<T> cls) { return null; }\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		DocsMember findAll = types.get(0).getMethods().get(0);
		assertThat(findAll.getName()).isEqualTo("findAll");
		assertThat(findAll.getType()).isEqualTo("List<String>");

		DocsMember find = types.get(0).getMethods().get(1);
		assertThat(find.getName()).isEqualTo("find");
		// Generic type param <T> should be stripped, leaving "T"
		assertThat(find.getType()).isEqualTo("T");
	}

	@Test
	void throwsClause() {
		String src = "package com.example;\n"
				+ "public class Loader {\n"
				+ "    public void load() throws IOException, IllegalStateException {}\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		DocsMember load = types.get(0).getMethods().get(0);
		assertThat(load.getThrowsList()).containsExactly("IOException", "IllegalStateException");
	}

	@Test
	void noPackageDeclaration() {
		String src = "public class Bare {\n"
				+ "    public void go() {}\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		assertThat(types.get(0).getPackageName()).isEmpty();
		assertThat(types.get(0).getQualifiedName()).isEqualTo("Bare");
	}

	@Test
	void multipleClassesInOneFile() {
		String src = "package com.example;\n"
				+ "public class Alpha {\n"
				+ "    public void a() {}\n"
				+ "}\n"
				+ "class Beta {\n"
				+ "    public void b() {}\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(2);
		assertThat(types.get(0).getName()).isEqualTo("Alpha");
		assertThat(types.get(1).getName()).isEqualTo("Beta");
		assertThat(types.get(1).getVisibility()).isEqualTo("package");
	}

	@Test
	void commentsAreIgnored() {
		String src = "package com.example;\n"
				+ "// This is a line comment\n"
				+ "/* Block comment */\n"
				+ "/**\n"
				+ " * Javadoc comment\n"
				+ " */\n"
				+ "public class Documented {\n"
				+ "    // field comment\n"
				+ "    public int value;\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		assertThat(types).hasSize(1);
		assertThat(types.get(0).getDescription()).isNull();
		assertThat(types.get(0).getFields()).hasSize(1);
	}

	@Test
	void methodParameters() {
		String src = "package com.example;\n"
				+ "public class Service {\n"
				+ "    public String process(String input, int count) { return null; }\n"
				+ "}\n";

		List<DocsType> types = JavaSourceParser.parse(src);
		DocsMember method = types.get(0).getMethods().get(0);
		assertThat(method.getParameters()).hasSize(2);
		assertThat(method.getParameters().get(0).getName()).isEqualTo("input");
		assertThat(method.getParameters().get(0).getType()).isEqualTo("String");
		assertThat(method.getParameters().get(1).getName()).isEqualTo("count");
		assertThat(method.getParameters().get(1).getType()).isEqualTo("int");
	}
}
