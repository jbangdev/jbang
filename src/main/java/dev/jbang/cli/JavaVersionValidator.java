package dev.jbang.cli;

import org.aesh.command.validator.OptionValidator;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.command.validator.ValidatorInvocation;

/**
 * Validates that the --java option value is a number optionally followed by a
 * plus sign (e.g. "11", "17+").
 */
public class JavaVersionValidator implements OptionValidator<ValidatorInvocation<String, ?>> {

	@Override
	public void validate(ValidatorInvocation<String, ?> invocation) throws OptionValidatorException {
		String value = invocation.getValue();
		if (value != null && !value.matches("\\d+[+]?")) {
			throw new OptionValidatorException(
					String.format(
							"Invalid value for '--java': '%s' should be a number optionally followed by a plus sign",
							value));
		}
	}
}
