package dev.jbang;

import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TestTrustEdit {

	@Test
	void testAddTrust(@TempDir File storage) throws IOException {

		File settings = new File(storage, "sources.json");

		TrustedSources td = new TrustedSources(new String[0]);

		assertThat(settings, not(anExistingFile()));

		td.add(Arrays.asList("a"), settings);

		assertThat(settings, anExistingFile());

		TrustedSources sources = TrustedSources.load(settings.toPath());

		assertThat(sources.getTrustedSources(), arrayWithSize(1));
		assertThat(sources.getTrustedSources(), arrayContaining("a"));

	}

	@Test
	void testMultiAddTrust(@TempDir File storage) throws IOException {

		File settings = new File(storage, "sources.json");

		TrustedSources td = new TrustedSources(new String[0]);

		assertThat(settings, not(anExistingFile()));

		td.add(Arrays.asList("a", "b"), settings);

		assertThat(settings, anExistingFile());

		TrustedSources trustedSources = TrustedSources.load(settings.toPath());

		assertThat(trustedSources.getTrustedSources(), arrayContaining("a", "b"));

		trustedSources.add(Arrays.asList("c"), settings);

		assertThat(trustedSources.getTrustedSources(), arrayContaining("a", "b", "c"));

	}

}
