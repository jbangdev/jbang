package dev.jbang.docs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestJavapExtractor {

	@Test
	void simplePublicClass() {
		String output = "Compiled from \"Widget.java\"\n"
				+ "public class com.example.Widget {\n"
				+ "  public int count;\n"
				+ "  public com.example.Widget();\n"
				+ "  public void reset();\n"
				+ "  public int getCount();\n"
				+ "}\n";
		DocsType type = JavapExtractor.parseJavapOutput(output);
		assertThat(type).isNotNull();
		assertThat(type.getName()).isEqualTo("Widget");
		assertThat(type.getQualifiedName()).isEqualTo("com.example.Widget");
		assertThat(type.getPackageName()).isEqualTo("com.example");
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.CLASS);
		assertThat(type.getVisibility()).isEqualTo("public");
		assertThat(type.getFields()).hasSize(1);
		assertThat(type.getFields().get(0).getName()).isEqualTo("count");
		assertThat(type.getConstructors()).hasSize(1);
		assertThat(type.getMethods()).hasSize(2);
	}

	@Test
	void publicInterface() {
		String output = "Compiled from \"Runnable.java\"\n"
				+ "public interface com.example.MyRunnable {\n"
				+ "  public abstract void run();\n"
				+ "}\n";
		DocsType type = JavapExtractor.parseJavapOutput(output);
		assertThat(type).isNotNull();
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.INTERFACE);
		assertThat(type.getName()).isEqualTo("MyRunnable");
		assertThat(type.getMethods()).hasSize(1);
		assertThat(type.getMethods().get(0).getName()).isEqualTo("run");
	}

	@Test
	void enumType() {
		String output = "public final class com.example.Color extends java.lang.Enum<com.example.Color> {\n"
				+ "  public static final com.example.Color RED;\n"
				+ "  public static final com.example.Color GREEN;\n"
				+ "  public static com.example.Color[] values();\n"
				+ "  public static com.example.Color valueOf(java.lang.String);\n"
				+ "}\n";
		DocsType type = JavapExtractor.parseJavapOutput(output);
		assertThat(type).isNotNull();
		assertThat(type.getName()).isEqualTo("Color");
		// javap shows enums as "class" with Enum supertype
		assertThat(type.getFields()).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void abstractClassWithExtendsAndImplements() {
		String output = "public abstract class com.example.Base extends java.lang.Object implements java.io.Serializable {\n"
				+ "  public abstract void doWork();\n"
				+ "  public final int id;\n"
				+ "}\n";
		DocsType type = JavapExtractor.parseJavapOutput(output);
		assertThat(type).isNotNull();
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.CLASS);
		assertThat(type.getModifiers()).contains("abstract");
		assertThat(type.getMethods()).hasSize(1);
		assertThat(type.getMethods().get(0).getName()).isEqualTo("doWork");
	}

	@Test
	void genericClass() {
		String output = "public class com.example.Box<T> {\n"
				+ "  public java.lang.Object value;\n"
				+ "  public com.example.Box();\n"
				+ "  public java.lang.Object get();\n"
				+ "  public void set(java.lang.Object);\n"
				+ "}\n";
		DocsType type = JavapExtractor.parseJavapOutput(output);
		assertThat(type).isNotNull();
		assertThat(type.getName()).isEqualTo("Box");
		assertThat(type.getQualifiedName()).isEqualTo("com.example.Box");
		assertThat(type.getMethods()).hasSize(2);
	}

	@Test
	void nullForGarbage() {
		DocsType type = JavapExtractor.parseJavapOutput("not javap output at all");
		assertThat(type).isNull();
	}

	@Test
	void javapParameterNamesExtracted() {
		String verboseOutput = "public class com.example.Foo {\n"
				+ "  public void greet(java.lang.String,int);\n"
				+ "    descriptor: (Ljava/lang/String;I)V\n"
				+ "    flags: (0x0001) ACC_PUBLIC\n"
				+ "    MethodParameters:\n"
				+ "      Name                           Flags\n"
				+ "      name\n"
				+ "      age\n"
				+ "}\n";
		var names = JavapExtractor.javapParameterNames(verboseOutput);
		assertThat(names).containsKey("greet");
		assertThat(names.get("greet")).containsExactly("name", "age");
	}
}
