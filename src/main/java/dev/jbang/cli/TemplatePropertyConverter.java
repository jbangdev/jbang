package dev.jbang.cli;

import picocli.CommandLine.ITypeConverter;

/**
 * Template property converter that is able to parse the following input:
 *
 * <ul>
 * <li>property-name</li>
 * <li>property-name:property-description</li>
 * <li>property-name:property-description:default-value</li>
 * </ul>
 *
 * Examples:
 * <ul>
 * <li>test-key</li>
 * <li>"test-key:This is a description for the property"</li>
 * <li>"test-key:This is a description for the property:2.11"</li>
 * </ul>
 *
 */
public class TemplatePropertyConverter implements ITypeConverter<TemplateAdd.TemplatePropertyInput> {
	@Override
	public TemplateAdd.TemplatePropertyInput convert(final String input) throws Exception {
		String[] split = input.split(":");
		TemplateAdd.TemplatePropertyInput templatePropertyInput = new TemplateAdd.TemplatePropertyInput();
		if (split.length > 0) {
			templatePropertyInput.setKey(split[0]);
		}
		if (split.length > 1) {
			templatePropertyInput.setDescription(split[1]);
		}
		if (split.length > 2) {
			templatePropertyInput.setDefaultValue(split[2]);
		}
		return templatePropertyInput;
	}
}
