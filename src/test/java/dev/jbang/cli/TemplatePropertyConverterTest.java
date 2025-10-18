package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TemplatePropertyConverterTest {

	private final TemplatePropertyConverter underTest = new TemplatePropertyConverter();

	@ParameterizedTest
	@MethodSource("propertyConverterTestCases")
	void convertShouldReturnProperPropertyObjects(TemplatePropertyConverterTestCase testCase) throws Exception {
		TemplateAdd.TemplatePropertyInput templatePropertyInput = underTest.convert(testCase.getInput());
		assertEquals(testCase.getExpected(), templatePropertyInput);
	}

	static Stream<Arguments> propertyConverterTestCases() {
		return Stream.of(
				Arguments.of(new TemplatePropertyConverterTestCase("test-key",
						new TemplateAdd.TemplatePropertyInput("test-key", null, null))),
				Arguments.of(new TemplatePropertyConverterTestCase("test-key::",
						new TemplateAdd.TemplatePropertyInput("test-key", null, null))),
				Arguments.of(new TemplatePropertyConverterTestCase("test-key:This is a description for the property",
						new TemplateAdd.TemplatePropertyInput("test-key", "This is a description for the property",
								null))),
				Arguments.of(new TemplatePropertyConverterTestCase("test-key:This is a description for the property:",
						new TemplateAdd.TemplatePropertyInput("test-key", "This is a description for the property",
								null))),
				Arguments.of(
						new TemplatePropertyConverterTestCase("test-key:This is a description for the property:2.11",
								new TemplateAdd.TemplatePropertyInput("test-key",
										"This is a description for the property", "2.11"))),
				Arguments.of(new TemplatePropertyConverterTestCase("test-key::2.11",
						new TemplateAdd.TemplatePropertyInput("test-key", null, "2.11"))));
	}
}

class TemplatePropertyConverterTestCase {
	private final String input;
	private final TemplateAdd.TemplatePropertyInput expected;

	public TemplatePropertyConverterTestCase(String input, TemplateAdd.TemplatePropertyInput expected) {
		this.input = input;
		this.expected = expected;
	}

	public String getInput() {
		return input;
	}

	public TemplateAdd.TemplatePropertyInput getExpected() {
		return expected;
	}
}