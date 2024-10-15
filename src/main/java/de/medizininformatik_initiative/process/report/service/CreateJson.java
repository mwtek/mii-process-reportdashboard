package de.medizininformatik_initiative.process.report.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class CreateJson extends AbstractServiceDelegate
{

	private static final Logger logger = LoggerFactory.getLogger(CreateJson.class);

	public CreateJson(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution delegateExecution, Variables variables) throws BpmnError, Exception
	{
		logger.info("Inside the CreateJSON class");

		// URL from the curl command
		String url = "http://ukb3354.klink.bn:9091/createJson";

		// Credentials from the curl command
		String username = "ukb";
		String password = "mHaesy6x4mTwtgEC";

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

		// Print the HTTP status code
		System.out.println("Response Status Code: " + response.getStatusCodeValue());
	}
}
