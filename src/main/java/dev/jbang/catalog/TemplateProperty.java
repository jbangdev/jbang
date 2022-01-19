package dev.jbang.catalog;

import com.google.gson.annotations.SerializedName;

public class TemplateProperty {

	private String description;
	@SerializedName(value = "default")
	private String defaultValue;

	public TemplateProperty(String description, String defaultValue) {
		this.description = description;
		this.defaultValue = defaultValue;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}
}
