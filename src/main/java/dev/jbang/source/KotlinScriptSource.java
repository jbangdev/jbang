package dev.jbang.source;

import dev.jbang.net.KotlinManager;
import org.jboss.jandex.ClassInfo;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static dev.jbang.net.KotlinManager.resolveInKotlinHome;

public class KotlinScriptSource extends ScriptSource {

    protected KotlinScriptSource(ResourceRef script) {
        super(script);
    }

    @Override
    public List<String> getCompileOptions() {
        return Collections.emptyList();
    }

    @Override
    protected String getCompilerBinary(String requestedJavaVersion) {
        return resolveInKotlinHome("kotlinc", getKotlinVersion());
    }

    @Override
    protected Predicate<ClassInfo> getMainFinder() {
        return pubClass -> pubClass.method("main", ScriptSource.STRINGARRAYTYPE) != null
                           || pubClass.method("main") != null;
    }

    @Override
    protected String getExtension() {
        return ".kt";
    }

    public String getKotlinVersion() {
        return collectAll((s) -> collectOptions("KOTLIN"))
                   .stream().findFirst()
                   .orElse(KotlinManager.DEFAULT_KOTLIN_VERSION);
    }
}
