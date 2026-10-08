package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TestRequestedVersionComparator {

	private final JavaUtil.RequestedVersionComparator cmp = new JavaUtil.RequestedVersionComparator();

	@Test
	void testNullHandling() {
		assertEquals(0, cmp.compare(null, null));
		assertTrue(cmp.compare(null, "17+") > 0);
		assertTrue(cmp.compare("17+", null) < 0);
	}

	@Test
	void testSameVersion() {
		assertEquals(0, cmp.compare("17+", "17+"));
		assertEquals(0, cmp.compare("17", "17"));
	}

	@Test
	void testDifferentVersions() {
		assertTrue(cmp.compare("11+", "17+") < 0, "11+ should be less than 17+");
		assertTrue(cmp.compare("17+", "11+") > 0, "17+ should be greater than 11+");
		assertTrue(cmp.compare("24+", "25+") < 0, "24+ should be less than 25+");
		assertTrue(cmp.compare("25+", "24+") > 0, "25+ should be greater than 24+");
	}

	@Test
	void testExactVsOpen() {
		// Same version: exact (no +) sorts before open (+)
		assertTrue(cmp.compare("17", "17+") < 0, "17 should be less than 17+");
		assertTrue(cmp.compare("17+", "17") > 0, "17+ should be greater than 17");
	}

	@Test
	void testDifferentVersionsExactVsOpen() {
		assertTrue(cmp.compare("11", "25+") < 0, "11 should be less than 25+");
		assertTrue(cmp.compare("25+", "11") > 0, "25+ should be greater than 11");
	}

	@Test
	void testMaxSelectsHighest() {
		java.util.List<String> versions = java.util.Arrays.asList("11+", "17+", "25+", "21+");
		String max = versions.stream().max(cmp).orElse(null);
		assertEquals("25+", max, "max() should select the highest version");
	}
}
