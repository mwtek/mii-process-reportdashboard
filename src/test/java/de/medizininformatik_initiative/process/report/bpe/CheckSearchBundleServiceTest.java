package de.medizininformatik_initiative.process.report.bpe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.service.CheckSearchBundle;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.service.FhirWebserviceClientProvider;
import dev.dsf.bpe.v1.service.TaskHelper;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;
import dev.dsf.fhir.client.FhirWebserviceClient;
import dev.dsf.fhir.client.PreferReturnMinimalWithRetry;

@RunWith(MockitoJUnitRunner.class)
public class CheckSearchBundleServiceTest
{
	@Mock
	private DelegateExecution execution;

	@Mock
	private ProcessPluginApi api;

	@Mock
	private Variables variables;

	@Mock
	private Target target;

	@Mock
	private Task task;

	@Mock
	private TaskHelper taskHelper;

	@Mock
	FhirWebserviceClientProvider clientProvider;

	@Mock
	private FhirWebserviceClient webserviceClient;

	@Mock
	private PreferReturnMinimalWithRetry preferReturnMinimalWithRetry;

	@Mock
	private ProcessEngine processEngine;

	@Mock
	private RuntimeService runtimeService;

	@Captor
	ArgumentCaptor<Task.TaskOutputComponent> output;

	@InjectMocks
	private CheckSearchBundle service;

	@Test
	public void testValid()
	{
		testValid("/fhir/Bundle/search-bundle.xml");
	}

	@Test
	public void testInvalidResource()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-resource.xml", "resources");
	}

	@Test
	public void testInvalidRequestMethod()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-request-method.xml", "GET");
	}

	@Test
	public void testInvalidResourceId()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-resource-id.xml", "request url with resource id");
	}

	@Test
	public void testInvalidNoSummary()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-not-exists.xml", "without _summary parameter");
	}

	@Test
	public void testInvalidDoubleSummary()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-double.xml", "more than one _summary parameter");
	}

	@Test
	public void testInvalidSummaryUrlEncoded()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-url-encoded.xml", "invalid search params");
	}

	@Test
	public void testInvalidSummaryUrlEncodedFull()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-url-encoded-full.xml", "invalid search params");
	}

	@Test
	public void testInvalidUnexpectedSummary()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-summary-not-allowed.xml", "unexpected _summary parameter");
	}

	@Test
	public void testInvalidParam()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-param.xml", "invalid search params");
	}

	@Test
	public void testInvalidDateFilter()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-date-filter.xml", "not starting with 'eq'");
	}

	@Test
	public void testInvalidDateValue()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-date-value-single.xml", "not limited to a year");
	}

	@Test
	public void testInvalidDoubleDateValue()
	{
		testInvalid("/fhir/Bundle/search-bundle-invalid-date-value-double.xml", "not limited to a year");
	}

	@Test
	public void testValidV1_1()
	{
		testValid("/fhir/Bundle/search-bundle-v1.1.xml");
	}

	@Test
	public void testValidV1_1EncounterType()
	{
		testValid("/fhir/Bundle/search-bundle-v1.1-valid-encounter-type.xml");
	}

	@Test
	public void testInvalidV1_1SingleCode()
	{
		testInvalid("/fhir/Bundle/search-bundle-v1.1-invalid-code-single.xml", "not limited to system");
	}

	@Test
	public void testInvalidV1_1DoubleCode()
	{
		testInvalid("/fhir/Bundle/search-bundle-v1.1-invalid-code-double.xml", "not limited to system");
	}

	@Test
	public void testInvalidV1_1CodeIngredient()
	{
		testInvalid("/fhir/Bundle/search-bundle-v1.1-invalid-code-ingredient.xml", "not limited to system");
	}

	private void testValid(String pathToBundle)
	{
		try (InputStream in = getClass().getResourceAsStream(pathToBundle))
		{
			Bundle bundle = FhirContext.forR4().newXmlParser().parseResource(Bundle.class, in);
			Mockito.when(api.getVariables(execution)).thenReturn(variables);
			Mockito.when(variables.getStartTask()).thenReturn(task);
			Mockito.when(variables.getTarget()).thenReturn(target);
			Mockito.when(target.getOrganizationIdentifierValue()).thenReturn("Test_DIC1");
			Mockito.when(variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE))
					.thenReturn(bundle);

			service.execute(execution);
		}
		catch (Exception exception)
		{
			fail();
		}
	}

	private void testInvalid(String pathToBundle, String errorContains)
	{
		try (InputStream in = getClass().getResourceAsStream(pathToBundle))
		{
			Bundle bundle = FhirContext.forR4().newXmlParser().parseResource(Bundle.class, in);
			Mockito.when(api.getVariables(execution)).thenReturn(variables);
			Mockito.when(variables.getStartTask()).thenReturn(task);
			Mockito.when(variables.getTarget()).thenReturn(target);
			Mockito.when(target.getOrganizationIdentifierValue()).thenReturn("Test_DIC1");
			Mockito.when(variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE))
					.thenReturn(bundle);
			Mockito.when(execution.getProcessDefinitionId()).thenReturn("processDefinitionId");
			Mockito.when(execution.getActivityInstanceId()).thenReturn("activityInstanceId");
			Mockito.when(api.getTaskHelper()).thenReturn(taskHelper);
			Mockito.when(taskHelper.getLocalVersionlessAbsoluteUrl(task)).thenReturn("http://foo.bar/fhir/Task/id");
			Mockito.when(variables.getTasks()).thenReturn(List.of(task));
			Mockito.when(task.getStatus()).thenReturn(Task.TaskStatus.INPROGRESS);

			Mockito.when(api.getFhirWebserviceClientProvider()).thenReturn(clientProvider);
			Mockito.when(clientProvider.getLocalWebserviceClient()).thenReturn(webserviceClient);
			Mockito.when(webserviceClient.withMinimalReturn()).thenReturn(preferReturnMinimalWithRetry);
			Mockito.when(preferReturnMinimalWithRetry.update(task))
					.thenReturn(new IdType(UUID.randomUUID().toString()));
			Mockito.when(execution.getProcessEngine()).thenReturn(processEngine);
			Mockito.when(processEngine.getRuntimeService()).thenReturn(runtimeService);

			service.execute(execution);
			Mockito.verify(task).addOutput(output.capture());

			Task.TaskOutputComponent value = output.getValue();
			assertEquals(1,
					value.getType().getCoding().stream().filter(c -> CodeSystems.BpmnMessage.URL.equals(c.getSystem())
							&& CodeSystems.BpmnMessage.error().getCode().equals(c.getCode())).count());

			assertTrue(value.getValue() instanceof StringType);
			assertTrue(((StringType) value.getValue()).getValue().contains(errorContains));
		}
		catch (Exception exception)
		{
			fail();
		}
	}
}
