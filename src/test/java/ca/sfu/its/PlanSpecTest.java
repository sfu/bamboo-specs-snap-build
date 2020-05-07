package ca.sfu.its;

import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.util.EntityPropertiesBuilders;
import org.junit.Test;

public class PlanSpecTest {
    @Test
    public void checkYourPlanOffline() {
        Plan plan = new PlanSpec().createPlan();

        EntityPropertiesBuilders.build(plan);
    }
}
