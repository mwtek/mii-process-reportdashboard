package de.medizininformatik_initiative.process.report.service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.service.QuestionnaireResponseHelper;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.PreferReturnMinimal;
import jakarta.ws.rs.core.Response;

public class CreateDashboardReport extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CreateDashboardReport.class);

	private static final String RESPONSE_OK = "200";

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

			System.out.println("CreateDashboardReport.doExecute() - 2");
			Bundle reportBundle = transformToReportBundle(new Bundle(), responseBundle, target);
			dataLogger.logResource("Report Bundle", reportBundle);

			System.out.println("CreateDashboardReport.doExecute() - 3");
			String reportReference = storeReportBundle(reportBundle, target.getOrganizationIdentifierValue(),
					task.getId());

			System.out.println("CreateDashboardReport.doExecute() - 4");
			variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE,
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

	private Bundle executeSearchBundle(Bundle searchBundle, String hrpIdentifier)
	{
		logger.info(
				"Executing search Bundle from HRP '{}' against FHIR store with base url '{}' - this could take a while...",
				hrpIdentifier, fhirClientFactory.getFhirClient().getFhirBaseUrl());

		Bundle responseBundle = new Bundle();
		responseBundle.setType(Bundle.BundleType.BATCHRESPONSE);

		searchBundle.getEntry().stream().filter(Bundle.BundleEntryComponent::hasRequest)
				.map(Bundle.BundleEntryComponent::getRequest)
				.filter(r -> r.hasUrl() && r.hasMethod() && Bundle.HTTPVerb.GET.equals(r.getMethod()))
				.map(Bundle.BundleEntryRequestComponent::getUrl).map(this::executeRequest)
				.forEach(responseBundle::addEntry);

		return responseBundle;
	}

	private Bundle.BundleEntryComponent executeRequest(String url)
	{
		Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();

		try
		{
			logger.debug("Executing report search request '{}'", url);

			Resource result = fhirClientFactory.getFhirClient().search(url);
			entry.setResource(result);
			entry.setResponse(new Bundle.BundleEntryResponseComponent().setStatus(RESPONSE_OK));
		}
		catch (BaseServerResponseException exception)
		{
			logger.warn("Could not execute report search request '{}' - {}", url, exception.getMessage());

			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR)
					.setCode(OperationOutcome.IssueType.EXCEPTION).setDiagnostics(exception.getMessage());
			Bundle.BundleEntryResponseComponent response = new Bundle.BundleEntryResponseComponent()
					.setStatus(String.valueOf(exception.getStatusCode())).setOutcome(outcome);

			entry.setResponse(response);
		}

		return entry;
	}

	private Bundle transformToReportBundle(Bundle searchBundle, Bundle responseBundle, Target target)
	{
		Bundle report = new Bundle();
		report.setMeta(responseBundle.getMeta());
		report.getMeta().addProfile(ConstantsReport.PROFILE_REPORT_SEARCH_BUNDLE_RESPONSE + "|" + resourceVersion);
		report.getMeta().setLastUpdated(new Date());
		report.setType(responseBundle.getType());

		report.setIdentifier(new Identifier().setSystem(ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER)
				.setValue(api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
						.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue empty"))));

		api.getReadAccessHelper().addLocal(report);
		api.getReadAccessHelper().addOrganization(report, target.getOrganizationIdentifierValue());

		for (int i = 0; i < searchBundle.getEntry().size(); i++)
		{
			Bundle.BundleEntryComponent responseEntry = responseBundle.getEntry().get(i);
			Bundle.BundleEntryComponent reportEntry = new Bundle.BundleEntryComponent();

			if (responseEntry.getResource() instanceof Bundle || !responseEntry.hasResource())
			{
				toEntryComponentBundleResource(responseEntry, reportEntry,
						searchBundle.getEntry().get(i).getRequest().getUrl());
			}

			if (responseEntry.getResource() instanceof CapabilityStatement)
			{
				toEntryComponentCapabilityStatementResource(responseEntry, reportEntry);
			}

			reportEntry.setResponse(responseEntry.getResponse());
			report.addEntry(reportEntry);
		}

		return report;
	}

	private void toEntryComponentBundleResource(Bundle.BundleEntryComponent responseEntry,
			Bundle.BundleEntryComponent reportEntry, String url)
	{
		Bundle reportEntryBundle = new Bundle();
		reportEntryBundle.getMeta().setLastUpdated(new Date());
		reportEntryBundle.addLink().setRelation("self").setUrl(url);
		reportEntryBundle.setType(Bundle.BundleType.SEARCHSET);
		reportEntryBundle.setTotal(0);

		if (responseEntry.getResource() instanceof Bundle responseEntryBundle)
		{
			reportEntryBundle.setTotal(responseEntryBundle.getTotal());
			reportEntryBundle.getMeta().setLastUpdated(responseEntryBundle.getMeta().getLastUpdated());
		}

		reportEntry.setResource(reportEntryBundle);
	}

	private void toEntryComponentCapabilityStatementResource(Bundle.BundleEntryComponent responseEntry,
			Bundle.BundleEntryComponent reportEntry)
	{
		CapabilityStatement responseEntryCapabilityStatement = (CapabilityStatement) responseEntry.getResource();
		CapabilityStatement reportEntryCapabilityStatement = new CapabilityStatement();

		reportEntryCapabilityStatement.setKind(CapabilityStatement.CapabilityStatementKind.CAPABILITY);
		reportEntryCapabilityStatement.setStatus(responseEntryCapabilityStatement.getStatus());
		reportEntryCapabilityStatement.setDate(responseEntryCapabilityStatement.getDate());
		reportEntryCapabilityStatement.setName("Server");

		reportEntryCapabilityStatement.getSoftware().setName(responseEntryCapabilityStatement.getSoftware().getName());
		reportEntryCapabilityStatement.getSoftware()
				.setVersion(responseEntryCapabilityStatement.getSoftware().getVersion());

		reportEntryCapabilityStatement.setFhirVersion(responseEntryCapabilityStatement.getFhirVersion());

		reportEntryCapabilityStatement.setFormat(responseEntryCapabilityStatement.getFormat().stream()
				.filter(f -> "application/fhir+xml".equals(f.getCode()) || "application/fhir+json".equals(f.getCode()))
				.collect(Collectors.toList()));

		for (CapabilityStatement.CapabilityStatementRestComponent oldRestComponent : responseEntryCapabilityStatement
				.getRest())
		{
			List<CapabilityStatement.CapabilityStatementRestResourceComponent> resources = oldRestComponent
					.getResource().stream().map(r -> new CapabilityStatement.CapabilityStatementRestResourceComponent()
							.setType(r.getType()).setSearchParam(removeDocumentation(r.getSearchParam())))
					.toList();

			CapabilityStatement.CapabilityStatementRestComponent newRestComponent = new CapabilityStatement.CapabilityStatementRestComponent()
					.setResource(resources).setMode(oldRestComponent.getMode())
					.setSearchParam(removeDocumentation(oldRestComponent.getSearchParam()));

			reportEntryCapabilityStatement.addRest(newRestComponent);
		}

		reportEntry.setResource(reportEntryCapabilityStatement);
	}

	private List<CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent> removeDocumentation(
			List<CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent> searchParams)
	{
		return searchParams.stream().map(s -> s.setDocumentation(null)).toList();
	}

	private void checkReportBundle(Bundle searchBundle, Bundle reportBundle, String hrpIdentifier)
	{
		int requests = searchBundle.getEntry().size();

		List<String> errorCodes = reportBundle.getEntry().stream().filter(e -> e.hasResponse())
				.map(e -> e.getResponse()).filter(r -> r.hasStatus()).map(r -> r.getStatus())
				.filter(s -> !s.contains(RESPONSE_OK)).toList();

		if (errorCodes.size() >= requests)
			throw new RuntimeException(
					"Report Bundle for HRP '" + hrpIdentifier + "' only contains error status codes");
	}

	private String storeReportBundle(Bundle responseBundle, String hrpIdentifier, String taskId)
	{
		System.out.println("CreateDashboardReport.storeReportBundle()");
		PreferReturnMinimal client = api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
				.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);

		String localOrganizationIdentifier = api.getOrganizationProvider().getLocalOrganizationIdentifierValue()
				.orElseThrow(() -> new RuntimeException("LocalOrganizationIdentifierValue empty"));
		IdType bundleIdType = client.updateConditionaly(responseBundle, Map.of("identifier", Collections.singletonList(
				ConstantsReport.NAMINGSYSTEM_CDS_REPORT_IDENTIFIER + "|" + localOrganizationIdentifier)));

		String absoluteId = new IdType(api.getEndpointProvider().getLocalEndpointAddress(), ResourceType.Bundle.name(),
				bundleIdType.getIdPart(), bundleIdType.getVersionIdPart()).getValue();

		logger.info("Stored report Bundle with id '{}' for HRP '{}' and Task with id '{}'", absoluteId, hrpIdentifier,
				taskId);

		return absoluteId;
	}
}
