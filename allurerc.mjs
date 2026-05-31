// Allure 3 report configuration file.
//
// NOTE: When generating reports via `./gradlew allureReport`, the Allure Gradle
// plugin 4.x generates its own config file and passes it to the Allure 3 CLI
// via --config. This file is therefore NOT automatically picked up by Gradle.
// It IS used when running the Allure 3 CLI directly (e.g. `allure generate ...`).
//
// The Gradle plugin default already uses the "awesome" plugin with
// groupBy defaulting to ["parentSuite", "suite", "subSuite"], which groups
// tests by platform (OS/arch/Java) at the top level — matching our intent.
//
// Custom grouping hierarchy:
//   Level 1: parentSuite label  — OS/arch/Java version (set per-job in CI)
//   Level 2: tag label          — "unit-test" or "integration-test"
//
// Reference: https://allurereport.org/docs/how-it-works-report-configuration/
export default {
    name: "JBang Test Report",
    plugins: {
        awesome: {
            options: {
                groupBy: ["parentSuite", "tag"],
            },
        },
    },
};
