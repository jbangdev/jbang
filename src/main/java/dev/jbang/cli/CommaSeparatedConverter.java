package dev.jbang.cli;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine.ITypeConverter;

public class CommaSeparatedConverter implements ITypeConverter<List<String>> {
	@Override
	public List<String> convert(final String input) throws Exception {
		return Arrays.asList(input.split(","));
	}
}
