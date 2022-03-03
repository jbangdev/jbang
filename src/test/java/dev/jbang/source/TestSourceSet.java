package dev.jbang.source;

import dev.jbang.BaseTest;
import dev.jbang.source.sources.JavaSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSourceSet extends BaseTest {

    @Test
    void testCDS() {
        Source source = new JavaSource("//CDS\nclass m { }", null);
        Source source2 = new JavaSource("class m { }", null);

        SourceSet ss = SourceSet.forSource(source);
        SourceSet ss2 = SourceSet.forSource(source2);

        assertTrue(ss.enableCDS());
        assertFalse(ss2.enableCDS());
    }
}
