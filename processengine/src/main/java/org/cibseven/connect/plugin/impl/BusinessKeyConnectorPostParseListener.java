package org.cibseven.connect.plugin.impl;

import org.cibseven.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.cibseven.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.cibseven.bpm.engine.impl.pvm.process.ActivityImpl;
import org.cibseven.bpm.engine.impl.pvm.process.ScopeImpl;
import org.cibseven.bpm.engine.impl.util.xml.Element;

public class BusinessKeyConnectorPostParseListener extends AbstractBpmnParseListener {

    @Override
    public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope, ActivityImpl activity) {
        injectBusinessKeyBehavior(activity);
    }

    @Override
    public void parseSendTask(Element sendTaskElement, ScopeImpl scope, ActivityImpl activity) {
        injectBusinessKeyBehavior(activity);
    }

    @Override
    public void parseBusinessRuleTask(Element businessRuleTaskElement, ScopeImpl scope, ActivityImpl activity) {
        injectBusinessKeyBehavior(activity);
    }

    private void injectBusinessKeyBehavior(ActivityImpl activity) {
        ActivityBehavior behavior = activity.getActivityBehavior();
        if (behavior instanceof ServiceTaskConnectorActivityBehavior connectorBehavior
                && !(behavior instanceof BusinessKeyHttpConnectorActivityBehavior)) {
            activity.setActivityBehavior(new BusinessKeyHttpConnectorActivityBehavior(
                    connectorBehavior.connectorId, connectorBehavior.ioMapping));
        }
    }
}
