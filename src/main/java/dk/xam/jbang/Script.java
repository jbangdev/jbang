package dk.xam.jbang;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Script {

	private String DEPS_COMMENT_PREFIX = "//DEPS ";
	
			
	private String script;

	Script(String content) {
		this.script = content;
	}

	
	
	public List<String> collectDependencies() {

		List<String> lines = Arrays.asList(script.split("\\r?\\n"));

		
		// Make sure that dependencies declarations are well formatted
	    if (lines.stream().anyMatch(it -> it.startsWith("// DEPS"))) {
	        throw new IllegalArgumentException("Dependencies must be declared by using the line prefix //DEPS");
	    }

	    List<String> dependencies = lines.stream()
	    		.filter(it -> isDependDeclare(it))
	    		.flatMap(it -> extractDependencies(it)).collect(Collectors.toList());
	    				
	    return dependencies;
	}



	Stream<String> extractDependencies(String line) {
	    if(line.startsWith(DEPS_COMMENT_PREFIX)) {
	    	return Arrays.stream(line.split("[ ;,]+")).skip(1).map(String::trim);
	    }
        
        return Stream.of();
	}



	boolean isDependDeclare(String line) {
		return line.startsWith(DEPS_COMMENT_PREFIX);
	}
	
	
	
}
