package de.medizininformatik_initiative.process.report.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Target;
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
	private static final String TYPE_SEARCH_PARAM = "type";

	private static final List<String> DATE_SEARCH_PARAMS = List.of("date", "recorded-date", "onset-date", "effective",
			"effective-time", "authored", "collected", "issued", "period", "location-period", "occurrence");
	private static final List<String> TOKEN_SEARCH_PARAMS = List.of("code", "ingredient-code", TYPE_SEARCH_PARAM);
	private static final List<String> OTHER_SEARCH_PARAMS = List.of("_profile", SUMMARY_SEARCH_PARAM);

	private static final List<String> VALID_SEARCH_PARAMS = Stream
			.of(DATE_SEARCH_PARAMS.stream(), TOKEN_SEARCH_PARAMS.stream(), OTHER_SEARCH_PARAMS.stream()).flatMap(s -> s)
			.toList();

	public CheckSearchBundle(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Task task = variables.getStartTask();
		Target target = variables.getTarget();
		Bundle bundle = variables.getResource(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE);

		logger.info("Checking downloaded search Bundle from HRP '{}' as part of Task with id '{}'",
				target.getOrganizationIdentifierValue(), task.getId());

		try
		{
			List<Bundle.BundleEntryComponent> searches = bundle.getEntry();

			testNoResources(searches);
			testRequestMethod(searches);
			testRequestUrls(searches);

			logger.info(
					"Search Bundle downloaded from HRP '{}' as part of Task with id '{}' contains only valid requests of type GET and valid search params {}",
					target.getOrganizationIdentifierValue(), task.getId(), VALID_SEARCH_PARAMS);
		}
		catch (Exception exception)
		{
			logger.warn("Error while checking search Bundle from HRP '{}' in Task with id '{}' - {}",
					target.getOrganizationIdentifierValue(), task.getId(), exception.getMessage());
			throw new RuntimeException(
					"Error while checking search Bundle from HRP '" + target.getOrganizationIdentifierValue()
							+ "' in Task with id '" + task.getId() + "' - " + exception.getMessage(),
					exception);
		}
	}

	private void testNoResources(List<Bundle.BundleEntryComponent> searches)
	{
		if (searches.stream().map(Bundle.BundleEntryComponent::getResource).anyMatch(Objects::nonNull))
			throw new RuntimeException("Search Bundle contains resources");
	}

	private void testRequestMethod(List<Bundle.BundleEntryComponent> searches)
	{
		long searchesCount = searches.size();
		long httpGetCount = searches.stream().filter(Bundle.BundleEntryComponent::hasRequest)
				.map(Bundle.BundleEntryComponent::getRequest).filter(Bundle.BundleEntryRequestComponent::hasMethod)
				.map(Bundle.BundleEntryRequestComponent::getMethod).filter(Bundle.HTTPVerb.GET::equals).count();

		if (searchesCount != httpGetCount)
			throw new RuntimeException("Search Bundle contains HTTP method other then GET");
	}

	private void testRequestUrls(List<Bundle.BundleEntryComponent> searches)
	{
		int searchesCount = searches.size();
		List<Bundle.BundleEntryRequestComponent> requests = searches.stream()
				.filter(Bundle.BundleEntryComponent::hasRequest).map(Bundle.BundleEntryComponent::getRequest)
				.filter(Bundle.BundleEntryRequestComponent::hasUrl).toList();
		int requestCount = requests.size();

		if (searchesCount != requestCount)
			throw new RuntimeException("Search Bundle contains request without url");

		List<UriComponents> uriComponents = requests.stream()
				.map(r -> UriComponentsBuilder.fromUriString(r.getUrl()).build()).toList();

		testPathIds(uriComponents);
		testSummaryCount(uriComponents);
		testContainsValidSearchParams(uriComponents);
		testContainsValidDateSearchParams(uriComponents);
		testContainsValidTokenSearchParams(uriComponents);
	}

	private void testPathIds(List<UriComponents> uriComponents)
	{
		// Resource type is only present if URI path contains a resource id
		List<IdType> pathsWithId = uriComponents.stream().map(UriComponents::getPath).filter(Objects::nonNull)
				.map(IdType::new).filter(i -> i.getResourceType() != null).toList();

		if (!pathsWithId.isEmpty())
		{
			throw new RuntimeException("Search Bundle contains request url with resource id - ["
					+ pathsWithId.stream().map(IdType::getValue).collect(Collectors.joining(",")) + "]");
		}
	}

	private void testSummaryCount(List<UriComponents> uriComponents)
	{
		uriComponents.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath()))
				.map(UriComponents::getQueryParams).forEach(this::testSummaryCount);
	}

	private void testSummaryCount(MultiValueMap<String, String> queryParams)
	{
		List<String> summaryParams = queryParams.get(SUMMARY_SEARCH_PARAM);

		if (summaryParams == null || summaryParams.isEmpty())
		{
			throw new RuntimeException("Search Bundle contains request url without _summary parameter");
		}

		if (summaryParams.size() > 1)
		{
			throw new RuntimeException("Search Bundle contains request url with more than one _summary parameter");
		}

		if (!SUMMARY_SEARCH_PARAM_VALUE_COUNT.equals(summaryParams.get(0)))
		{
			throw new RuntimeException(
					"Search Bundle contains request url with unexpected _summary parameter value (expected: count, actual: "
							+ summaryParams.get(0) + ")");
		}
	}

	private void testContainsValidSearchParams(List<UriComponents> uriComponents)
	{
		uriComponents.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath()))
				.map(UriComponents::getQueryParams).forEach(this::testSearchParamNames);
	}

	private void testSearchParamNames(MultiValueMap<String, String> queryParams)
	{
		if (queryParams.keySet().stream().map(s -> MODIFIERS.matcher(s).replaceAll(""))
				.anyMatch(s -> !VALID_SEARCH_PARAMS.contains(s)))
			throw new RuntimeException("Search Bundle contains invalid search params, only allowed search params are "
					+ VALID_SEARCH_PARAMS);
	}

	private void testContainsValidDateSearchParams(List<UriComponents> uriComponents)
	{
		uriComponents.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath()))
				.map(UriComponents::getQueryParams).forEach(this::testSearchParamDateValues);
	}

	private void testSearchParamDateValues(MultiValueMap<String, String> queryParams)
	{
		List<Map.Entry<String, String>> dateParams = queryParams.entrySet().stream()
				.filter(e -> DATE_SEARCH_PARAMS.contains(MODIFIERS.matcher(e.getKey()).replaceAll("")))
				.flatMap(e -> e.getValue().stream().map(v -> Map.entry(e.getKey(), v))).toList();

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

	private void testContainsValidTokenSearchParams(List<UriComponents> uriComponents)
	{
		uriComponents.stream().filter(u -> !CAPABILITY_STATEMENT_PATH.equals(u.getPath()))
				.forEach(this::testSearchParamTokenValues);
	}

	private void testSearchParamTokenValues(UriComponents uriComponents)
	{
		List<Map.Entry<String, String>> codeParams = uriComponents.getQueryParams().entrySet().stream()
				.filter(e -> TOKEN_SEARCH_PARAMS.contains(MODIFIERS.matcher(e.getKey()).replaceAll("")))
				.flatMap(e -> e.getValue().stream().map(v -> Map.entry(e.getKey(), v))).toList();

		// Exemption for Encounter.type token params
		List<Map.Entry<String, String>> erroneousCodeValues = codeParams.stream()
				.filter(e -> !e.getValue().endsWith("|"))
				.filter(e -> !isEncounterType(uriComponents.getPath(), e.getKey())).toList();

		if (erroneousCodeValues.size() > 0)
			throw new RuntimeException(
					"Search Bundle contains code search params not limited to system - [" + erroneousCodeValues.stream()
							.map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",")) + "]");
	}

	private boolean isEncounterType(String path, String paramName)
	{
		return TYPE_SEARCH_PARAM.equals(paramName) && ResourceType.Encounter.name().equals(path);
	}
}
