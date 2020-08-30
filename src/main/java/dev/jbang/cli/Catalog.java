package dev.jbang.cli;

import java.io.PrintWriter;

import dev.jbang.AliasUtil;
import dev.jbang.Settings;
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
		if (Settings.getCatalogs().containsKey(name)) {
			throw new IllegalArgumentException("A catalog with that name already exists");
		}
		PrintWriter err = spec.commandLine().getErr();
		AliasUtil.Aliases aliases = AliasUtil.getCatalogAliasesByRef(urlOrFile, true);
		if (description == null) {
			description = aliases.description;
		}
		Settings.addCatalog(name, urlOrFile, description);
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "update", description = "Retrieve the latest contents of the catalogs.")
	public Integer update() {
		PrintWriter err = spec.commandLine().getErr();
		Settings.getCatalogs()
				.entrySet()
				.stream()
				.forEach(e -> {
					err.println("Updating catalog '" + e.getKey() + "' from " + e.getValue().catalogRef + "...");
					AliasUtil.getCatalogAliasesByRef(e.getValue().catalogRef, true);
				});
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "list", description = "Show currently defined catalogs.")
	public Integer list(
			@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of a catalog", arity = "0..1") String name) {
		PrintWriter out = spec.commandLine().getOut();
		PrintWriter err = spec.commandLine().getErr();
		if (name == null) {
			Settings.getCatalogs()
					.keySet()
					.stream()
					.sorted()
					.forEach(nm -> {
						AliasUtil.Catalog cat = Settings.getCatalogs().get(nm);
						if (cat.description != null) {
							out.println(nm + " = " + cat.description);
							out.println(Util.repeat(" ", nm.length()) + "   (" + cat.catalogRef + ")");
						} else {
							out.println(nm + " = " + cat.catalogRef);
						}
					});
		} else {
			AliasUtil.Aliases aliases = AliasUtil.getCatalogAliasesByName(name, false);
			Alias.printAliases(out, name, aliases);
		}
		return CommandLine.ExitCode.SOFTWARE;
	}

	@CommandLine.Command(name = "remove", description = "Remove existing catalog.")
	public Integer remove(
			@CommandLine.Parameters(paramLabel = "name", index = "0", description = "The name of the catalog", arity = "1") String name) {
		if (!Settings.getCatalogs().containsKey(name)) {
			throw new IllegalArgumentException("A catalog with that name does not exist");
		}
		Settings.removeCatalog(name);
		return CommandLine.ExitCode.SOFTWARE;
	}
}
