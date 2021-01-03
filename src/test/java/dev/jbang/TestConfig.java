package dev.jbang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class TestConfig extends BaseTest {

	@Test
	public void testWriteReadConfig() throws IOException {
		ConfigUtil.Config cfg = ConfigUtil.defaults;
		Path cfgFile = jbangTempDir.getRoot().toPath().resolve(Settings.CONFIG_JSON);
		ConfigUtil.writeConfig(cfgFile, cfg);
		ConfigUtil.Config cfg2 = ConfigUtil.readConfig(cfgFile);
		assertEquals(cfg, cfg2);
	}

}
