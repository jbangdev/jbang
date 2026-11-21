package dev.jbang.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.annotations.SerializedName;

/**
 * Checks that a type's nested {@code JsonFields} helper (used to deserialize
 * without mutating final fields) accepts the same JSON names and types as the
 * type itself.
 */
public final class JsonFieldsAssert {
	private JsonFieldsAssert() {
	}

	public static void assertJsonFieldsMatch(Class<?> type, FieldNamingStrategy naming)
			throws ClassNotFoundException {
		Class<?> jsonFields = Class.forName(type.getName() + "$JsonFields");
		assertEquals(jsonNames(type, naming), jsonNames(jsonFields, naming),
				"JSON names and types of " + jsonFields.getName() + " must match " + type.getName());
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
