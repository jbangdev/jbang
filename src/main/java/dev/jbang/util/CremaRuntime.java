package dev.jbang.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.jbang.ExitException;

/**
 * Experimental bytecode launcher. The build marker is captured at image build
 * time only for nativeImageCrema.
 */
public final class CremaRuntime {
	private static final boolean ENABLED = "buildtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))
			&& Boolean.getBoolean("jbang.crema.enabled");

	private CremaRuntime() {
	}

	public static boolean isSupported() {
		return ENABLED && "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
	}

	public static void launch(List<Path> classpath, String mainClass, String[] args, Map<String, String> properties)
			throws IOException {
		URL[] urls = new URL[classpath.size()];
		for (int i = 0; i < classpath.size(); i++) {
			urls[i] = classpath.get(i).toUri().toURL();
		}
		// Use the platform loader as parent, avoiding JBang's embedded dependencies.
		// Reflection keeps the regular distribution compatible with Java 8.
		ClassLoader parent;
		try {
			parent = (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
		} catch (ReflectiveOperationException e) {
			parent = null;
		}
		URLClassLoader loader = new URLClassLoader(urls, parent);
		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		thread.setContextClassLoader(loader);
		properties.forEach(System::setProperty);
		System.setProperty("java.class.path", classpath.stream()
			.map(Path::toString)
			.collect(Collectors.joining(java.io.File.pathSeparator)));
		if ("".equals(System.getProperty("jdk.module.path"))) {
			System.clearProperty("jdk.module.path");
		}
		try {
			Class<?> type = Class.forName(mainClass, false, loader);
			Method main = type.getMethod("main", String[].class);
			if (!Modifier.isStatic(main.getModifiers()) || main.getReturnType() != void.class) {
				throw ExitException
					.invalidInput("jbang crema requires public static void main(String[]): " + mainClass);
			}
			main.invoke(null, (Object) args);
		} catch (InvocationTargetException e) {
			throw ExitException.genericError("Application failed in jbang crema: " + e.getCause(), e.getCause());
		} catch (ReflectiveOperationException | LinkageError e) {
			throw ExitException.genericError("Cannot launch " + mainClass + " in jbang crema: " + e, e);
		} finally {
			thread.setContextClassLoader(previous);
			// Do not close the loader: application threads may still need
			// classes/resources.
		}
	}
}
