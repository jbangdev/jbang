package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;

class TemplatePropertyConverterTest {

	private TemplatePropertyConverter underTest = new TemplatePropertyConverter();

	@ParameterizedTest
	@ArgumentsSource(TemplatePropertyArgumentProvider.class)
	void convertShouldReturnProperPropertyObjects(TemplatePropertyConverterTestCase testCase) throws Exception {
		TemplateAdd.TemplatePropertyInput templatePropertyInput = underTest.convert(testCase.getInput());
		assertEquals(testCase.getExpected(), templatePropertyInput);
	}

}

class TemplatePropertyArgumentProvider implements ArgumentsProvider {

	@Override
	public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context)
			throws Exception {
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