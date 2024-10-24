package de.medizininformatik_initiative.process.report.service;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.PreferReturnMinimal;

public class CreateDashboardReport extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CreateDashboardReport.class);

	private final String resourceVersion;
	private final FhirClientFactory fhirClientFactory;
	private final DataLogger dataLogger;

	public CreateDashboardReport(ProcessPluginApi api, String resourceVersion, FhirClientFactory fhirClientFactory,
			DataLogger dataLogger)
	{
		super(api);

		this.resourceVersion = resourceVersion;
		this.fhirClientFactory = fhirClientFactory;
		this.dataLogger = dataLogger;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(resourceVersion, "resourceVersion");
		Objects.requireNonNull(fhirClientFactory, "fhirClientFactory");
		Objects.requireNonNull(dataLogger, "dataLogger");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		Target target = variables.getTarget();

		String ddpJson = variables.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_DASHBOARD_REPORT_DDP_JSON);
		try
		{
			System.out.println("Target: " + target.getOrganizationIdentifierValue());
			System.out.println("CreateDashboardReport.doExecute() - 1");

			Bundle responseBundle = new Bundle();
			responseBundle.setType(Bundle.BundleType.BATCHRESPONSE);
			responseBundle.getMeta().setLastUpdated(new Date());

			Bundle.BundleEntryComponent bec = new Bundle.BundleEntryComponent();
			QuestionnaireResponseItemComponent qric = new QuestionnaireResponseItemComponent();
			QuestionnaireResponseItemAnswerComponent qriac = new QuestionnaireResponseItemAnswerComponent();
			qriac.setValue(new StringType(ddpJson));
			qric.addAnswer(qriac);
			QuestionnaireResponse qr = new QuestionnaireResponse();
			qr.addItem(qric);
			bec.setResource(qr);
			responseBundle.addEntry(bec);

			String resp = FhirContext.forR4().newJsonParser().setPrettyPrint(true)
					.encodeResourceToString(responseBundle);
			System.out.println(resp);

			System.out.println("CreateDashboardReport.doExecute() - 3");
			String reportReference = storeReportBundle(responseBundle, target.getOrganizationIdentifierValue(),
					task.getId());

			System.out.println("CreateDashboardReport.doExecute() - 4");
			variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_DASHBOARD_REPORT_DDP_JSON_RESPONSE_REFERENCE,
					reportReference);

			System.out.println("CreateDashboardReport.doExecute(): Finish");
		}
		catch (Exception exception)
		{
			logger.warn("Could not create report for HRP '{}' in Task with id '{}' - {}",
					target.getOrganizationIdentifierValue(), task.getId(), exception.getMessage());
			throw new RuntimeException("Could not create report for HRP '" + target.getOrganizationIdentifierValue()
					+ "' in Task with id '" + task.getId() + "' - " + exception.getMessage(), exception);
		}
	}

	private String storeReportBundle(Bundle responseBundle, String hrpIdentifier, String taskId)
	{
		System.out.println("CreateDashboardReport.storeReportBundle()");
		System.out.println("CreateDashboardReport.storeReportBundle(): HRP '" + hrpIdentifier + "' and Task with id '"
				+ taskId + "'");
		System.out
				.println("Base URL: " + api.getFhirWebserviceClientProvider().getLocalWebserviceClient().getBaseUrl());

		PreferReturnMinimal client = api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
				.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);

		System.out.println("CreateDashboardReport.storeReportBundle() - 1");
		String localOrganizationIdentifier = api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
				.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue empty"));
		System.out.println("CreateDashboardReport.storeReportBundle() - 2");
		IdType bundleIdType = client.updateConditionaly(responseBundle, Map.of("identifier", Collections.singletonList(
				ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER + "|" + localOrganizationIdentifier)));
		System.out.println("CreateDashboardReport.storeReportBundle() - 3");
		String absoluteId = new IdType(api.getEndpointProvider().getLocalEndpointAddress(), ResourceType.Bundle.name(),
				bundleIdType.getIdPart(), bundleIdType.getVersionIdPart()).getValue();

		logger.info("Stored report Bundle with id '{}' for HRP '{}' and Task with id '{}'", absoluteId, hrpIdentifier,
				taskId);
		System.out.println("Stored report Bundle with id '" + absoluteId + "' for HRP '" + hrpIdentifier
				+ "' and Task with id '" + taskId + "'");

		return absoluteId;
	}
}
