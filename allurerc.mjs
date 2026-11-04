// Allure 3 report configuration.
//
// The Gradle allureReport task is configured (in build.gradle) to use this file
// via --config, overriding the plugin's auto-generated allurerc.json.
//
// Environments: each CI matrix cell produces a distinct parentSuite label
// (e.g. "Linux-amd64-21.0.10-unit-test-jvm-17-abc1234"). The matchers below
// group results by OS so the Allure 3 Environments view shows a side-by-side
// comparison across platforms.
//
// Reference: https://allurereport.org/docs/v3/configure/

export default {
	name: "JBang Test Report",
	plugins: {
		awesome: {
			options: {
				singleFile: true,
				groupBy: ["parentSuite", "suite", "subSuite"],
			},
		},
	},
	environments: {
		linux: {
			name: "Linux",
			matcher: ({ labels }) =>
				labels.some(
					(l) => l.name === "parentSuite" && /^Linux/i.test(l.value),
				),
		},
		macos: {
			name: "macOS",
			matcher: ({ labels }) =>
				labels.some(
					(l) =>
						l.name === "parentSuite" &&
						/^Mac/i.test(l.value),
				),
		},
		windows: {
			name: "Windows",
			matcher: ({ labels }) =>
				labels.some(
					(l) =>
						l.name === "parentSuite" &&
						/^Windows/i.test(l.value),
				),
		},
	},
};
