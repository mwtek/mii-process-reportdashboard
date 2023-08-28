package de.medizininformatik_initiative.process.report.service;

import java.util.List;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.constants.NamingSystems;
import dev.dsf.bpe.v1.variables.Target;
import dev.dsf.bpe.v1.variables.Variables;

public class SelectTargetHrp extends AbstractServiceDelegate
{
	private static final Logger logger = LoggerFactory.getLogger(SelectTargetHrp.class);

	public SelectTargetHrp(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		Identifier parentIdentifier = NamingSystems.OrganizationIdentifier.withValue(
				ConstantsBase.NAMINGSYSTEM_DSF_ORGANIZATION_IDENTIFIER_MEDICAL_INFORMATICS_INITIATIVE_CONSORTIUM);
		Coding hrpRole = new Coding().setSystem(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE)
				.setCode(ConstantsBase.CODESYSTEM_DSF_ORGANIZATION_ROLE_VALUE_HRP);

		Organization organization = getHrpOrganization(parentIdentifier, hrpRole);
		Identifier organizationIdentifier = extractHrpIdentifier(organization);

		Endpoint endpoint = getHrpEndpoint(parentIdentifier, organizationIdentifier, hrpRole);
		String endpointIdentifier = extractEndpointIdentifier(endpoint);

		Target target = variables.createTarget(organizationIdentifier.getValue(), endpointIdentifier,
				endpoint.getAddress());
		variables.setTarget(target);
	}

	private Organization getHrpOrganization(Identifier parentIdentifier, Coding role)
	{
		List<Organization> hrps = api.getOrganizationProvider().getOrganizations(parentIdentifier, role);

		if (hrps.size() < 1)
			throw new RuntimeException("Could not find any organization with role '" + role.getCode()
					+ "' and parent organization '" + parentIdentifier.getValue() + "'");

		if (hrps.size() > 1)
			logger.warn(
					"Found more than 1 ({}) organization with role '{}' and parent organization '{}', using the first ('{}')",
					hrps.size(), role.getCode(), parentIdentifier.getValue(),
					hrps.get(0).getIdentifierFirstRep().getValue());

		return hrps.get(0);
	}

	private Endpoint getHrpEndpoint(Identifier parentIdentifier, Identifier organizationIdentifier, Coding role)
	{
		return api.getEndpointProvider().getEndpoint(parentIdentifier, organizationIdentifier, role)
				.orElseThrow(() -> new RuntimeException("Could not find any endpoint of '" + role.getCode()
						+ "' with identifier '" + organizationIdentifier.getValue() + "'"));
	}

	private Identifier extractHrpIdentifier(Organization organization)
	{
		return organization.getIdentifier().stream()
				.filter(i -> NamingSystems.OrganizationIdentifier.SID.equals(i.getSystem())).findFirst()
				.orElseThrow(() -> new RuntimeException("organization with id '" + organization.getId()
						+ "' is missing identifier with system '" + NamingSystems.OrganizationIdentifier.SID + "'"));
	}

	private String extractEndpointIdentifier(Endpoint endpoint)
	{
		return endpoint.getIdentifier().stream().filter(i -> NamingSystems.EndpointIdentifier.SID.equals(i.getSystem()))
				.map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Endpoint with id '" + endpoint.getId()
						+ "' is missing identifier with system '" + NamingSystems.EndpointIdentifier.SID + "'"));
	}
}
