package de.medizininformatik_initiative.process.report;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;

public class ReportProcessPluginDeploymentStateListener
		implements ProcessPluginDeploymentStateListener, InitializingBean
{
	private final FhirClientFactory fhirClientFactory;

	public ReportProcessPluginDeploymentStateListener(FhirClientFactory fhirClientFactory)
	{
		this.fhirClientFactory = fhirClientFactory;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(fhirClientFactory, "fhirClientFactory");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		if (activeProcesses.contains(ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND))
			fhirClientFactory.testConnection();
	}
}
