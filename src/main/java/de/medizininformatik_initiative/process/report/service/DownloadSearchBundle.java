package de.medizininformatik_initiative.process.report.service;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.BasicFhirWebserviceClient;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class DownloadSearchBundle extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(DownloadSearchBundle.class);

	private final ReportStatusGenerator statusGenerator;
	private final DataLogger dataLogger;

	public DownloadSearchBundle(ProcessPluginApi api, ReportStatusGenerator statusGenerator, DataLogger dataLogger)
	{
		super(api);

		this.statusGenerator = statusGenerator;
		this.dataLogger = dataLogger;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(statusGenerator, "statusGenerator");
		Objects.requireNonNull(dataLogger, "dataLogger");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		Target target = variables.getTarget();
		String searchBundleIdentifier = ConstantsReport.CODESYSTEM_REPORT + "|"
				+ ConstantsReport.CODESYSTEM_REPORT_VALUE_SEARCH_BUNDLE;

		logger.info("Downloading search Bundle '{}' from HRP '{}' referenced in Task with id '{}'",
				searchBundleIdentifier, target.getOrganizationIdentifierValue(), task.getId());

		try
		{
			Bundle bundle = searchSearchBundle(target, searchBundleIdentifier);
			dataLogger.logResource("Search Response", bundle);

			Bundle searchBundle = extractSearchBundle(bundle, searchBundleIdentifier, task.getId());
			dataLogger.logResource("Search Bundle", searchBundle);

			variables.setResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE, searchBundle);
		}
		catch (Exception exception)
		{
			if (exception instanceof WebApplicationException webException)
			{
				String statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_REACHABLE;

				if (webException.getResponse() != null
						&& webException.getResponse().getStatus() == Response.Status.FORBIDDEN.getStatusCode())
				{
					statusCode = ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_NOT_ALLOWED;
				}

				task.addOutput(statusGenerator.createReportStatusOutput(statusCode, "Download search bundle failed"));
				variables.updateTask(task);
			}

			logger.warn(
					"Error while reading search Bundle with identifier '{}' from organization '{}' referenced in Task with id '{}' - {}",
					searchBundleIdentifier, task.getRequester().getIdentifier().getValue(), task.getId(),
					exception.getMessage());
			throw new RuntimeException(
					"Error while reading search Bundle with identifier '" + searchBundleIdentifier
							+ "' from organization '" + task.getRequester().getIdentifier().getValue()
							+ "' referenced in Task with id '" + task.getId() + "' - " + exception.getMessage(),
					exception);
		}
	}

	private Bundle searchSearchBundle(Target target, String searchBundleIdentifier)
	{
		BasicFhirWebserviceClient client = api.getFhirWebserviceClientProvider()
				.getWebserviceClient(target.getEndpointUrl())
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);

		return client.searchWithStrictHandling(Bundle.class,
				Map.of("identifier", Collections.singletonList(searchBundleIdentifier)));
	}

	private Bundle extractSearchBundle(Bundle bundle, String searchBundleIdentifier, String taskId)
	{
		if (bundle.getTotal() != 1 && !(bundle.getEntryFirstRep().getResource() instanceof Bundle))
			throw new IllegalStateException(
					"Expected a bundle from the HRP with one entry being a search Bundle with identifier '"
							+ searchBundleIdentifier + "' but found " + bundle.getTotal() + " for Task with id '"
							+ taskId + "'");

		return (Bundle) bundle.getEntryFirstRep().getResource();
	}
}
