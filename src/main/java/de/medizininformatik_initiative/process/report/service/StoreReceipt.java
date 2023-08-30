package de.medizininformatik_initiative.process.report.service;

import java.util.Objects;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.util.ReportStatusGenerator;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.variables.Variables;

public class StoreReceipt extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(StoreReceipt.class);

	private final ReportStatusGenerator statusGenerator;

	public StoreReceipt(ProcessPluginApi api, ReportStatusGenerator statusGenerator)
	{
		super(api);
		this.statusGenerator = statusGenerator;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	protected void doExecute(DelegateExecution execution, Variables variables)
	{
		String reportLocation = variables
				.getString(ConstantsReport.BPMN_EXECUTION_VARIABLE_REPORT_SEARCH_BUNDLE_RESPONSE_REFERENCE);

		Task startTask = variables.getStartTask();
		Task currentTask = variables.getLatestTask();

		if (!currentTask.getId().equals(startTask.getId()))
			handleReceivedResponse(startTask, currentTask);
		else
			handleMissingResponse(startTask);

		writeStatusLogAndSendMail(startTask, reportLocation);

		variables.updateTask(startTask);

		if (Task.TaskStatus.FAILED.equals(startTask.getStatus()))
		{
			api.getFhirWebserviceClientProvider().getLocalWebserviceClient()
					.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES, ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)
					.update(startTask);
		}
	}

	private void handleReceivedResponse(Task startTask, Task currentTask)
	{
		statusGenerator.transformInputToOutput(currentTask, startTask);

		if (startTask.getOutput().stream().filter(Task.TaskOutputComponent::hasExtension)
				.flatMap(o -> o.getExtension().stream())
				.anyMatch(e -> ConstantsReport.EXTENSION_REPORT_STATUS_ERROR_URL.equals(e.getUrl())))
			startTask.setStatus(Task.TaskStatus.FAILED);
	}

	private void handleMissingResponse(Task startTask)
	{
		startTask.setStatus(Task.TaskStatus.FAILED);
		startTask.addOutput(statusGenerator
				.createReportStatusOutput(ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_MISSING));
	}

	private void writeStatusLogAndSendMail(Task startTask, String reportLocation)
	{
		startTask.getOutput().stream().filter(o -> o.getValue() instanceof Coding)
				.filter(o -> ConstantsReport.CODESYSTEM_REPORT_STATUS.equals(((Coding) o.getValue()).getSystem()))
				.forEach(o -> doWriteStatusLogAndSendMail(o, startTask.getId(), reportLocation));
	}

	private void doWriteStatusLogAndSendMail(Task.TaskOutputComponent output, String startTaskId, String reportLocation)
	{
		Coding status = (Coding) output.getValue();
		String code = status.getCode();
		String error = output.hasExtension() ? output.getExtensionFirstRep().getValueAsPrimitive().getValueAsString()
				: "";
		String errorLog = error.isBlank() ? "" : " - " + error;

		if (ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_OK.equals(code))
		{
			logger.info("Task with id '{}' has report-status code '{}'", startTaskId, code);
			sendSuccessfulMail(reportLocation, code);
		}
		else
		{
			logger.warn("Task with id '{}' has report-status code '{}'{}", startTaskId, code, errorLog);
			sendErrorMail(startTaskId, reportLocation, code, error);
		}
	}

	private void sendSuccessfulMail(String reportLocation, String code)
	{
		String subject = "New successful report in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "'";
		String message = "A new report has been successfully created and retrieved by the HRP with status code '" + code
				+ "' in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "' and can be accessed using the following link:\n" + "- " + reportLocation;

		api.getMailService().send(subject, message);
	}

	private void sendErrorMail(String startTaskId, String reportLocation, String code, String error)
	{
		String subject = "Error in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "'";

		String message = "HRP could not download or insert new report with reference '" + reportLocation
				+ "' in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "' in Task with id '" + startTaskId
				+ "':\n" + "- status code: " + code + "\n" + "- error: " + error;

		api.getMailService().send(subject, message);
	}
}
