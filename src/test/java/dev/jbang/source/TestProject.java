package dev.jbang.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.jbang.BaseTest;
import dev.jbang.resources.ResourceRef;
import dev.jbang.source.sources.JavaSource;

public class TestProject extends BaseTest {

	@Test
	void testCDS() {
		Source source = new JavaSource(ResourceRef.forLiteral("//CDS\nclass m { }"), null);
		Source source2 = new JavaSource(ResourceRef.forLiteral("class m { }"), null);

		Project prj = Project.builder().build(source);
		Project prj2 = Project.builder().build(source2);

		assertTrue(prj.enableCDS());
		assertFalse(prj2.enableCDS());
	}
}
