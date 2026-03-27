package dev.jbang.source.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class KeyValueTest {

	@Test
	void testSimpleKeyValue() {
		KeyValue kv = KeyValue.of("key=value");
		assertEquals("key", kv.getKey());
		assertEquals("value", kv.getValue());
	}

	@Test
	void testKeyOnly() {
		KeyValue kv = KeyValue.of("key");
		assertEquals("key", kv.getKey());
		assertNull(kv.getValue());
	}

	@Test
	void testValueWithEquals() {
		// This is the fix for Add-Reads=module1=module2
		KeyValue kv = KeyValue.of("Add-Reads=java.base=java.logging");
		assertEquals("Add-Reads", kv.getKey());
		assertEquals("java.base=java.logging", kv.getValue());
	}

	@Test
	void testValueWithMultipleEquals() {
		KeyValue kv = KeyValue.of("key=value1=value2=value3");
		assertEquals("key", kv.getKey());
		assertEquals("value1=value2=value3", kv.getValue());
	}

	@Test
	void testEmptyValue() {
		KeyValue kv = KeyValue.of("key=");
		assertEquals("key", kv.getKey());
		assertEquals("", kv.getValue());
	}

	@Test
	void testWhitespaceInKey() {
		KeyValue kv = KeyValue.of("my key=value");
		assertEquals("my key", kv.getKey());
		assertEquals("value", kv.getValue());
	}

	@Test
	void testWhitespaceInValue() {
		KeyValue kv = KeyValue.of("key=my value");
		assertEquals("key", kv.getKey());
		assertEquals("my value", kv.getValue());
	}

	@Test
	void testModuleReadsFormat() {
		// Real-world example from Add-Reads manifest attribute
		KeyValue kv = KeyValue.of("Add-Reads=module.a=module.b");
		assertEquals("Add-Reads", kv.getKey());
		assertEquals("module.a=module.b", kv.getValue());
	}
}
