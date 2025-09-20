package dev.jbang.cli;

import static dev.jbang.util.Util.entry;
import static java.lang.System.getenv;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import com.google.gson.Gson;

import dev.jbang.catalog.TemplateProperty;
import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.source.RefTarget;
import dev.jbang.util.JavaUtil;
import dev.jbang.util.TemplateEngine;
import dev.jbang.util.Util;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "Initialize a script.")
public class Init extends BaseCommand {

	@CommandLine.Mixin
	BuildMixin buildMixin;

	@CommandLine.Option(names = { "--template",
			"-t" }, description = "Init script with a java class useful for scripting")
	public String initTemplate;

	@CommandLine.Option(names = {
			"--force" }, description = "Force overwrite of existing files")
	public boolean force;

	@CommandLine.Option(names = { "--edit" }, description = "Open editor on the generated file(s)")
	public boolean edit;

	@CommandLine.Option(names = { "-D" }, description = "set a system property", mapFallbackValue = "true")
	public Map<String, Object> properties = new HashMap<>();

	@CommandLine.Option(names = {
			"--deps" }, converter = CommaSeparatedConverter.class, description = "Add additional dependencies (Use commas to separate them).")
	List<String> dependencies;

	@CommandLine.Parameters(paramLabel = "scriptOrFile", index = "0", description = "A file or URL to a Java code file", arity = "1")
	String scriptOrFile;
	@CommandLine.Parameters(paramLabel = "params", index = "1..*", arity = "0..*", description = "Parameters to pass on to the generation")
	List<String> params = new ArrayList<>();

	public void requireScriptArgument() {
		if (scriptOrFile == null) {
			throw new IllegalArgumentException("Missing required parameter: '<scriptOrFile>'");
		}
	}

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();

		dev.jbang.catalog.Template tpl = dev.jbang.catalog.Template.get(initTemplate);
		if (tpl == null) {
			throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
					"Could not find init template named: " + initTemplate
							+ ". Try run with --fresh to get latest catalog updates.");
		}

		boolean absolute = new File(scriptOrFile).isAbsolute();
		Path outFile = Util.getCwd().resolve(scriptOrFile);
		Path outDir = outFile.getParent();
		String outName = outFile.getFileName().toString();

		String baseName = Util.getBaseName(Paths.get(scriptOrFile).getFileName().toString());
		String extension = Util.extension(scriptOrFile);

		properties.put("scriptref", scriptOrFile);
		properties.put("baseName", baseName);
		properties.put("dependencies", dependencies);

		int reqVersion = buildMixin.javaVersion != null ? JavaUtil.minRequestedVersion(buildMixin.javaVersion)
				: JavaUtil.getCurrentMajorJavaVersion();

		properties.put("requestedJavaVersion", buildMixin.javaVersion);
		properties.put("javaVersion", reqVersion);
		properties.put("compactSourceFiles", reqVersion >= 25);
		// properties.put("magiccontent", "//no gpt response. make sure you ran with
		// --preview and OPENAI_API_KEY set");
		if (Util.isPreview() && !params.isEmpty()) {
			Util.infoMsg("JBangGPT Preview activated");
			Util.warnMsg(
					"The result can vary greatly. Sometimes it works - other times it is just for inspiration or a good laugh.");
			String openaiKey = getenv("OPENAI_API_KEY");
			if (openaiKey != null && !openaiKey.trim().isEmpty()) {
				String response = fetchGptResponse(baseName, extension, String.join(" ", params), openaiKey);
				// sometimes gpt adds a markdown ```java block so lets remove all lines starting
				// with ``` in the output.
				response = response.replaceAll("(?m)^```.*(?:\r?\n|$)", "");
				properties.put("magiccontent", response);
			} else {
				Util.warnMsg("OPENAI_API_KEY environment variable not found. Will use normal jbang init.");
			}
		}

		List<RefTarget> refTargets = tpl.fileRefs.entrySet()
			.stream()
			.map(e -> entry(
					resolveBaseName(e.getKey(), e.getValue(), outName),
					tpl.resolve(e.getValue())))
			.map(e -> RefTarget.create(
					e.getValue(),
					e.getKey(),
					ResourceResolver.combined(tpl.catalog.catalogRef, ResourceResolver.forResources())))
			.collect(Collectors.toList());

		applyTemplateProperties(tpl);

		if (!force) {
			// Check if any of the files already exist
			for (RefTarget refTarget : refTargets) {
				Path target = refTarget.to(outDir);
				if (Files.exists(target)) {
					warn("File " + target + " already exists. Will not initialize.");
					return EXIT_GENERIC_ERROR;
				}
			}
		}

		try {
			for (RefTarget refTarget : refTargets) {
				if (refTarget.getSource().getOriginalResource().endsWith(".qute")) {
					// TODO fix outFile path handling
					Path out = refTarget.to(outDir);
					renderQuteTemplate(out, refTarget.getSource(), properties);
				} else {
					refTarget.copy(outDir);
				}
			}
		} catch (IOException e) {
			// Clean up any files we already created
			for (RefTarget refTarget : refTargets) {
				Util.deletePath(refTarget.to(outDir), true);
			}
		}

		String renderedScriptOrFile = getRenderedScriptOrFile(tpl.fileRefs, refTargets, outDir, absolute);
		if (edit) {
			info("File initialized. Opening editor for you. You can also now run it with 'jbang "
					+ renderedScriptOrFile);
			// TODO: quick hack that gets the job of opening editor done; but really should
			// make a isolated api to open editor instead of invoking subcommand.
			// nice thing wit this is that it will honor you jbang config for edit
			// automatically.
			JBang.getCommandLine().execute("edit", renderedScriptOrFile);
		} else {
			info("File initialized. You can now run it with 'jbang " + renderedScriptOrFile
					+ "' or edit it using 'jbang edit --open=[editor] "
					+ renderedScriptOrFile + "' where [editor] is your editor or IDE, e.g. '"
					+ Edit.knownEditors[new Random().nextInt(Edit.knownEditors.length)]
					+ "'. If your IDE supports JBang, you can edit the directory instead: 'jbang edit . "
					+ renderedScriptOrFile + "'. See https://jbang.dev/ide");
		}
		return EXIT_OK;
	}

	static class GPTResponse {
		public String id;
		public String object;
		public double created;
		public String model;
		public Map<String, Double> usage;
		public List<Choice> choices;

		static public class Choice {
			public Message message;

			public static class Message {
				public String role;
				public String content;
			}
		}

		public Error error;

		static public class Error {
			String message;
			String type;
			String param;
			String code;

			@Override
			public String toString() {
				return type + ": " + message + " (code:" + code + "/param:" + param + ")";
			}
		}
	}

	public static String fetchGptResponse(String baseName, String extension, String request, String key) {
		String answer = null;
		try {
			URL url = new URL("https://api.openai.com/v1/chat/completions");
			HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
			httpConn.setRequestMethod("POST");

			httpConn.setRequestProperty("Content-Type", "application/json");
			httpConn.setRequestProperty("Authorization", "Bearer " + key);

			httpConn.setDoOutput(true);
			OutputStreamWriter writer = new OutputStreamWriter(httpConn.getOutputStream());

			Map<String, Object> prompt = new HashMap<>();
			prompt.put("model", "gpt-3.5-turbo");
			prompt.put("temperature", 0.8); // reduce variation, more deterministic
			Gson gson = new Gson();

			List<Map> messages = new ArrayList<>();
			messages.add(prompt("system",
					"You are to generate a response that only contain code that is written in a file ending in "
							+ extension + " in the style of jbang. The main class must be named "
							+ baseName
							+ " " +
							". Add no additional text." +
							"You can put comments in the code."));
			messages.add(prompt("user", request));
			prompt.put("messages", messages);
			Util.verboseMsg("ChatGPT prompt " + prompt);
			writer.write(gson.toJson(prompt));
			writer.flush();
			writer.close();
			httpConn.getOutputStream().close();

			InputStream responseStream = httpConn.getResponseCode() / 100 == 2
					? httpConn.getInputStream()
					: httpConn.getErrorStream();
			Scanner s = new Scanner(responseStream).useDelimiter("\\A");
			String response = s.hasNext() ? s.next() : "";
			Util.verboseMsg("ChatGPT response: " + response);
			GPTResponse result = gson.fromJson(response, GPTResponse.class);
			if (result.choices != null && result.error == null) {
				answer = result.choices.stream().map(c -> c.message.content).collect(Collectors.joining("\n"));
			} else {
				Util.errorMsg(
						"Received no useful response from ChatGPT. Usage limit exceeded or wrong key? " + result.error);
				throw new ExitException(EXIT_UNEXPECTED_STATE);
			}
		} catch (IOException e) {
			Util.errorMsg("Problem fetching response from ChatGPT", e);
		}
		return answer;
	}

	private static Map prompt(String role, String content) {
		Map<String, String> m = new HashMap<>();
		m.put("role", role);
		m.put("content", content);
		return m;
	}

	private void applyTemplateProperties(dev.jbang.catalog.Template tpl) {
		if (tpl.properties != null) {
			for (Map.Entry<String, TemplateProperty> entry : tpl.properties.entrySet()) {
				if (entry.getValue().getDefaultValue() != null) {
					properties.putIfAbsent(entry.getKey(), entry.getValue().getDefaultValue());
				}
			}
		}
	}

	static Path resolveBaseName(String refTarget, String refSource, String outName) {
		String result = refTarget;
		if (dev.jbang.cli.Template.TPL_FILENAME_PATTERN.matcher(refTarget).find()
				|| dev.jbang.cli.Template.TPL_BASENAME_PATTERN.matcher(refTarget).find()) {
			String baseName = Util.base(outName);
			String outExt = Util.extension(outName);
			String targetExt = Util.extension(refTarget);
			if (targetExt.isEmpty()) {
				targetExt = refSource.endsWith(".qute") ? Util.extension(Util.base(refSource))
						: Util.extension(refSource);
			}
			if (!outExt.isEmpty() && !outExt.equals(targetExt)) {
				throw new ExitException(BaseCommand.EXIT_INVALID_INPUT,
						"Template expects " + targetExt + " extension, not " + outExt);
			}
			result = dev.jbang.cli.Template.TPL_FILENAME_PATTERN.matcher(result).replaceAll(outName);
			result = dev.jbang.cli.Template.TPL_BASENAME_PATTERN.matcher(result).replaceAll(baseName);
		}
		return Paths.get(result);
	}

	void renderQuteTemplate(Path outFile, ResourceRef templateRef, Map<String, Object> properties)
			throws IOException {
		Template template = TemplateEngine.instance().getTemplate(templateRef);
		if (template == null) {
			throw new ExitException(EXIT_INVALID_INPUT,
					"Could not find or load template: " + templateRef);
		}

		if (outFile.toString().endsWith(".java")) {
			String basename = Util.getBaseName(outFile.getFileName().toString());
			if (!SourceVersion.isIdentifier(basename)) {
				throw new ExitException(EXIT_INVALID_INPUT,
						"'" + basename + "' is not a valid class name in java. Remove the special characters");
			}
		}

		Files.createDirectories(outFile.getParent());
		try (BufferedWriter writer = Files.newBufferedWriter(outFile)) {
			TemplateInstance templateWithData = template.instance();
			properties.forEach(templateWithData::data);
			Util.verboseMsg("Rendering template: " + templateRef + " with properties: " + properties);
			String result = templateWithData.render();

			writer.write(result);
			outFile.toFile().setExecutable(true);
		}
	}

	String getRenderedScriptOrFile(Map<String, String> fileRefs, List<RefTarget> refTargets, Path outDir,
			boolean absolute) {
		Optional<Map.Entry<String, String>> optionalFileRefEntry = fileRefs.entrySet()
			.stream()
			.filter(fileRef -> dev.jbang.cli.Template.TPL_BASENAME_PATTERN.matcher(
					fileRef.getKey())
				.find())
			.findFirst();
		if (optionalFileRefEntry.isPresent()) {
			Optional<RefTarget> optionalRefTarget = refTargets.stream()
				.filter(refTarget -> refTarget.getSource()
					.getOriginalResource()
					.endsWith(
							optionalFileRefEntry.get()
								.getValue()))
				.findFirst();
			if (optionalRefTarget.isPresent()) {
				Path path = optionalRefTarget.get().to(outDir);
				if (absolute) {
					return path.toString();
				} else {
					return Util.getCwd().relativize(path).toString();
				}
			}
		}
		return scriptOrFile;
	}
}
