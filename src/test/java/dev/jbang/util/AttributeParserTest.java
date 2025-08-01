package dev.jbang.util;

import static dev.jbang.util.AttributeParser.parseAttributeList;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class AttributeParserTest {

	Map<String, List<String>> parse(String str) {
		return parse(str, "");
	}

	Map<String, List<String>> parse(String str, String def) {
		return parseAttributeList(str, def);
	}

	@Test
	public void testEmpty() {
		assertThat(parse("")).isEmpty();
	}

	@Test
	public void testParseBasic() {
		assertThat(parse("basic"))
			.isNotEmpty()
			.contains(entry("", of("basic")));
	}

	@Test
	public void testParseBoolean() {
		assertThat(parse("%transitive"))
			.isNotEmpty()
			.contains(entry("transitive", of("true")));
	}

	@Test
	public void testList() {
		assertThat(parse("basic,run"))
			.isNotEmpty()
			.contains(entry("", of("basic", "run")));
	}

	@Test
	public void testMultipleBooleans() {
		assertThat(parse("%transitive%import"))
			.isNotEmpty()
			.contains(entry("transitive", of("true")), entry("import", of("true")));
	}

	@Test
	public void testNamedAttributes() {
		assertThat(parse("my=basic,run"))
			.isNotEmpty()
			.hasSize(2)
			.contains(entry("my", of("basic")), entry("", of("run")));
	}

	@Test
	public void testCombinedMultipleBooleans() {
		assertThat(parse("basic,run,scope=herewego,%transitive%import", "scope"))
			.isNotEmpty()
			.hasSize(3)
			.contains(
					entry("transitive", of("true")),
					entry("import", of("true")),
					entry("scope", of("basic", "run", "herewego")));
	}

	@Test
	public void testQuotes() {
		assertThat(parse("'basic,run' ,scope=\"here 'we' \\\"go\\\" \",%transitive%import", "scope"))
			.isNotEmpty()
			.hasSize(3)
			.contains(
					entry("transitive", of("true")),
					entry("import", of("true")),
					entry("scope", of("basic,run", "here 'we' \"go\" ")));
	}

}
