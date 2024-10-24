package de.medizininformatik_initiative.process.report.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.PreferReturnMinimal;

public class InsertReport extends AbstractServiceDelegate
{
	@Value("${de.netzwerk.universitaetsmedizin.dashboard.report.backend.url:#{null}}")
	private String DASHBOARD_BACKEND_URL;
	@Value("${de.netzwerk.universitaetsmedizin.dashboard.report.backend.user:#{null}}")
	private String DASHBOARD_BACKEND_USER;
	@Value("${de.netzwerk.universitaetsmedizin.dashboard.report.backend.password:#{null}}")
	private String DASHBOARD_BACKEND_PASSWORD;

	private static final Logger logger = LoggerFactory.getLogger(InsertReport.class);

	private final ReportStatusGenerator statusGenerator;

	public InsertReport(ProcessPluginApi api, ReportStatusGenerator statusGenerator)
	{
		super(api);
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(statusGenerator, "reportStatusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		String sendingOrganization = task.getRequester().getIdentifier().getValue();
		Identifier reportIdentifier = getReportIdentifier(task);

		Bundle report = variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);
		report.setId("").getMeta().setVersionId("").setTag(null);
		report.setIdentifier(reportIdentifier);

		api.getReadAccessHelper().addLocal(report);
		api.getReadAccessHelper().addOrganization(report, task.getRequester().getIdentifier().getValue());

		PreferReturnMinimal client = api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
				.withMinimalReturn()
				.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN);
		try
		{
			IdType reportId = client.updateConditionaly(report, Map.of("identifier",
					Collections.singletonList(reportIdentifier.getSystem() + "|" + reportIdentifier.getValue())));

			task.addOutput(statusGenerator
					.createReportStatusOutput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_OK));
			variables.updateTask(task);

			String absoluteReportId = new IdType(api.getEndpointProvider().getLocalEndpointAddress(),
					ResourceType.Bundle.name(), reportId.getIdPart(), reportId.getVersionIdPart()).getValue();

			logger.info("Stored report with id '{}' from organization '{}' for Task with id '{}'", absoluteReportId,
					sendingOrganization, task.getId());

			sendDataToDashboard(report);
			sendMail(sendingOrganization, absoluteReportId);
		}
		catch (Exception exception)
		{
			task.setStatus(Task.TaskStatus.FAILED);
			task.addOutput(statusGenerator.createReportStatusOutput(
					ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIVE_ERROR, "Insert report failed"));
			variables.updateTask(task);

			variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR_MESSAGE,
					"Insert report failed");

			logger.warn("Storing report from organization '{}' for Task with id '{}' failed - {}", sendingOrganization,
					task.getId(), exception.getMessage());
			throw new BpmnError(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_RECEIVE_ERROR,
					"Insert report - " + exception.getMessage());
		}
	}

	protected ResponseEntity<String> sendDataToDashboard(Bundle report)
	{

		// Convert report to a pretty-printed JSON string
		String reportString = FhirContext.forR4().newJsonParser().setPrettyPrint(true).encodeResourceToString(report);
		System.out.println("reportString:" + reportString);

		// Extract the valueString from the QuestionnaireResponse
		QuestionnaireResponse qr = (QuestionnaireResponse) report.getEntry().get(0).getResource();
		String valueString = qr.getItem().get(0).getAnswer().get(0).getValueStringType().getValue();
		System.out.println("valueString: " + valueString);

		// Create an instance of RestTemplate
		RestTemplate restTemplate = new RestTemplate();

		// Set up the headers, including the Authorization header for basic auth
		HttpHeaders headers = new HttpHeaders();
		String auth = this.DASHBOARD_BACKEND_USER + ":" + this.DASHBOARD_BACKEND_PASSWORD;
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
		String authHeader = "Basic " + encodedAuth;
		headers.set("Authorization", authHeader);
		headers.setContentType(MediaType.APPLICATION_JSON); // Set content type as JSON

		// Create a JSON request body with valueString
		String requestBody = "{ \"value\": \"" + valueString + "\" }";
		System.out.println("Request Body: " + requestBody);

		// Create an HttpEntity with the headers and request body
		HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

		// Send the request using exchange to include headers and request body, and get the response
		ResponseEntity<String> response = restTemplate.exchange(this.DASHBOARD_BACKEND_URL, HttpMethod.PUT, entity,
				String.class);

		// Print the response body
		System.out.println("Response Body: " + response.getBody());

		return response;

	}

	private Identifier getReportIdentifier(Task task)
	{
		return new Identifier().setSystem(ConstantsReport.NAMINGSYSTEM_CDS_DASHBOARD_REPORT_IDENTIFIER)
				.setValue(task.getRequester().getIdentifier().getValue());
	}

	private void sendMail(String sendingOrganization, String reportLocation)
	{
		String subject = "New report stored in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE + "'";
		String message = "A new report has been stored in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_RECEIVE
				+ "' from organization '" + sendingOrganization + "' and can be accessed using the following link:\n"
				+ "- " + reportLocation;

		api.getMailService().send(subject, message);
	}
}
