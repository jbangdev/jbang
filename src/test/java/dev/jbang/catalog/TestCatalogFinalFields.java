package dev.jbang.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.ReflectionAccessFilter.FilterResult;
import com.google.gson.annotations.SerializedName;

class TestCatalogFinalFields {
	@Test
	void readsAllCatalogTypesWithoutReflectiveAccessToTheirFinalFields() {
		Gson gson = Catalog.gsonBuilder()
			.addReflectionAccessFilter(type -> type == Catalog.class || type == CatalogRef.class || type == Alias.class
					|| type == Alias.JavaAgent.class || type == Template.class
							? FilterResult.BLOCK_ALL
							: FilterResult.INDECISIVE)
			.create();
		String json = "{\"baseRef\":\"src\",\"description\":\"catalog\","
				+ "\"catalogs\":{\"child\":{\"catalogRef\":\"child.json\"}},"
				+ "\"aliases\":{\"app\":{\"scriptRef\":\"app.java\",\"java-options\":[\"-ea\"],"
				+ "\"java-agents\":[{\"agent-ref\":\"agent.jar\",\"options\":\"debug\"}],"
				+ "\"manifest-options\":{\"key\":\"value\"}}},"
				+ "\"templates\":{\"hello\":{\"file-refs\":{\"Hello.java\":\"hello.java\"},"
				+ "\"properties\":{\"name\":{\"default\":\"world\"}}}}}";
		Catalog catalog = gson.fromJson(json, Catalog.class);
		assertEquals("src", catalog.baseRef);
		assertEquals("catalog", catalog.description);
		assertEquals("child.json", catalog.catalogs.get("child").catalogRef);
		Alias alias = catalog.aliases.get("app");
		assertEquals("app.java", alias.scriptRef);
		assertEquals("-ea", alias.runtimeOptions.get(0));
		assertEquals("agent.jar", alias.javaAgents.get(0).agentRef);
		assertEquals("debug", alias.javaAgents.get(0).options);
		assertEquals("value", alias.manifestOptions.get("key"));
		assertEquals("hello.java", catalog.templates.get("hello").fileRefs.get("Hello.java"));
		assertEquals("world", catalog.templates.get("hello").properties.get("name").getDefaultValue());
		JsonObject serialized = Catalog.gsonBuilder().create().toJsonTree(catalog).getAsJsonObject();
		assertEquals("src", serialized.get("base-ref").getAsString());
		assertFalse(serialized.has("baseRef"));
	}

	@Test
	void acceptsNullFieldsAndMissingFields() {
		Gson gson = Catalog.gsonBuilder().create();
		Alias alias = gson.fromJson("{\"script-ref\":null,\"java-agents\":[{\"agent-ref\":null}]}", Alias.class);
		assertNull(alias.scriptRef);
		assertNull(alias.javaAgents.get(0).agentRef);
		assertNull(alias.javaAgents.get(0).options);
		assertNull(gson.fromJson("null", Template.class));
	}

	@Test
	void jsonFieldsMatchTheirCatalogTypes() throws ClassNotFoundException {
		FieldNamingStrategy naming = Catalog.gsonBuilder().create().fieldNamingStrategy();
		Class<?>[] types = { Catalog.class, CatalogRef.class, Alias.class, Alias.JavaAgent.class, Template.class };
		for (Class<?> type : types) {
			Class<?> jsonFields = Class.forName(type.getName() + "$JsonFields");
			assertEquals(jsonNames(type, naming), jsonNames(jsonFields, naming),
					"JSON names and types of " + jsonFields.getName() + " must match " + type.getName());
		}
	}

	private static Map<String, Type> jsonNames(Class<?> type, FieldNamingStrategy naming) {
		Map<String, Type> names = new TreeMap<>();
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || f.isSynthetic()) {
					continue;
				}
				SerializedName sn = f.getAnnotation(SerializedName.class);
				names.put(sn != null ? sn.value() : naming.translateName(f), f.getGenericType());
				if (sn != null) {
					for (String alternate : sn.alternate()) {
						names.put(alternate, f.getGenericType());
					}
				}
			}
		}
		return names;
	}
}
