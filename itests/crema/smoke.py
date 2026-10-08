#!/usr/bin/env python3
"""Opt-in native smoke tests: python3 itests/crema/smoke.py /path/to/jbang.bin"""
import pathlib
import subprocess
import sys
import tempfile


binary = pathlib.Path(sys.argv[1]).resolve()


def run(source, expected_status=0, expected_output=None, arguments=()):
    result = subprocess.run(
        [str(binary), "crema", str(source), *arguments],
        text=True, capture_output=True, timeout=180,
    )
    assert result.returncode == expected_status, (result.returncode, result.stdout, result.stderr)
    if expected_output is not None:
        assert result.stdout.strip() == expected_output, (result.stdout, result.stderr)


with tempfile.TemporaryDirectory(prefix="jbang-crema-") as temporary:
    root = pathlib.Path(temporary)
    hello = root / "Hello.java"
    hello.write_text(
        'public class Hello { public static void main(String[] args) { '
        'System.out.println(String.join("|", args)); } }\n'
    )
    for _ in range(2):
        run(hello, expected_output="two words|--flag", arguments=("two words", "--flag"))

    dependency = root / "Dependency.java"
    dependency.write_text(
        '//DEPS com.google.code.gson:gson:2.8.9\n'
        'public class Dependency { public static void main(String[] args) { '
        'System.out.println(new com.google.gson.Gson().toJson("dependency")); } }\n'
    )
    run(dependency, expected_output='"dependency"')

    (root / "message.txt").write_text("resource")
    resource = root / "Resource.java"
    resource.write_text(
        '//FILES message.txt\n'
        'public class Resource { public static void main(String[] args) throws Exception { '
        'Thread worker = new Thread(() -> { try { '
        'System.out.println(new String(Thread.currentThread().getContextClassLoader()'
        '.getResourceAsStream("message.txt").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); '
        '} catch (Exception e) { throw new RuntimeException(e); } }); '
        'worker.start(); worker.join(); } }\n'
    )
    run(resource, expected_output="resource")

    exit_source = root / "Exit.java"
    exit_source.write_text(
        'public class Exit { public static void main(String[] args) { System.exit(7); } }\n'
    )
    run(exit_source, expected_status=7)

    broken = root / "Broken.java"
    broken.write_text(
        'public class Broken { public static void main(String[] args) { '
        'throw new IllegalStateException("boom"); } }\n'
    )
    run(broken, expected_status=1)

print("Crema native smoke tests passed")
