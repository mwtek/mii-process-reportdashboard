package de.medizininformatik_initiative.process.report.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class CheckSearchBundle extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(CheckSearchBundle.class);

	private static final Pattern MODIFIERS = Pattern.compile(":.*");
	private static final Pattern YEAR_ONLY = Pattern.compile("\\b20\\d{2}(?!\\S)");
	private static final String DATE_EQUALITY_FILTER = "eq";

	private static final String CAPABILITY_STATEMENT_PATH = "metadata";
	private static final String SUMMARY_SEARCH_PARAM = "_summary";
	private static final String SUMMARY_SEARCH_PARAM_VALUE_COUNT = "count";

	private static final List<String> DATE_SEARCH_PARAMS = List.of("date", "recorded-date", "onset-date", "effective",
			"effective-time", "authored", "collected", "issued", "period", "location-period", "occurrence");
	private static final List<String> OTHER_SEARCH_PARAMS = List.of("_profile", "type", SUMMARY_SEARCH_PARAM);

	private static final List<String> VALID_SEARCH_PARAMS = Stream
			.concat(DATE_SEARCH_PARAMS.stream(), OTHER_SEARCH_PARAMS.stream()).toList();

	public CheckSearchBundle(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		Bundle bundle = variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);

		logger.info("Checking downloaded as part of Task with id '{}'", task.getId());

		try
		{
			List<Bundle.BundleEntryComponent> searches = bundle.getEntry();

			testNoResources(searches);
			testRequestMethod(searches);
			testRequestUrls(searches);

			logger.info(
					"Search Bundle downloaded as part of Task with id '{}' contains only valid requests of type GET and valid search params {}",
					task.getId(), VALID_SEARCH_PARAMS);
		}
		catch (Exception exception)
		{
			logger.warn("Error while checking search Bundle referenced in Task with id '{}' - {}", task.getId(),
					exception.getMessage());
			throw new RuntimeException("Error while checking search Bundle referenced in Task with id '" + task.getId()
					+ "' - " + exception.getMessage(), exception);
		}
	}

	private void testNoResources(List<Bundle.BundleEntryComponent> searches)
	{
		if (searches.stream().map(Bundle.BundleEntryComponent::getResource).anyMatch(Objects::nonNull))
			throw new RuntimeException("Search Bundle contains resources");
	}

	private void testRequestMethod(List<Bundle.BundleEntryComponent> searches)
	{
		long httpGetCount = searches.stream().filter(Bundle.BundleEntryComponent::hasRequest)
				.map(Bundle.BundleEntryComponent::getRequest).filter(Bundle.BundleEntryRequestComponent::hasMethod)
				.map(Bundle.BundleEntryRequestComponent::getMethod).filter(Bundle.HTTPVerb.GET::equals).count();

		int searchesCount = searches.size();

		if (searchesCount != httpGetCount)
			throw new RuntimeException("Search Bundle contains HTTP method other then GET");
	}

	private void testRequestUrls(List<Bundle.BundleEntryComponent> searches)
	{
		List<Bundle.BundleEntryRequestComponent> requests = searches.stream()
				.filter(Bundle.BundleEntryComponent::hasRequest).map(Bundle.BundleEntryComponent::getRequest)
				.filter(Bundle.BundleEntryRequestComponent::hasUrl).toList();

		int requestCount = requests.size();
		int searchesCount = searches.size();

		if (searchesCount != requestCount)
			throw new RuntimeException("Search Bundle contains request without url");

		List<UriComponents> uriComponents = requests.stream()
				.map(r -> UriComponentsBuilder.fromUriString(r.getUrl()).build()).collect(Collectors.toList());

		testContainsSummaryCount(uriComponents);
		testContainsValidSearchParams(uriComponents);
		testContainsValidDateSearchParams(uriComponents);
	}

	private void testContainsSummaryCount(List<UriComponents> uriComponents)
	{
		uriComponents.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath()))
				.map(u -> u.getQueryParams().toSingleValueMap()).forEach(this::testSummaryCount);
	}

	private void testSummaryCount(Map<String, String> queryParams)
	{
		if (!SUMMARY_SEARCH_PARAM_VALUE_COUNT.equals(queryParams.get(SUMMARY_SEARCH_PARAM)))
			throw new RuntimeException("Search Bundle contains request url without _summary=count");
	}

	private void testContainsValidSearchParams(List<UriComponents> uriComponents)
	{
		uriComponents.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath()))
				.map(u -> u.getQueryParams().toSingleValueMap()).forEach(this::testSearchParamNames);
	}

	private void testSearchParamNames(Map<String, String> queryParams)
	{
		if (queryParams.keySet().stream().map(s -> MODIFIERS.matcher(s).replaceAll(""))
				.anyMatch(s -> !VALID_SEARCH_PARAMS.contains(s)))
			throw new RuntimeException("Search Bundle contains invalid search params, only allowed search params are "
					+ VALID_SEARCH_PARAMS);
	}

	private void testContainsValidDateSearchParams(List<UriComponents> uriComponents)
	{
		uriComponents.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath()))
				.map(u -> u.getQueryParams().toSingleValueMap()).forEach(this::testSearchParamDateValues);
	}

	private void testSearchParamDateValues(Map<String, String> queryParams)
	{
		List<Map.Entry<String, String>> dateParams = queryParams.entrySet().stream()
				.filter(e -> DATE_SEARCH_PARAMS.contains(MODIFIERS.matcher(e.getKey()).replaceAll(""))).toList();

		List<Map.Entry<String, String>> erroneousDateFilters = dateParams.stream()
				.filter(e -> !e.getValue().startsWith(DATE_EQUALITY_FILTER)).toList();

		if (erroneousDateFilters.size() > 0)
			throw new RuntimeException(
					"Search Bundle contains date search params not starting with 'eq' - [" + erroneousDateFilters
							.stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")) + "]");

		List<Map.Entry<String, String>> erroneousDateValues = dateParams.stream()
				.filter(e -> !YEAR_ONLY.matcher(e.getValue().replace(DATE_EQUALITY_FILTER, "")).matches()).toList();

		if (erroneousDateValues.size() > 0)
			throw new RuntimeException(
					"Search Bundle contains date search params not limited to a year - [" + erroneousDateValues.stream()
							.map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")) + "]");
	}
}
