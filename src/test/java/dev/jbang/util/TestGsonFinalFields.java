package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.jbang.catalog.Alias;
import dev.jbang.catalog.Catalog;
import dev.jbang.catalog.CatalogRef;
import dev.jbang.catalog.Template;
import dev.jbang.catalog.TemplateProperty;
import dev.jbang.spi.IntegrationInput;
import dev.jbang.spi.IntegrationResult;

class TestGsonFinalFields {
	// Gson sets fields reflectively. Java 26+ warns about, and will block, doing
	// that to final fields, so classes Gson reads into keep their fields private
	// and non-final and expose them through accessor methods instead. See #2671.
	@ParameterizedTest
	@ValueSource(classes = { Catalog.class, CatalogRef.class, Alias.class, Alias.JavaAgent.class, Template.class,
			TemplateProperty.class, IntegrationInput.class, IntegrationResult.class })
	void fieldsSetByGsonAreNotFinal(Class<?> type) {
		for (Field f : type.getDeclaredFields()) {
			int mod = f.getModifiers();
			if (!Modifier.isStatic(mod) && !Modifier.isTransient(mod)) {
				assertFalse(Modifier.isFinal(mod), type.getSimpleName() + "." + f.getName() + " must not be final");
			}
		}
	}
}
