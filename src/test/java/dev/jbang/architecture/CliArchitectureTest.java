package dev.jbang.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.aesh.command.option.Argument;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Ensures that Aesh CLI annotations ({@code @Option}, {@code @Argument},
 * {@code @Arguments}, {@code @OptionList}, {@code @OptionGroup}) never target
 * fields with Java primitive types.
 * <p>
 * Primitive types have implicit defaults ({@code false}, {@code 0}, etc.) which
 * make it impossible for {@link dev.jbang.cli.JBangDefaultValueProvider} to
 * distinguish "user didn't pass the flag" from "user explicitly set the
 * default". This breaks config-file overriding: a config value can never be
 * negated from the CLI because the primitive default shadows it.
 * <p>
 * Use the corresponding boxed type instead ({@code Boolean} instead of
 * {@code boolean}, {@code Integer} instead of {@code int}, etc.) so the field
 * is {@code null} when unset, allowing the default-value provider and
 * {@code afterParse()} logic to work correctly.
 *
 * @see dev.jbang.cli.JBangDefaultValueProvider
 * @see <a href="https://github.com/jbangdev/jbang/issues/2504">Issue 2504</a>
 */
@AnalyzeClasses(packages = "dev.jbang")
class CliArchitectureTest {

	private static final Set<Class<?>> PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(
			boolean.class, byte.class, char.class, short.class,
			int.class, long.class, float.class, double.class));

	// --- @Option fields must not use primitive types ---

	@ArchTest
	static final ArchRule option_fields_must_not_be_primitive = fields()
		.that()
		.areAnnotatedWith(Option.class)
		.should(notBePrimitiveType("@Option"));

	// --- @Argument fields must not use primitive types ---

	@ArchTest
	static final ArchRule argument_fields_must_not_be_primitive = fields()
		.that()
		.areAnnotatedWith(Argument.class)
		.should(notBePrimitiveType("@Argument"));

	// --- @Arguments fields must not use primitive types ---

	@ArchTest
	static final ArchRule arguments_fields_must_not_be_primitive = fields()
		.that()
		.areAnnotatedWith(Arguments.class)
		.should(notBePrimitiveType("@Arguments"));

	private static ArchCondition<JavaField> notBePrimitiveType(String annotationName) {
		return new ArchCondition<JavaField>(
				"not use a primitive type as JBangDefaultValueProvider cannot distinguish between not set and default value.") {
			@Override
			public void check(JavaField field, ConditionEvents events) {
				JavaClass rawType = field.getRawType();
				for (Class<?> primitive : PRIMITIVE_TYPES) {
					if (rawType.isEquivalentTo(primitive)) {
						events.add(SimpleConditionEvent.violated(
								field,
								field.getOwner().getName() + "." + field.getName()
										+ ": use " + boxedName(primitive) + " instead of "
										+ primitive.getName()));
					}
				}
			}
		};
	}

	private static String boxedName(Class<?> primitive) {
		if (primitive == boolean.class)
			return "Boolean";
		if (primitive == byte.class)
			return "Byte";
		if (primitive == char.class)
			return "Character";
		if (primitive == short.class)
			return "Short";
		if (primitive == int.class)
			return "Integer";
		if (primitive == long.class)
			return "Long";
		if (primitive == float.class)
			return "Float";
		if (primitive == double.class)
			return "Double";
		return primitive.getName();
	}
}
