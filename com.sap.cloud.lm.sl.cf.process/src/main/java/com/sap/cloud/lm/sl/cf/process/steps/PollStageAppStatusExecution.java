package com.sap.cloud.lm.sl.cf.process.steps;

import static java.text.MessageFormat.format;

import org.cloudfoundry.client.lib.CloudControllerClient;
import org.cloudfoundry.client.lib.CloudControllerException;
import org.cloudfoundry.client.lib.CloudOperationException;
import org.cloudfoundry.client.lib.StartingInfo;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.PackageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.sap.cloud.lm.sl.cf.client.XsCloudControllerClient;
import com.sap.cloud.lm.sl.cf.core.cf.clients.RecentLogsRetriever;
import com.sap.cloud.lm.sl.cf.persistence.services.ProcessLoggerProvider;
import com.sap.cloud.lm.sl.cf.process.Constants;
import com.sap.cloud.lm.sl.cf.process.message.Messages;
import com.sap.cloud.lm.sl.cf.process.util.XMLValueFilter;
import com.sap.cloud.lm.sl.common.SLException;
import com.sap.cloud.lm.sl.common.util.Pair;

public class PollStageAppStatusExecution implements AsyncExecution {

    private static final Logger LOGGER = LoggerFactory.getLogger(PollStageAppStatusExecution.class);

    private RecentLogsRetriever recentLogsRetriever;

    public PollStageAppStatusExecution(RecentLogsRetriever recentLogsRetriever) {
        this.recentLogsRetriever = recentLogsRetriever;
    }

    @Override
    public AsyncExecutionState execute(ExecutionWrapper execution) {
        CloudApplication app = StepsUtil.getApp(execution.getContext());
        CloudControllerClient client = execution.getControllerClient();

        try {
            execution.getStepLogger()
                .debug(Messages.CHECKING_APP_STATUS, app.getName());

            Pair<PackageState, String> state = getStagingState(execution, client, app);
            ProcessLoggerProvider processLoggerProvider = execution.getStepLogger().getProcessLoggerProvider();
            StepsUtil.saveAppLogs(execution.getContext(), client, recentLogsRetriever, app, LOGGER, processLoggerProvider);
            if (!state._1.equals(PackageState.STAGED)) {
                return checkStagingState(execution, app, state);
            }

            execution.getStepLogger()
                .info(Messages.APP_STAGED, app.getName());
            return AsyncExecutionState.FINISHED;
        } catch (CloudOperationException coe) {
            CloudControllerException e = new CloudControllerException(coe);
            execution.getStepLogger()
                .error(e, Messages.ERROR_STAGING_APP_1, app.getName());
            throw e;
        } catch (SLException e) {
            execution.getStepLogger()
                .error(e, Messages.ERROR_STAGING_APP_1, app.getName());
            throw e;
        }
    }

    private Pair<PackageState, String> getStagingState(ExecutionWrapper execution, CloudControllerClient client, CloudApplication app) {
        if (client instanceof XsCloudControllerClient) {
            return reportStagingLogs(execution, client);
        } else {
            app = client.getApplication(app.getName());
            return new Pair<>(app.getPackageState(), app.getStagingError());
        }
    }

    private Pair<PackageState, String> reportStagingLogs(ExecutionWrapper execution, CloudControllerClient client) {
        try {
            StartingInfo startingInfo = StepsUtil.getStartingInfo(execution.getContext());
            int offset = (Integer) execution.getContext()
                .getVariable(Constants.VAR_OFFSET);
            String stagingLogs = client.getStagingLogs(startingInfo, offset);
            if (stagingLogs != null) {
                // Staging logs successfully retrieved
                stagingLogs = stagingLogs.trim();
                if (!stagingLogs.isEmpty()) {
                    // TODO delete filtering when parallel app push is implemented
                    stagingLogs = new XMLValueFilter(stagingLogs).getFiltered();
                    execution.getStepLogger()
                        .debug(stagingLogs);
                    offset += stagingLogs.length();
                    execution.getContext()
                        .setVariable(Constants.VAR_OFFSET, offset);
                }
                return new Pair<>(PackageState.PENDING, null);
            } else {
                // No more staging logs
                return new Pair<>(PackageState.STAGED, null);
            }
        } catch (CloudOperationException e) {
            // "400 Bad Request" might mean that staging had already finished
            if (e.getStatusCode()
                .equals(HttpStatus.BAD_REQUEST)) {
                return new Pair<>(PackageState.STAGED, null);
            } else {
                return new Pair<>(PackageState.FAILED, e.getMessage());
            }
        }
    }

    private AsyncExecutionState checkStagingState(ExecutionWrapper execution, CloudApplication app, Pair<PackageState, String> state) {

        if (state._1.equals(PackageState.FAILED)) {
            // Application staging failed
            String message = format(Messages.ERROR_STAGING_APP_2, app.getName(), state._2);
            execution.getStepLogger()
                .error(message);
            return AsyncExecutionState.ERROR;
        }
        // Application not staged yet, wait and try again unless it's a timeout.
        return AsyncExecutionState.RUNNING;
    }

}
