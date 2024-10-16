package de.medizininformatik_initiative.process.report.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class CreateJson extends AbstractServiceDelegate
{
	@Value("${de.netzwerk.universitaetsmedizin.dashboard.report.ddp.url:#{null}}")
	private String DDP_URL;
	@Value("${de.netzwerk.universitaetsmedizin.dashboard.report.ddp.user:#{null}}")
	private String DDP_USERNAME;
	@Value("${de.netzwerk.universitaetsmedizin.dashboard.report.ddp.password:#{null}}")
	private String DDP_PASSWORD;

	@Value("${de.netzwerk.universitaetsmedizin.dashboard.report.ddp.approval:#{null}}")
	private String DDP_APPROVAL;

	private static final Logger logger = LoggerFactory.getLogger(CreateJson.class);

	public CreateJson(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution delegateExecution, Variables variables) throws BpmnError, Exception
	{
		// URL from the curl command
		String url = DDP_URL;

		// Credentials from the curl command
		String username = DDP_USERNAME;
		String password = DDP_PASSWORD;

		// Create an instance of RestTemplate
		RestTemplate restTemplate = new RestTemplate();

		// Set up the headers, including the Authorization header for basic auth
		HttpHeaders headers = new HttpHeaders();
		String auth = username + ":" + password;
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
		String authHeader = "Basic " + encodedAuth;
		headers.set("Authorization", authHeader);

		// Create an HttpEntity with the headers
		HttpEntity<String> entity = new HttpEntity<>(headers);

		// Send the request using exchange to include headers and get the response
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, // Change to POST if necessary
				entity, String.class);

		// Print the response body
		System.out.println("Response Body: " + response.getBody());
		// Save response
		variables.setString(ConstantsReport.BPMN_EXECUTION_VARIABLE_DASHBOARD_REPORT_DDP_JSON, response.getBody());

		// Set approval variable for next step
		if (DDP_APPROVAL.equals("false") || DDP_APPROVAL.equals("0"))
		{
			variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_DASHBOARD_REPORT_DDP_APPROVAL, false);
		}
		else
		{
			variables.setBoolean(ConstantsReport.BPMN_EXECUTION_VARIABLE_DASHBOARD_REPORT_DDP_APPROVAL, true);
		}
	}
}
