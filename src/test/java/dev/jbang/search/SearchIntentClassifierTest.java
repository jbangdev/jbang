package dev.jbang.search;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import dev.jbang.search.SearchIntentClassifier.Intent;
import dev.jbang.search.SearchIntentClassifier.IntentType;

class SearchIntentClassifierTest {

	@Test
	void testExactGav() {
		Intent i = SearchIntentClassifier.classify("com.fasterxml.jackson.core:jackson-databind:2.20.0");
		assertEquals(IntentType.EXACT_GAV, i.type);
		assertEquals("com.fasterxml.jackson.core:jackson-databind:2.20.0", i.centralQuery);
	}

	@Test
	void testGroupArtifact() {
		Intent i = SearchIntentClassifier.classify("com.fasterxml.jackson.core:jackson-databind");
		assertEquals(IntentType.GROUP_ARTIFACT, i.type);
		assertEquals("com.fasterxml.jackson.core:jackson-databind", i.centralQuery);
	}

	@Test
	void testFullyQualifiedClassName() {
		Intent i = SearchIntentClassifier.classify("com.fasterxml.jackson.databind.ObjectMapper");
		assertEquals(IntentType.FULLY_QUALIFIED_CLASS, i.type);
		assertEquals("fc:com.fasterxml.jackson.databind.ObjectMapper", i.centralQuery);
	}

	@Test
	void testSimpleClassName() {
		Intent i = SearchIntentClassifier.classify("ObjectMapper");
		assertEquals(IntentType.SIMPLE_CLASS, i.type);
		assertEquals("c:ObjectMapper", i.centralQuery);
	}

	@Test
	void testJavaImport() {
		Intent i = SearchIntentClassifier.classify("import com.fasterxml.jackson.databind.ObjectMapper;");
		assertEquals(IntentType.JAVA_IMPORT, i.type);
		assertEquals("fc:com.fasterxml.jackson.databind.ObjectMapper", i.centralQuery);
	}

	@Test
	void testJavaImportWithoutSemicolon() {
		Intent i = SearchIntentClassifier.classify("import com.fasterxml.jackson.databind.ObjectMapper");
		assertEquals(IntentType.JAVA_IMPORT, i.type);
		assertEquals("fc:com.fasterxml.jackson.databind.ObjectMapper", i.centralQuery);
	}

	@Test
	void testStaticImport() {
		Intent i = SearchIntentClassifier.classify("import static org.junit.jupiter.api.Assertions.assertEquals;");
		assertEquals(IntentType.JAVA_IMPORT, i.type);
		assertEquals("fc:org.junit.jupiter.api.Assertions.assertEquals", i.centralQuery);
	}

	@Test
	void testWildcardImport() {
		Intent i = SearchIntentClassifier.classify("import java.util.*;");
		assertEquals(IntentType.JAVA_IMPORT, i.type);
		assertEquals("java.util", i.centralQuery);
		assertEquals("java.util", i.localQuery);
	}

	@Test
	void testPackageError() {
		Intent i = SearchIntentClassifier.classify("error: package org.jsoup does not exist");
		assertEquals(IntentType.PACKAGE_ERROR, i.type);
		assertEquals("org.jsoup", i.centralQuery);
	}

	@Test
	void testPackageErrorWithoutPrefix() {
		Intent i = SearchIntentClassifier.classify("package org.junit.jupiter.api does not exist");
		assertEquals(IntentType.PACKAGE_ERROR, i.type);
		assertEquals("org.junit.jupiter.api", i.centralQuery);
	}

	@Test
	void testSymbolError() {
		Intent i = SearchIntentClassifier.classify("error: cannot find symbol class ObjectMapper");
		assertEquals(IntentType.SYMBOL_ERROR, i.type);
		assertEquals("c:ObjectMapper", i.centralQuery);
	}

	@Test
	void testExplicitClassPrefix() {
		Intent i = SearchIntentClassifier.classify("c:ObjectMapper");
		assertEquals(IntentType.KEYWORD, i.type);
		assertEquals("c:ObjectMapper", i.centralQuery);
	}

	@Test
	void testExplicitFqcnPrefix() {
		Intent i = SearchIntentClassifier.classify("fc:com.fasterxml.jackson.databind.ObjectMapper");
		assertEquals(IntentType.KEYWORD, i.type);
		assertEquals("fc:com.fasterxml.jackson.databind.ObjectMapper", i.centralQuery);
	}

	@Test
	void testKeyword() {
		Intent i = SearchIntentClassifier.classify("jackson databind");
		assertEquals(IntentType.KEYWORD, i.type);
		assertEquals("jackson databind", i.centralQuery);
	}

	@Test
	void testSingleKeyword() {
		Intent i = SearchIntentClassifier.classify("jsoup");
		assertEquals(IntentType.KEYWORD, i.type);
		assertEquals("jsoup", i.centralQuery);
	}

	@Test
	void testEmpty() {
		Intent i = SearchIntentClassifier.classify("");
		assertEquals(IntentType.KEYWORD, i.type);
		assertEquals("", i.centralQuery);
	}

	@Test
	void testNull() {
		Intent i = SearchIntentClassifier.classify(null);
		assertEquals(IntentType.KEYWORD, i.type);
		assertEquals("", i.centralQuery);
	}

	@Test
	void testShortClassName() {
		// 2 chars — too short for simple class heuristic
		Intent i = SearchIntentClassifier.classify("DB");
		assertEquals(IntentType.KEYWORD, i.type);
	}

	@Test
	void testThreeCharClassName() {
		Intent i = SearchIntentClassifier.classify("App");
		assertEquals(IntentType.SIMPLE_CLASS, i.type);
		assertEquals("c:App", i.centralQuery);
	}
}
