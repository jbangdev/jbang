package dev.jbang.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.Type;

import dev.jbang.util.Util;

/**
 * Scans a source of compiled class bytes and reports the classes that can act
 * as an application {@code main}, Java agent {@code agentmain}, or
 * {@code premain} entry point.
 * <p>
 * This is a <em>deep</em> module: the jandex indexing, the entry-point
 * detection predicates and the per-class error handling all live behind a
 * single {@link #scan(ClassBytesSource)} call. Callers supply only
 * <em>where</em> the class bytes come from (a jar, a directory of
 * {@code .class} files, ...) via a tiny {@link ClassBytesSource} adapter and
 * decide for themselves how to pick from the reported candidates.
 * <p>
 * Invariants:
 * <ul>
 * <li>A single unparseable class never aborts the scan; it is skipped with a
 * verbose note.</li>
 * <li>The scan never prompts, never mutates anything, and never fails because
 * no entry point was found - that is caller policy.</li>
 * <li>I/O errors from the underlying source propagate to the caller, which owns
 * the source and its error handling.</li>
 * </ul>
 */
public final class MainClassScanner {

	private static final Type STRING_ARRAY = Type.create(DotName.createSimple("[Ljava.lang.String;"),
			Type.Kind.ARRAY);
	private static final Type STRING = Type.create(DotName.createSimple("java.lang.String"), Type.Kind.CLASS);
	private static final Type INSTRUMENTATION = Type.create(
			DotName.createSimple("java.lang.instrument.Instrumentation"), Type.Kind.CLASS);

	private static final Predicate<ClassInfo> MAIN_FINDER = c -> c.method("main", STRING_ARRAY) != null
			|| c.method("main") != null;
	private static final Predicate<ClassInfo> AGENTMAIN_FINDER = c -> c.method("agentmain", STRING,
			INSTRUMENTATION) != null
			|| c.method("agentmain", STRING) != null;
	private static final Predicate<ClassInfo> PREMAIN_FINDER = c -> c.method("premain", STRING, INSTRUMENTATION) != null
			|| c.method("premain", STRING) != null;

	private MainClassScanner() {
	}

	/**
	 * A source of compiled class bytes. Implementations open each {@code .class}
	 * stream, hand it to the consumer, and are responsible for closing it; they
	 * also apply any source-specific filtering (e.g. skipping inner classes or
	 * {@code module-info.class}).
	 */
	@FunctionalInterface
	public interface ClassBytesSource {
		void forEachClass(ClassConsumer consumer) throws IOException;
	}

	/** Receives one class entry (its name, for diagnostics, and its bytes). */
	@FunctionalInterface
	public interface ClassConsumer {
		void accept(String entryName, InputStream bytes);
	}

	/**
	 * The entry-point classes found by a scan, as fully-qualified names.
	 * <p>
	 * The index is built once by {@link #scan(ClassBytesSource)}; each kind of
	 * entry point is detected lazily on first access and memoized, so a caller only
	 * pays for the queries it actually makes (most callers want just the
	 * {@code main} classes).
	 */
	public static final class MainScan {
		private final Collection<ClassInfo> classes;
		private List<String> mainClasses;
		private boolean agentMainComputed;
		private String agentMain;
		private boolean preMainComputed;
		private String preMain;

		MainScan(Collection<ClassInfo> classes) {
			this.classes = classes;
		}

		/** All classes declaring a {@code main(String[])} (or no-arg {@code main}). */
		public List<String> getMainClasses() {
			if (mainClasses == null) {
				mainClasses = classes.stream()
					.filter(MAIN_FINDER)
					.map(c -> c.name().toString())
					.collect(Collectors.toList());
			}
			return mainClasses;
		}

		public Optional<String> getAgentMain() {
			if (!agentMainComputed) {
				agentMain = findFirst(AGENTMAIN_FINDER);
				agentMainComputed = true;
			}
			return Optional.ofNullable(agentMain);
		}

		public Optional<String> getPreMain() {
			if (!preMainComputed) {
				preMain = findFirst(PREMAIN_FINDER);
				preMainComputed = true;
			}
			return Optional.ofNullable(preMain);
		}

		private String findFirst(Predicate<ClassInfo> finder) {
			return classes.stream()
				.filter(finder)
				.map(c -> c.name().toString())
				.findFirst()
				.orElse(null);
		}
	}

	/**
	 * Index every class yielded by {@code source} and report its entry-point
	 * candidates.
	 *
	 * @throws IOException if the source fails to yield its classes
	 */
	public static MainScan scan(ClassBytesSource source) throws IOException {
		Indexer indexer = new Indexer();
		source.forEachClass((name, bytes) -> {
			try {
				indexer.index(bytes);
			} catch (Exception e) {
				// One unparseable class shouldn't break entry-point detection
				Util.verboseMsg("Error indexing class " + name + ": " + e);
			}
		});
		return new MainScan(indexer.complete().getKnownClasses());
	}
}
