package dev.jbang.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;

import dev.jbang.resources.resolvers.AliasResourceResolver;
import dev.jbang.resources.resolvers.LiteralScriptResourceResolver;
import dev.jbang.source.BuildContext;
import dev.jbang.source.CmdGeneratorBuilder;
import dev.jbang.source.Project;
import dev.jbang.source.ProjectBuilder;
import dev.jbang.source.Source;
import dev.jbang.util.Util;

@CommandDefinition(name = "run", description = "Builds and runs provided script. (default command)", generateHelp = true, stopAtFirstPositional = true, helpGroup = "Essentials")
public class Run extends BaseBuildCommand {

	/**
	 * A PrintStream that always writes to the real stdout, bypassing any
	 * System.setOut() redirection. Used to output the command line for the parent
	 * process to execute.
	 */
	public PrintStream realOut = new PrintStream(new FileOutputStream(FileDescriptor.out));

	@Mixin
	public RunMixin runMixin;

	@Option(shortName = 'c', name = "code", fallbackValue = "", description = "Run the given string as code")
	public String literalScript;

	@Arguments(paramLabel = "userParams", index = "1..*", arity = "0..*", description = "Parameters for the script")
	public List<String> userParams;

	@Override
	public void afterParse() {
		super.afterParse();
		runMixin.resolveAfterParse();
		if (userParams == null) {
			userParams = new ArrayList<>();
		}
	}

	protected void requireScriptArgument() {
		if (scriptMixin.scriptOrFile == null && ((runMixin.interactive != Boolean.TRUE && literalScript == null)
				|| (literalScript != null && literalScript.isEmpty()))) {
			if (commandInvocation != null) {
				System.err.println(commandInvocation.getHelpInfo());
			}
			throw new ExitException(EXIT_INVALID_INPUT, "Missing required parameter: '<scriptOrFile>'");
		}
	}

	private void rewriteScriptArguments() {
		if (literalScript != null && !literalScript.isEmpty()
				&& scriptMixin.scriptOrFile != null) {
			List<String> args = new ArrayList<>();
			args.add(scriptMixin.scriptOrFile);
			if (userParams != null) {
				args.addAll(userParams);
			}
			userParams = args;
		}
	}

	@Override
	public Integer doCall() throws IOException {
		requireScriptArgument();
		rewriteScriptArguments();
		userParams = handleRemoteFiles(userParams);
		String script = scriptMixin.scriptOrFile;

		ProjectBuilder pb = createProjectBuilderForRun();

		Project prj;
		if (literalScript != null) {
			String code;
			if (!literalScript.isEmpty()) {
				code = literalScript;
			} else {
				code = script;
			}
			Util.verboseMsg("Literal Script to execute: '" + code + "'");
			prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, code, scriptMixin.getForceType()));
		} else {
			if (script != null) {
				prj = pb.build(script);
			} else {
				// HACK it's a crappy way to work around the fact that in the case of
				// interactive we might not have a file to reference but all the code
				// expects one to exist
				prj = pb.build(LiteralScriptResourceResolver.stringToResourceRef(null, "", scriptMixin.getForceType()));
			}
		}

		if (Boolean.TRUE.equals(nativeMixin.nativeImage)
				&& (scriptMixin.getForceType() == Source.Type.jshell || prj.isJShell())) {
			warn(".jsh cannot be used with --native thus ignoring --native.");
			prj.setNativeImage(false);
		}

		BuildContext ctx = BuildContext.forProject(prj, getBuildDir());
		CmdGeneratorBuilder genb = Project.codeBuilder(ctx).build();

		buildAgents(ctx);

		String cmdline = updateGeneratorForRun(genb).build().generate();

		Util.verboseMsg("run: " + cmdline);
		realOut.println(cmdline);

		return EXIT_EXECUTE;
	}

	void buildAgents(BuildContext ctx) throws IOException {
		Project prj = ctx.getProject();
		Map<String, String> agents = runMixin.javaAgentSlots;
		if (agents == null && prj.getResourceRef() instanceof AliasResourceResolver.AliasedResourceRef) {
			AliasResourceResolver.AliasedResourceRef aref = (AliasResourceResolver.AliasedResourceRef) prj
				.getResourceRef();
			if (aref.getAlias().javaAgents != null) {
				Map<String, String> tmpAgents = new HashMap<>();
				aref.getAlias().javaAgents.forEach(a -> tmpAgents.put(a.agentRef, a.options));
				agents = tmpAgents;
			}
		}
		if (agents != null) {
			if (runMixin.javaRuntimeOptions == null) {
				runMixin.javaRuntimeOptions = new ArrayList<>();
			}
			agents = handleRemoteFiles(agents);
			for (Map.Entry<String, String> agentOption : agents.entrySet()) {
				String javaAgent = agentOption.getKey();
				String javaAgentOptions = agentOption.getValue();
				ProjectBuilder apb = createBaseProjectBuilder();
				Project aprj = apb.build(javaAgent);
				BuildContext actx = BuildContext.forProject(aprj);
				Project.codeBuilder(actx).build();
				runMixin.javaRuntimeOptions.addAll(javaAgentOptions(actx, javaAgentOptions));
			}
		}
	}

	private List<String> javaAgentOptions(BuildContext agentCtx, String agentOptions) {
		return Collections.singletonList(
				"-javaagent:" + agentCtx.getJarFile() + (agentOptions != null ? "=" + agentOptions : ""));
	}

	ProjectBuilder createProjectBuilderForRun() {
		return createBaseProjectBuilder();
	}

	CmdGeneratorBuilder updateGeneratorForRun(CmdGeneratorBuilder gb) {
		gb
			.setArguments(userParams)
			.runtimeOptions(runMixin.javaRuntimeOptions)
			.mainClass(buildMixin.main)
			.moduleName(buildMixin.module)
			.interactive(runMixin.interactive)
			.enableAssertions(runMixin.enableAssertions)
			.enableSystemAssertions(runMixin.enableSystemAssertions)
			.flightRecorderString(runMixin.flightRecorderString)
			.debugString(runMixin.debugString)
			.classDataSharing(runMixin.getCds());

		return gb;
	}

	private static List<String> handleRemoteFiles(List<String> args) {
		return args.stream().map(Util::substituteRemote).collect(Collectors.toList());
	}

	private static Map<String, String> handleRemoteFiles(Map<String, String> slots) {
		Map<String, String> result = new HashMap<>();
		slots.forEach((key, value) -> result.put(key, Util.substituteRemote(value)));
		return result;
	}
}
