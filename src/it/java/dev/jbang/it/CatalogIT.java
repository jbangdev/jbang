package dev.jbang.it;

import static dev.jbang.it.CommandResultAssert.assertThat;

import org.junit.jupiter.api.Test;

public class CatalogIT extends AbstractHelpBaseIT {

//     Scenario: java catalog list
//     When command('jbang catalog add --global --name averylongcatalogname jbang-catalog.json')
//     And command('jbang catalog list')
//     Then match out contains "averylongcatalogname"
//     Then match out contains "JBang test scripts"
	@Test
	public void shouldListCatalog() {
		assertThat(shell("jbang catalog add --global --name averylongcatalogname jbang-catalog.json"));
		assertThat(shell("jbang catalog add --global --name averylongcatalogname jbang-catalog.json"));
		assertThat(shell("jbang catalog list"))
			.outContains("averylongcatalogname")
			.outContains("JBang test scripts");
	}

//   Scenario: add catalog and run catalog named reference
//     When command('jbang catalog add --global --name tc jbang-catalog.json')
//     Then command('jbang echo@tc tako')
//     Then match out == "0:tako\n"
	@Test
	public void shouldAddCatalogAndRunCatalogNamedReference() {
		assertThat(shell("jbang catalog add --global --name tc jbang-catalog.json")).succeeded();
		assertThat(shell("jbang echo@tc tako"))
			.outIsExactly("0:tako" + System.lineSeparator());
	}

//   Scenario: add catalog and remove
//     When command('jbang catalog add --global --name tc jbang-catalog.json')
//     Then command('jbang catalog remove tc')
//     Then command('jbang echo@tc tako')
//     Then match err contains "Unknown catalog 'tc'"
	@Test
	public void shouldAddCatalogAndRemove() {
		shell("jbang catalog add --global --name tc jbang-catalog.json");
		shell("jbang catalog remove tc");
		assertThat(shell("jbang echo@tc tako")).errContains("Unknown catalog 'tc'");
	}

//   Scenario: add catalog twice with same name
//     When command('jbang catalog add --global --name tc jbang-catalog.json')
//     Then command('jbang catalog add --global --name tc jbang-catalog.json')
//     Then match err contains "A catalog with name 'tc' already exists, use '--force' to add anyway"
//     Then match exit == 2
	@Test
	public void shouldAddCatalogTwiceWithSameName() {
		shell("jbang catalog add --global --name tc jbang-catalog.json");
		assertThat(shell("jbang catalog add --global --name tc jbang-catalog.json"))
			.errContains(
					"A catalog with name 'tc' already exists, use '--force' to add anyway")
			.exitedWith(2);
	}

//   Scenario: force add catalog twice with same name
//     When command('jbang catalog add --global --name tc jbang-catalog.json')
//     Then command('jbang catalog add --global --name tc --force jbang-catalog.json')
//     Then match exit == 0
	@Test
	public void shouldForceAddCatalogTwiceWithSameName() {
		shell("jbang catalog add --global --name tc jbang-catalog.json");
		assertThat(shell("jbang catalog add --global --name tc --force jbang-catalog.json"))
			.exitedWith(0);
	}

//   Scenario: add catalog twice with different name
//     When command('jbang catalog add --global --name tc jbang-catalog.json')
//     Then command('jbang catalog add --global --name ct jbang-catalog.json')
//     Then command('jbang echo@tc tako')
//     And  match exit == 0
//     Then command('jbang echo@ct tako')
//     And match exit == 0
	@Test
	public void shouldAddCatalogTwiceWithDifferentName() {
		shell("jbang catalog add --global --name tc jbang-catalog.json");
		shell("jbang catalog add --global --name ct jbang-catalog.json");
		assertThat(shell("jbang echo@tc tako")).exitedWith(0);
		assertThat(shell("jbang echo@ct tako")).exitedWith(0);
	}

//   Scenario: access remote catalog
//     When command('jbang build hello@jbangdev')
//     Then  match exit == 0
	@Test
	public void shouldAccessRemoteCatalog() {
		assertThat(shell("jbang build hello@jbangdev")).succeeded();
	}

//   Scenario: list remote catalog aliases
//     When command('jbang alias list jbangdev/jbang-catalog')
//     Then  match exit == 0
//     And match out contains "@jbangdev"
//     And match out !contains "@jbangdev/jbang-catalog"
	@Test
	public void shouldListRemoteCatalogAliases() {
		assertThat(shell("jbang alias list jbangdev/jbang-catalog")).succeeded()
			.outContains("@jbangdev")
			.outDoesNotContain("@jbangdev/jbang-catalog");
	}

//   Scenario: list remote catalog templates
//     When command('jbang template list jbangdev/jbang-catalog')
//     Then  match exit == 0
//     And match out contains "@jbangdev"
//     And match out !contains "@jbangdev/jbang-catalog"
	@Test
	public void shouldListRemoteCatalogTemplates() {
		assertThat(shell("jbang template list jbangdev/jbang-catalog")).succeeded()
			.outContains("@jbangdev")
			.outDoesNotContain("@jbangdev/jbang-catalog");
	}

//   Scenario: Removing built-in catalog
//   When command('jbang catalog remove jbanghub')
//   * match exit == 0
//   * match err contains "Cannot remove catalog ref jbanghub from built-in catalog"
	@Test
	public void shouldRemoveBuiltInCatalog() {
		assertThat(shell("jbang catalog remove jbanghub"))
			.errContains(
					"Cannot remove catalog ref jbanghub from built-in catalog")
			.exitedWith(0);
	}

	@Override
	protected String commandName() {
		return "catalog";
	}
}