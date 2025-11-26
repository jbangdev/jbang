package dev.jbang.filesystem.github;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

/**
 * Path matcher for GitHub filesystem.
 */
class GitHubPathMatcher implements PathMatcher {

	private final GitHubFileSystem fileSystem;
	private final String syntax;
	private final Pattern pattern;

	GitHubPathMatcher(GitHubFileSystem fileSystem, String syntax, String pattern) {
		this.fileSystem = fileSystem;
		this.syntax = syntax;
		if ("glob".equals(syntax)) {
			this.pattern = globToRegex(pattern);
		} else {
			this.pattern = Pattern.compile(pattern);
		}
	}

	@Override
	public boolean matches(@NonNull Path path) {
		if (!(path instanceof GitHubPath)) {
			return false;
		}
		String pathStr = path.toString();
		return pattern.matcher(pathStr).matches();
	}

	private Pattern globToRegex(String glob) {
		StringBuilder regex = new StringBuilder();
		boolean inGroup = false;
		int i = 0;
		while (i < glob.length()) {
			char c = glob.charAt(i);
			switch (c) {
			case '*':
				if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
					regex.append(".*");
					i += 2;
				} else {
					regex.append("[^/]*");
					i++;
				}
				break;
			case '?':
				regex.append("[^/]");
				i++;
				break;
			case '[':
				regex.append('[');
				i++;
				break;
			case ']':
				regex.append(']');
				i++;
				break;
			case '{':
				regex.append('(');
				inGroup = true;
				i++;
				break;
			case '}':
				regex.append(')');
				inGroup = false;
				i++;
				break;
			case ',':
				if (inGroup) {
					regex.append('|');
				} else {
					regex.append(',');
				}
				i++;
				break;
			case '\\':
				if (i + 1 < glob.length()) {
					regex.append('\\').append(glob.charAt(i + 1));
					i += 2;
				} else {
					regex.append('\\');
					i++;
				}
				break;
			default:
				if (".+*?^${}[]|()".indexOf(c) >= 0) {
					regex.append('\\');
				}
				regex.append(c);
				i++;
				break;
			}
		}
		return Pattern.compile(regex.toString());
	}
}

