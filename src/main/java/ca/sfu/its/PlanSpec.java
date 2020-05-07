package ca.sfu.its;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.AtlassianModule;
import com.atlassian.bamboo.specs.api.builders.BambooKey;
import com.atlassian.bamboo.specs.api.builders.BambooOid;
import com.atlassian.bamboo.specs.api.builders.docker.DockerConfiguration;
import com.atlassian.bamboo.specs.api.builders.notification.AnyNotificationRecipient;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.PlanIdentifier;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.ArtifactSubscription;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.ConcurrentBuilds;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.builders.notification.PlanCompletedNotification;
import com.atlassian.bamboo.specs.builders.notification.PlanStatusChangedNotification;
import com.atlassian.bamboo.specs.builders.task.CheckoutItem;
import com.atlassian.bamboo.specs.builders.task.DumpVariablesTask;
import com.atlassian.bamboo.specs.builders.task.ScriptTask;
import com.atlassian.bamboo.specs.builders.task.TestParserTask;
import com.atlassian.bamboo.specs.builders.task.VcsCheckoutTask;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import com.atlassian.bamboo.specs.util.BambooServer;

@BambooSpec
public class PlanSpec {

  DockerConfiguration dockerConfig =
      new DockerConfiguration().image("simonfraseruniversity/snap-build-deploy:1588889138")
          .volume("/home/bamboo/.ssh", "/home/bamboo/.ssh");

  public Plan createPlan() {
    final Plan plan = new Plan(new Project().oid(new BambooOid("gfkbnmmhsbur"))
        .key(new BambooKey("SNAP")).name("SFU Snap"), "Web App", new BambooKey("WEBAPP"))
            .oid(new BambooOid("gfamg199yjgk")).description("SFU Snap Web Application")
            .pluginConfigurations(new ConcurrentBuilds())
            .stages(new Stage("Checkout").jobs(new Job("Checkout", new BambooKey("CHECKOUT"))
                .artifacts(new Artifact().name("Code with Dependencies").copyPattern("snap.tar")
                    .shared(true))
                .tasks(new DumpVariablesTask(),
                    new ScriptTask().description("Post message to Slack channel").inlineBody(
                        "curl -s -X POST --data-urlencode 'payload={\"channel\": \"#snap-web-ci\", \"attachments\": [{\"color\": \"warning\",\"text\": \"<SFU Snap \u203A Web App \u203A ${bamboo.planRepository.1.branchName} \u203A ${bamboo.buildResultsUrl}|#${bamboo.buildNumber}> started. Manual run by ${bamboo.ManualBuildTriggerReason.userName}\"}]}' https://hooks.slack.com/services/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acurl -X POST --data-urlencode 'payload={\"channel\": \"#snap-web-ci\", \"attachments\": [{\"color\": \"warning\",\"text\": \"<${bamboo.buildResultsUrl}|SFU Snap &gt; Web App &gt; #${bamboo.buildNumber}> build started.\"}]}' https://hooks.slack.com/services/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"),
                    new VcsCheckoutTask().description("Checkout Default Repository")
                        .checkoutItems(new CheckoutItem().defaultRepository()).cleanCheckout(true),
                    new ScriptTask().description("Install Dependencies").inlineBody("yarn install"),
                    new ScriptTask().description("Tarball").inlineBody("tar cf snap.tar ."))
                .dockerConfiguration(dockerConfig)),
                new Stage("Run Tests").jobs(new Job("Run Unit Tests", new BambooKey("UNIT")).tasks(
                    new ScriptTask().description("Untar").inlineBody(
                        "find . -not -name 'snap.tar' -delete\ntar xf snap.tar && rm snap.tar"),
                    new ScriptTask().description("Run Tests").inlineBody("npm test")
                        .environmentVariables(
                            "CAS_BASE_URL=https://cas DATABASE_URL=postgres://foo:bar@localhost/wharrgarbl"),
                    new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                        .resultDirectories("./"))
                    .artifactSubscriptions(
                        new ArtifactSubscription().artifact("Code with Dependencies"))
                    .dockerConfiguration(dockerConfig)),
                new Stage("Build").jobs(
                    new Job("Build for Production Env", new BambooKey("BUILDPROD"))
                        .artifacts(new Artifact().name("Built Application Tarball - Prod")
                            .copyPattern("snap-release-production.tar").shared(true))
                        .tasks(new ScriptTask().description("Untar").inlineBody(
                            "find . -not -name 'snap.tar' -delete\ntar xf snap.tar\nrm snap.tar"),
                            new ScriptTask().description("SCP .env.production.test from mp01")
                                .inlineBody(
                                    "scp -i /home/bamboo/.ssh/snap-production snapuser@lcp-snap-mp01.dc.sfu.ca:/usr/local/snap/config/home/snapuser/snap/shared/config/env.production.prod .env.production"),
                            new ScriptTask().description("Build").inlineBody("npm run build")
                                .environmentVariables(
                                    "NODE_ENV=production BUILD=${bamboo.planKey}-${bamboo.buildNumber}"),
                            new ScriptTask().description("Create tarball").inlineBody(
                                "tar cf snap-release-production.tar --exclude=./snap-release-production.tar ."))
                        .artifactSubscriptions(
                            new ArtifactSubscription().artifact("Code with Dependencies"))
                        .dockerConfiguration(dockerConfig),
                    new Job("Build for Test Env", new BambooKey("BUILDTEST"))
                        .artifacts(new Artifact().name("Built Application Tarball - Test")
                            .copyPattern("snap-release-test.tar").shared(true))
                        .tasks(new ScriptTask().description("Untar").inlineBody(
                            "find . -not -name 'snap.tar' -delete\ntar xf snap.tar\nrm snap.tar"),
                            new ScriptTask().description("SCP .env.production.test from mt01")
                                .inlineBody(
                                    "scp -i /home/bamboo/.ssh/snap-test snapuser@lcp-snap-mt01.dc.sfu.ca:/usr/local/snap/config/home/snapuser/snap/shared/config/env.production.test .env.production"),
                            new ScriptTask().description("Build").inlineBody("npm run build")
                                .environmentVariables(
                                    "NODE_ENV=production BUILD=${bamboo.planKey}-${bamboo.buildNumber}"),
                            new ScriptTask().description("Create tarball").inlineBody(
                                "tar cf snap-release-test.tar --exclude=./snap-release-test.tar ."))
                        .artifactSubscriptions(
                            new ArtifactSubscription().artifact("Code with Dependencies"))
                        .dockerConfiguration(dockerConfig)))
            .linkedRepositories("sfu/snap")

            .planBranchManagement(new PlanBranchManagement().createForVcsBranch()
                .delete(new BranchCleanup()).notificationForCommitters())
            .notifications(new Notification().type(new PlanCompletedNotification())
                .recipients(new AnyNotificationRecipient(new AtlassianModule(
                    "com.atlassian.bamboo.plugins.bamboo-slack:recipient.slack")).recipientString(
                        "https://hooks.slack.com/services/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|||")),
                new Notification().type(new PlanStatusChangedNotification())
                    .recipients(new AnyNotificationRecipient(new AtlassianModule(
                        "com.atlassian.bamboo.plugins.bamboo-slack:recipient.slack"))
                            .recipientString(
                                "https://hooks.slack.com/services/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|||")));
    return plan;
  }

  public PlanPermissions planPermission() {
    final PlanPermissions planPermission =
        new PlanPermissions(new PlanIdentifier("SNAP", "WEBAPP")).permissions(new Permissions()
            .userPermissions("grahamb", PermissionType.EDIT, PermissionType.VIEW,
                PermissionType.ADMIN, PermissionType.CLONE, PermissionType.BUILD)
            .groupPermissions("lcp-staff", PermissionType.VIEW)
            .loggedInUserPermissions(PermissionType.VIEW).anonymousUserPermissionView());
    return planPermission;
  }

  public static void main(String... argv) {
    // By default credentials are read from the '.credentials' file.
    BambooServer bambooServer = new BambooServer("https://bamboo-nsx.its.sfu.ca");
    final PlanSpec planSpec = new PlanSpec();

    final Plan plan = planSpec.createPlan();
    bambooServer.publish(plan);

    final PlanPermissions planPermission = planSpec.planPermission();
    bambooServer.publish(planPermission);
  }
}
