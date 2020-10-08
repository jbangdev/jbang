package dev.jbang.cli;

import static dev.jbang.cli.BaseCommand.EXIT_INVALID_INPUT;
import static dev.jbang.cli.BaseCommand.EXIT_OK;

import java.io.PrintStream;
import java.io.PrintWriter;

import dev.jbang.AliasUtil;
import dev.jbang.ExitException;
import dev.jbang.Util;

import picocli.CommandLine;

@CommandLine.Command(name = "catalog", description = "Manage Catalogs of aliases.")
public class Catalog {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	@CommandLine.Command(name = "add", description = "Add a catalog.")
	public Integer add(
			@CommandLine.Option(names = { "--description",
					"-d" }, description = "A description for the catalog") String description,
			@CommandLine.Parameters(paramLabel = "name", index = "0", description = "A name for the catalog", arity = "1") String name,
			@CommandLine.Parameters(paramLabel = "urlOrFile", index = "1", description = "A file or URL to a catalog file", arity = "1") String urlOrFile) {
		if (!name.matches("^[a-zA-Z][-.\\w]*$")) {
			throw new IllegalArgumentException(
					"Invalid catalog name, it should start with a letter followed by 0 or more letters, digits, underscores, hyphens or dots");
		}
		if (AliasUtil.getCatalogIndex().catalogRefs.containsKey(name)) {
			throw new ExitException(EXIT_INVALID_INPUT, "A catalog with that name already exists");
		}
		AliasUtil.Catalog catalog = AliasUtil.getCatalogByRef(urlOrFile, true);
		if (description == null) {
			description = catalog.description;
		}
		AliasUtil.addCatalog(name, urlOrFile, description);
		return EXIT_OK;
	}

	@CommandLine.Command(name = "update", description = "Retrieve the latest contents of the catalogs.")
	public Integer update() {
		PrintWriter err = spec.commandLine().getErr();
		AliasUtil.getCatalogIndex().catalogRefs
												.entrySet()
												.stream()
												.forEach(e -> {
													err.println("Updating catalog '" + e.getKey() + "' from "
															+ e.getValue().catalogRef + "...");
													AliasUtil.getCatalogByRef(e.getValue().catalogRef, true);
												});
		return EXIT_OK;
	}

	@CommandLine.Command(name = "list", description = "Show currently defined catalogs.")
	public Integer list(
			@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of a catalog", arity = "0..1") String name) {
		PrintStream out = System.out;
		if (name == null) {
			AliasUtil.getCatalogIndex().catalogRefs
													.keySet()
													.stream()
													.sorted()
													.forEach(nm -> {
														AliasUtil.CatalogRef cat = AliasUtil.getCatalogIndex().catalogRefs.get(
																nm);
														if (cat.description != null) {
															out.println(nm + " = " + cat.description);
															out.println(Util.repeat(" ", nm.length()) + "   ("
																	+ cat.catalogRef
																	+ ")");
														} else {
															out.println(nm + " = " + cat.catalogRef);
														}
													});
		} else {
			AliasUtil.Catalog catalog = AliasUtil.getCatalogByName(name, false);
			AliasList.printAliases(out, name, catalog);
		}
		return EXIT_OK;
	}

	@CommandLine.Command(name = "remove", description = "Remove existing catalog.")
	public Integer remove(
			@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of the catalog", arity = "1") String name) {
		if (!AliasUtil.getCatalogIndex().catalogRefs.containsKey(name)) {
			throw new ExitException(EXIT_INVALID_INPUT, "A catalog with that name does not exist");
		}
		AliasUtil.removeCatalog(name);
		return EXIT_OK;
	}
}
