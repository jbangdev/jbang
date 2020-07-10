package dev.jbang;

public class TestUtil {
	public static void clearSettingsCaches() {
		Settings.catalogCache.clear();
		Settings.catalogInfo = null;
	}
}
