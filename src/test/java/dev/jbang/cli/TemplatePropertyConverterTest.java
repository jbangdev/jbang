package dev.jbang.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.jbang.catalog.TemplateProperty;

class TemplatePropertyConverterTest {

	@Test
	void testParseKeyOnly() {
		Map<String, TemplateProperty> result = Template.TemplateAdd.parseProperties(
				Collections.singletonList("test-key"));
		TemplateProperty prop = result.get("test-key");
		assertNull(prop.getDescription());
		assertNull(prop.getDefaultValue());
	}

	@Test
	void testParseKeyWithDescription() {
		Map<String, TemplateProperty> result = Template.TemplateAdd.parseProperties(
				Collections.singletonList("test-key=This is a description for the property"));
		TemplateProperty prop = result.get("test-key");
		assertEquals("This is a description for the property", prop.getDescription());
		assertNull(prop.getDefaultValue());
	}

	@Test
	void testParseKeyWithDescriptionAndDefault() {
		Map<String, TemplateProperty> result = Template.TemplateAdd.parseProperties(
				Collections.singletonList("test-key=This is a description for the property::2.11"));
		TemplateProperty prop = result.get("test-key");
		assertEquals("This is a description for the property", prop.getDescription());
		assertEquals("2.11", prop.getDefaultValue());
	}

	@Test
	void testParseKeyWithDefaultOnly() {
		Map<String, TemplateProperty> result = Template.TemplateAdd.parseProperties(
				Collections.singletonList("test-key=::2.11"));
		TemplateProperty prop = result.get("test-key");
		assertNull(prop.getDescription());
		assertEquals("2.11", prop.getDefaultValue());
	}

	@Test
	void testParseMultipleProperties() {
		Map<String, TemplateProperty> result = Template.TemplateAdd.parseProperties(
				Arrays.asList("key1=desc1::val1", "key2=desc2"));
		assertEquals("desc1", result.get("key1").getDescription());
		assertEquals("val1", result.get("key1").getDefaultValue());
		assertEquals("desc2", result.get("key2").getDescription());
		assertNull(result.get("key2").getDefaultValue());
	}

	@Test
	void testParseNullReturnsEmptyMap() {
		Map<String, TemplateProperty> result = Template.TemplateAdd.parseProperties(null);
		assertEquals(0, result.size());
	}
}
