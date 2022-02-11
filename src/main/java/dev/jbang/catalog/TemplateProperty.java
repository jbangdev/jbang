package dev.jbang.catalog;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;

/**
 * Class representing a property in the JBang catalog templates.
 */
public class TemplateProperty {

	private String description;
	@SerializedName(value = "default")
	private String defaultValue;

	public TemplateProperty(String description, String defaultValue) {
		this.description = description;
		this.defaultValue = defaultValue;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		TemplateProperty that = (TemplateProperty) o;
		return Objects.equals(description, that.description) && Objects.equals(defaultValue, that.defaultValue);
	}

	@Override
	public int hashCode() {
		return Objects.hash(description, defaultValue);
	}

	@Override
	public String toString() {
		return "TemplateProperty{" +
				"description='" + description + '\'' +
				", defaultValue='" + defaultValue + '\'' +
				'}';
	}
}
