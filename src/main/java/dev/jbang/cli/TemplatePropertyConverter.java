package dev.jbang.cli;

import org.apache.commons.lang3.StringUtils;

import picocli.CommandLine.ITypeConverter;

/**
 * Template property converter that is able to parse the following input:
 *
 * <ul>
 * <li>property-name</li>
 * <li>property-name:property-description</li>
 * <li>property-name:property-description:default-value</li>
 * <li>property-name::default-value</li>
 * </ul>
 *
 * Examples:
 * <ul>
 * <li>test-key</li>
 * <li>"test-key:This is a description for the property"</li>
 * <li>"test-key:This is a description for the property:2.11"</li>
 * <li>"test-key::2.11"</li>
 * </ul>
 *
 */
public class TemplatePropertyConverter implements ITypeConverter<TemplateAdd.TemplatePropertyInput> {
	@Override
	public TemplateAdd.TemplatePropertyInput convert(final String input) throws Exception {
		String[] propertyParts = input.split(":");
		TemplateAdd.TemplatePropertyInput templatePropertyInput = new TemplateAdd.TemplatePropertyInput();
		if (propertyParts.length > 0 && StringUtils.isNotBlank(propertyParts[0])) {
			templatePropertyInput.setKey(propertyParts[0]);
		}
		if (propertyParts.length > 1 && StringUtils.isNotBlank(propertyParts[1])) {
			templatePropertyInput.setDescription(propertyParts[1]);
		}
		if (propertyParts.length > 2 && StringUtils.isNotBlank(propertyParts[2])) {
			templatePropertyInput.setDefaultValue(propertyParts[2]);
		}
		return templatePropertyInput;
	}
}
