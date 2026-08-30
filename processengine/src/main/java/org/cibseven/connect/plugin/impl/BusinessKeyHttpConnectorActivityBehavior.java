package org.cibseven.connect.plugin.impl;

import org.cibseven.bpm.engine.impl.core.variable.mapping.IoMapping;
import org.cibseven.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.cibseven.connect.httpclient.HttpBaseRequest;
import org.cibseven.connect.spi.ConnectorRequest;

public class BusinessKeyHttpConnectorActivityBehavior extends ServiceTaskConnectorActivityBehavior {

    public static final String BUSINESS_KEY_HEADER = "Business-Key";

    public BusinessKeyHttpConnectorActivityBehavior(String connectorId, IoMapping ioMapping) {
        super(connectorId, ioMapping);
    }

    @Override
    protected void applyInputParameters(ActivityExecution execution, ConnectorRequest<?> request) {
        super.applyInputParameters(execution, request);
        if (request instanceof HttpBaseRequest<?, ?> httpRequest) {
            httpRequest.header(BUSINESS_KEY_HEADER, execution.getProcessBusinessKey());
        }
    }
}
