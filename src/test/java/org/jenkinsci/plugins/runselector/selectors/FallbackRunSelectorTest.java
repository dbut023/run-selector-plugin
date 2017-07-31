package org.jenkinsci.plugins.runselector.selectors;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.RandomStringUtils;
import org.jenkinsci.plugins.runselector.RunSelector;
import org.jenkinsci.plugins.runselector.context.RunSelectorContext;
import org.jenkinsci.plugins.runselector.steps.SelectRunStepTest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.jenkinsci.plugins.runselector.steps.SelectRunStepTest.assumeSymbolDependencies;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link StatusRunSelector}.
 *
 * @author Alexandru Somai
 */
public class FallbackRunSelectorTest {

    @ClassRule
    public static final JenkinsRule j = new JenkinsRule();

    private static WorkflowJob jobToSelect;
    private static WorkflowRun successRun;
    private static WorkflowRun unstableRun;
    private static WorkflowRun failureRun;
    private static WorkflowRun abortedRun;

    @BeforeClass
    @SuppressWarnings("Duplicates")
    public static void setUp() throws Exception {
        jobToSelect = j.jenkins.createProject(WorkflowJob.class, RandomStringUtils.randomAlphanumeric(7));

        jobToSelect.setDefinition(new CpsFlowDefinition("currentBuild.result = 'SUCCESS'"));
        successRun = jobToSelect.scheduleBuild2(0).get();

        jobToSelect.setDefinition(new CpsFlowDefinition("currentBuild.result = 'FAILURE'"));
        failureRun = jobToSelect.scheduleBuild2(0).get();
    }

    @Test
    public void testFirstSelector() throws Exception {
        RunSelector selector = new FallbackRunSelector(new StatusRunSelector(StatusRunSelector.BuildStatus.STABLE),new StatusRunSelector(StatusRunSelector.BuildStatus.FAILED));
        verifySelectedRun(selector, successRun);
    }

    @Test
    public void testFollowingSelector() throws Exception {
        RunSelector selector = new FallbackRunSelector(new StatusRunSelector(StatusRunSelector.BuildStatus.UNSTABLE),new StatusRunSelector(StatusRunSelector.BuildStatus.STABLE));
        verifySelectedRun(selector, successRun);
    }

    @Test
    public void testWorkflowWithEntries() throws Exception {
        assumeSymbolDependencies();

        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, RandomStringUtils.randomAlphanumeric(7));
        job.setDefinition(new CpsFlowDefinition(String.format("" +
                        "def noRunFilter = [$class: 'org.jenkinsci.plugins.runselector.filters.NoRunFilter']\n" +
                        "def build = selectRun( job: '%s',\n" +
                        "                       selector: fallback([\n" +
                        "                           [runFilter: noRunFilter, runSelector: status('UNSTABLE')],\n" +
                        "                           [runFilter: noRunFilter, runSelector: status('STABLE')]\n]))\n" +
                        "assert build.id == '%s'",
                jobToSelect.getFullName(), successRun.getId())));

        j.assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    @Test
    public void testWorkflowWithSelectorsOnly() throws Exception {
        assumeSymbolDependencies();

        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, RandomStringUtils.randomAlphanumeric(7));
        job.setDefinition(new CpsFlowDefinition(String.format("" +
                        "def build = selectRun( job: '%s',\n" +
                        "                       selector: fallback([[$class:'StatusRunSelector,staus: 'UNSTABLE']]))\n" +
                        "assert build.id == '%s'",
                jobToSelect.getFullName(), unstableRun.getId())));

        j.assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    @Test
    public void testWorkflowWithSelectorSymbolsOnly() throws Exception {
        assumeSymbolDependencies();

        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, RandomStringUtils.randomAlphanumeric(7));
        job.setDefinition(new CpsFlowDefinition(String.format("" +
                        "def build = selectRun( job: '%s',\n" +
                        "                       selector: fallback([status('UNSTABLE'),status('STABLE')]))\n" +
                        "assert build.id == '%s'",
                jobToSelect.getFullName(), successRun.getId())));

        j.assertBuildStatusSuccess(job.scheduleBuild2(0));
    }

    private static void verifySelectedRun(RunSelector selector, Run expectedRun) throws Exception {
        FreeStyleProject selecter = j.createFreeStyleProject();

        Run run = j.assertBuildStatusSuccess(selecter.scheduleBuild2(0));
        Run selectedRun = selector.select(jobToSelect, new RunSelectorContext(j.jenkins, run, TaskListener.NULL));
        assertThat(selectedRun, is(expectedRun));
    }
}
