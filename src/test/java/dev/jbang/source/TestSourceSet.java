package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.source.sources.JavaSource;

public class TestSourceSet extends BaseTest {

	@Test
	void testCDS() {
		Source source = new JavaSource("//CDS\nclass m { }", null);
		Source source2 = new JavaSource("class m { }", null);

		SourceSet ss = source.createSourceSet();
		SourceSet ss2 = source2.createSourceSet();

		assertTrue(ss.enableCDS());
		assertFalse(ss2.enableCDS());
	}
}
