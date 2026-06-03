package dev.jbang.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TestJavadocHtmlParser {

	// -------------------------------------------------------------------------
	// isJavadocTypePage
	// -------------------------------------------------------------------------

	@Test
	void typePage_acceptsNormalClassPage() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("com/example/Widget.html")).isTrue();
	}

	@Test
	void typePage_acceptsTopLevelPage() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("MyClass.html")).isTrue();
	}

	@Test
	void typePage_rejectsPackageSummary() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("com/example/package-summary.html"))
			.isFalse();
	}

	@Test
	void typePage_rejectsModuleSummary() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("com/example/module-summary.html"))
			.isFalse();
	}

	@Test
	void typePage_rejectsIndex() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("index.html")).isFalse();
	}

	@Test
	void typePage_rejectsLowercaseFile() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("com/example/widget.html")).isFalse();
	}

	@Test
	void typePage_rejectsHyphenatedFile() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("com/example/all-classes.html")).isFalse();
	}

	@Test
	void typePage_rejectsNonHtml() {
		assertThat(JavadocHtmlParser.isJavadocTypePage("com/example/Widget.txt")).isFalse();
	}

	// -------------------------------------------------------------------------
	// stripHtmlTags / normalizeDocText
	// -------------------------------------------------------------------------

	@Test
	void stripHtmlTags_removesTagsAndUnescapes() {
		String html = "<b>Hello</b> &amp; <i>World</i>";
		assertThat(JavadocHtmlParser.stripHtmlTags(html)).isEqualTo(" Hello  &  World ");
	}

	@Test
	void normalizeDocText_collapsesWhitespace() {
		assertThat(JavadocHtmlParser.normalizeDocText("  foo   bar\n  baz  ")).isEqualTo(
				"foo bar baz");
	}

	// -------------------------------------------------------------------------
	// extractTypeDescription
	// -------------------------------------------------------------------------

	@Test
	void extractTypeDescription_modernHtml() {
		String html = "<section class=\"class-description\">"
				+ "<div class=\"block\">A simple widget.</div>"
				+ "</section>";
		assertThat(JavadocHtmlParser.extractTypeDescription(html)).isEqualTo("A simple widget.");
	}

	@Test
	void extractTypeDescription_interfaceDescription() {
		String html = "<section class=\"interface-description\">"
				+ "<div class=\"block\">Defines widget behavior.</div>"
				+ "</section>";
		assertThat(JavadocHtmlParser.extractTypeDescription(html)).isEqualTo(
				"Defines widget behavior.");
	}

	@Test
	void extractTypeDescription_returnsNullWhenAbsent() {
		assertThat(JavadocHtmlParser.extractTypeDescription("<html></html>")).isNull();
	}

	// -------------------------------------------------------------------------
	// extractMemberDocs
	// -------------------------------------------------------------------------

	@Test
	void extractMemberDocs_parsesDetailSection() {
		String html = "<section class=\"detail\">"
				+ "<div class=\"member-signature\">public int getCount()</div>"
				+ "<div class=\"block\">Returns the count.</div>"
				+ "</section>";
		List<JavadocHtmlParser.MemberDoc> docs = JavadocHtmlParser.extractMemberDocs(html);
		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).signature).isEqualTo("public int getCount()");
		assertThat(docs.get(0).description).isEqualTo("Returns the count.");
	}

	@Test
	void extractMemberDocs_skipsDetailWithNoSignature() {
		String html = "<section class=\"detail\"><div class=\"block\">No sig.</div></section>";
		assertThat(JavadocHtmlParser.extractMemberDocs(html)).isEmpty();
	}

	// -------------------------------------------------------------------------
	// extractParameterDescriptions / extractReturnDescription
	// -------------------------------------------------------------------------

	@Test
	void extractParameterDescriptions() {
		String detail = "<dl><dt>Parameters:</dt>"
				+ "<dd><code>name</code> - The name of the widget.</dd>"
				+ "<dd><code>count</code> - Initial count value.</dd>"
				+ "</dl>";
		List<String[]> params = JavadocHtmlParser.extractParameterDescriptions(detail);
		assertThat(params).hasSize(2);
		assertThat(params.get(0)[0]).isEqualTo("name");
		assertThat(params.get(0)[1]).isEqualTo("The name of the widget.");
		assertThat(params.get(1)[0]).isEqualTo("count");
		assertThat(params.get(1)[1]).isEqualTo("Initial count value.");
	}

	@Test
	void extractReturnDescription() {
		String detail = "<dl><dt>Returns:</dt><dd>the current count</dd></dl>";
		assertThat(JavadocHtmlParser.extractReturnDescription(detail)).isEqualTo(
				"the current count");
	}

	@Test
	void extractReturnDescription_returnsNullWhenAbsent() {
		assertThat(JavadocHtmlParser.extractReturnDescription("<dl></dl>")).isNull();
	}

	// -------------------------------------------------------------------------
	// parseTypePage integration
	// -------------------------------------------------------------------------

	@Test
	void parseTypePage_classKind() {
		String html = "<html><body>"
				+ "<div class=\"header\"><h1>Class Widget</h1></div>"
				+ "<section class=\"class-description\">"
				+ "<div class=\"block\">A widget.</div>"
				+ "</section>"
				+ "<section class=\"detail\">"
				+ "<div class=\"member-signature\">public int getCount()</div>"
				+ "<div class=\"block\">Returns count.</div>"
				+ "<dl><dt>Returns:</dt><dd>the count</dd></dl>"
				+ "</section>"
				+ "</body></html>";
		DocsType type = JavadocHtmlParser.parseTypePage("com/example/Widget.html", html);
		assertThat(type).isNotNull();
		assertThat(type.getQualifiedName()).isEqualTo("com.example.Widget");
		assertThat(type.getName()).isEqualTo("Widget");
		assertThat(type.getPackageName()).isEqualTo("com.example");
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.CLASS);
		assertThat(type.getDescription()).isEqualTo("A widget.");
		assertThat(type.getMethods()).hasSize(1);
		DocsMember method = type.getMethods().get(0);
		assertThat(method.getName()).isEqualTo("getCount");
		assertThat(method.getReturnDescription()).isEqualTo("the count");
	}

	@Test
	void parseTypePage_interfaceKind() {
		String html = "<html><body>"
				+ "<div class=\"type-signature\"><span>public interface Runnable</span></div>"
				+ "</body></html>";
		DocsType type = JavadocHtmlParser.parseTypePage("com/example/Runnable.html", html);
		assertThat(type).isNotNull();
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.INTERFACE);
	}

	@Test
	void parseTypePage_enumKind() {
		String html = "<html><body>"
				+ "<div class=\"type-signature\"><span>public enum Color</span></div>"
				+ "</body></html>";
		DocsType type = JavadocHtmlParser.parseTypePage("com/example/Color.html", html);
		assertThat(type).isNotNull();
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.ENUM);
	}

	@Test
	void parseTypePage_recordKind() {
		String html = "<html><body>"
				+ "<div class=\"type-signature\"><span>public record Point(int x, int y)</span></div>"
				+ "</body></html>";
		DocsType type = JavadocHtmlParser.parseTypePage("com/example/Point.html", html);
		assertThat(type).isNotNull();
		assertThat(type.getKind()).isEqualTo(DocsType.Kind.RECORD);
	}
}
