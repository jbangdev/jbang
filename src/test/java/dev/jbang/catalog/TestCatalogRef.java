package dev.jbang.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.ReflectionAccessFilter.FilterResult;

class TestCatalogRef {
	@ParameterizedTest
	@ValueSource(strings = { "catalog-ref", "catalogRef" })
	void readsCatalogReferenceWithoutReflectiveAccess(String referenceKey) {
		Gson gson = new GsonBuilder()
			.addReflectionAccessFilter(type -> type == CatalogRef.class ? FilterResult.BLOCK_ALL
					: FilterResult.INDECISIVE)
			.create();
		String json = "{\"catalogs\":{\"example\":{\"" + referenceKey
				+ "\":\"./child/jbang-catalog.json\",\"description\":\"Local child\",\"import\":false}}}";

		CatalogRef ref = gson.fromJson(json, Catalog.class).catalogs.get("example");

		assertEquals("./child/jbang-catalog.json", ref.catalogRef);
		assertEquals("Local child", ref.description);
		assertEquals(Boolean.FALSE, ref.importItems);
	}

	@Test
	void writesCatalogReferenceUsingCanonicalFieldNames() {
		CatalogRef ref = new CatalogRef("./child/jbang-catalog.json", "Local child", false, Catalog.empty());

		JsonObject json = new Gson().toJsonTree(ref).getAsJsonObject();

		assertEquals("./child/jbang-catalog.json", json.get("catalog-ref").getAsString());
		assertEquals("Local child", json.get("description").getAsString());
		assertFalse(json.get("import").getAsBoolean());
		assertFalse(json.has("catalogRef"));
		assertFalse(json.has("catalog"));
	}
}
