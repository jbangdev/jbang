package dev.jbang.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.aesh.command.Command;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import dev.jbang.cli.BaseCommand;
import dev.jbang.cli.ExitException;

/**
 * Architectural rules enforced via ArchUnit.
 *
 * <p>
 * These rules verify structural invariants at the bytecode level: CLI command
 * inheritance and package dependency direction.
 *
 * <p>
 * For source-level conventions (wildcard imports, exit code literals), see
 * {@link SourceConventionTest}.
 */
@AnalyzeClasses(packages = "dev.jbang", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	/**
	 * Non-CLI packages must not depend on {@code dev.jbang.cli} classes except
	 * {@link ExitException}.
	 * <p>
	 * This enforces a clean dependency direction: CLI commands depend on domain
	 * logic, not the other way around. {@code EXIT_*} constants live on
	 * {@link ExitException} so non-CLI code never needs to import
	 * {@link BaseCommand}.
	 * <p>
	 * {@link dev.jbang.Main} is exempt as the bootstrap entry point.
	 */
	@ArchTest
	static final ArchRule non_cli_should_not_depend_on_cli = noClasses()
		.that()
		.resideOutsideOfPackage("dev.jbang.cli..")
		.and()
		.haveNameNotMatching("dev\\.jbang\\.Main")
		.should()
		.dependOnClassesThat(
				resideInAPackage("dev.jbang.cli..").and(not(equivalentTo(ExitException.class))));

	/**
	 * All CLI command classes must extend {@link BaseCommand} (or its subclass
	 * {@link dev.jbang.cli.BaseBuildCommand}).
	 * <p>
	 * This ensures every command inherits shared lifecycle behavior: default value
	 * resolution, debug/verbose flag handling, and consistent exit-code reporting.
	 * Direct {@code Command} implementations bypass this and will silently break
	 * CLI conventions.
	 */
	@ArchTest
	static final ArchRule cli_commands_must_extend_BaseCommand = classes()
		.that()
		.implement(Command.class)
		.and()
		.resideInAPackage("dev.jbang.cli..")
		.should()
		.beAssignableTo(BaseCommand.class);
}
