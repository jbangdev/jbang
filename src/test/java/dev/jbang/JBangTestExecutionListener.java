package dev.jbang;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class JBangTestExecutionListener implements TestExecutionListener {

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		System.err.println("##############################################");
		System.err.println("## BEFORE ALL TESTS");
		try {
			BaseTest.initBeforeAll();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		System.err.println("##############################################");
		TestExecutionListener.super.testPlanExecutionStarted(testPlan);
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan) {
		System.err.println("##############################################");
		System.err.println("## AFTER ALL TESTS");
		TestExecutionListener.super.testPlanExecutionFinished(testPlan);
		try {
			BaseTest.cleanupAfterAll();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		System.err.println("##############################################");
	}
}
