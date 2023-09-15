package de.medizininformatik_initiative.process.report.message;

import java.util.Optional;
import java.util.stream.Stream;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;
import dev.dsf.bpe.v1.variables.Variables;

public class StartSendReport extends AbstractTaskMessageSend
{
	public StartSendReport(ProcessPluginApi api)
	{
		super(api);
	}

	@Override
	protected Stream<Task.ParameterComponent> getAdditionalInputParameters(DelegateExecution execution,
			Variables variables)
	{
		Task startTask = variables.getStartTask();

		Optional<Reference> hrpIdentifier = api.getTaskHelper().getFirstInputParameterValue(startTask,
				ConstantsReport.CODESYSTEM_REPORT, ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER,
				Reference.class);

		return hrpIdentifier.stream().map(r -> api.getTaskHelper().createInput(r, ConstantsReport.CODESYSTEM_REPORT,
				ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER));
	}
}
