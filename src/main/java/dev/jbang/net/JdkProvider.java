package dev.jbang.net;

import static dev.jbang.util.JavaUtil.resolveJavaVersionStringFromPath;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.jbang.util.JavaUtil;

/**
 * This interface must be implemented by providers that are able to give access
 * to JDKs installed on the user's system. Some providers will also be able to
 * manage those JDKs by installing and uninstalling them at the user's request.
 * In those cases the <code>canUpdate()</code> should return <code>true</code>.
 *
 * The providers deal in JDK identifiers, not in versions. Those identifiers are
 * specific to the implementation but should follow two important rules: 1. they
 * must be unique across implementations 2. they must start with an integer
 * specifying the main JDK version
 */
public interface JdkProvider {

	class Jdk implements Comparable<Jdk> {
		@Nonnull
		private final transient JdkProvider provider;
		@Nonnull
		private final String id;
		@Nonnull
		private final String version;
		@Nullable
		private final Path home;

		private Jdk(@Nonnull JdkProvider provider, @Nonnull String id, @Nullable Path home, @Nonnull String version) {
			this.provider = provider;
			this.id = id;
			this.version = version;
			this.home = home;
		}

		@Nonnull
		public JdkProvider getProvider() {
			return provider;
		}

		/**
		 * Returns the id that is used to uniquely identify this JDK across all
		 * providers
		 */
		@Nonnull
		public String getId() {
			return id;
		}

		/**
		 * Returns the JDK's version
		 */
		public String getVersion() {
			return version;
		}

		/**
		 * The path to where the JDK is installed. Can be <code>null</code> which means
		 * the JDK isn't currently installed by that provider
		 */
		@Nullable
		public Path getHome() {
			return home;
		}

		public int getMajorVersion() {
			return JavaUtil.parseJavaVersion(getVersion());
		}

		public Jdk install() {
			return provider.install(id);
		}

		public void uninstall() {
			provider.uninstall(id);
		}

		public boolean isInstalled() {
			return home != null;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			Jdk jdk = (Jdk) o;
			return id.equals(jdk.id) && Objects.equals(home, jdk.home);
		}

		@Override
		public int hashCode() {
			return Objects.hash(home, id);
		}

		@Override
		public int compareTo(Jdk o) {
			return Integer.compare(getMajorVersion(), o.getMajorVersion());
		}

		@Override
		public String toString() {
			return getMajorVersion() + " (" + version + ", " + id + ", " + home + ")";
		}
	}

	default Jdk createJdk(@Nonnull String id, @Nullable Path home, @Nonnull String version) {
		return new Jdk(this, id, home, version);
	}

	default String name() {
		String nm = getClass().getSimpleName();
		// TODO: this 11 is here assuming it ends in "JdkProvider" - dont make that
		// broken assumption
		return nm.substring(0, nm.length() - 11).toLowerCase();
	}

	/**
	 * For providers that can update this returns a set of JDKs that are available
	 * for installation. Providers might set the <code>home</code> field of the JDK
	 * objects if the respective JDK is currently installed on the user's system,
	 * but only if they can ensure that it's the exact same version, otherwise they
	 * should just leave the field <code>null</code>.
	 *
	 * @return List of <code>Jdk</code> objects
	 */
	@Nonnull
	default List<Jdk> listAvailable() {
		throw new UnsupportedOperationException("Listing available JDKs is not supported by " + getClass().getName());
	}

	/**
	 * Returns a set of JDKs that are currently installed on the user's system.
	 *
	 * @return List of <code>Jdk</code> objects, possibly empty
	 */
	@Nonnull
	List<Jdk> listInstalled();

	/**
	 * Determines if a JDK of the requested version is currently installed by this
	 * provider and if so returns its respective <code>Jdk</code> object, otherwise
	 * it returns <code>null</code>. If <code>openVersion</code> is set to true the
	 * method will also return the next installed version if the exact version was
	 * not found.
	 *
	 * @param version     The specific JDK version to return
	 * @param openVersion Return newer version if exact is not available
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	default Jdk getJdkByVersion(int version, boolean openVersion) {
		List<Jdk> jdks = listInstalled();
		Jdk res;
		if (openVersion) {
			res = jdks	.stream()
						.sorted()
						.filter(jdk -> jdk.getMajorVersion() >= version)
						.findFirst()
						.orElse(null);
		} else {
			res = jdks	.stream()
						.filter(jdk -> jdk.getMajorVersion() == version)
						.findFirst()
						.orElse(null);
		}
		return res;
	}

	/**
	 * Determines if the given id refers to a JDK managed by this provider and if so
	 * returns its respective <code>Jdk</code> object, otherwise it returns
	 * <code>null</code>.
	 *
	 * @param id The id to look for
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	Jdk getJdkById(@Nonnull String id);

	/**
	 * Determines if the given path belongs to a JDK managed by this provider and if
	 * so returns its respective <code>Jdk</code> object, otherwise it returns
	 * <code>null</code>.
	 * 
	 * @param jdkPath The path to look for
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	Jdk getJdkByPath(@Nonnull Path jdkPath);

	/**
	 * For providers that can update this installs the indicated JDK
	 * 
	 * @param jdk The identifier of the JDK to install
	 * @return A <code>Jdk</code> object
	 * @throws UnsupportedOperationException if the provider can not update
	 */
	@Nonnull
	default Jdk install(@Nonnull String jdk) {
		throw new UnsupportedOperationException("Installing a JDK is not supported by " + getClass().getName());
	}

	/**
	 * Uninstalls the indicated JDK
	 * 
	 * @param jdk The identifier of the JDK to install
	 * @throws UnsupportedOperationException if the provider can not update
	 */
	default void uninstall(@Nonnull String jdk) {
		throw new UnsupportedOperationException("Uninstalling a JDK is not supported by " + getClass().getName());
	}

	/**
	 * Indicates if the provider can be used or not. This can perform sanity checks
	 * like the availability of certain package being installed on the system or
	 * even if the system is running a supported operating system.
	 *
	 * @return True if the provider can be used, false otherwise
	 */
	default boolean canUse() {
		return true;
	}

	/**
	 * Indicates if the provider is able to (un)install JDKs or not
	 * 
	 * @return True if JDKs can be (un)installed, false otherwise
	 */
	default boolean canUpdate() {
		return false;
	}

	/**
	 * This is a special "dummy" provider that can be used to create
	 * <code>Jdk</code> objects for JDKs that don't seem to belong to any of the
	 * known providers but for which we still want an object to represent them.
	 */
	class UnknownJdkProvider implements JdkProvider {
		private static final UnknownJdkProvider instance = new UnknownJdkProvider();

		@Nonnull
		@Override
		public List<Jdk> listInstalled() {
			return Collections.emptyList();
		}

		@Nullable
		@Override
		public Jdk getJdkById(@Nonnull String id) {
			return null;
		}

		@Nullable
		@Override
		public Jdk getJdkByPath(@Nonnull Path jdkPath) {
			Optional<String> version = resolveJavaVersionStringFromPath(jdkPath);
			if (version.isPresent()) {
				return createJdk("unknown", jdkPath, version.get());
			} else {
				return null;
			}
		}

		public static Jdk createJdk(Path jdkPath) {
			return instance.getJdkByPath(jdkPath);
		}
	}

}
