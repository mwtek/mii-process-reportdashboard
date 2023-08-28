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
import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractServiceDelegate;
import dev.dsf.bpe.v1.service.MailService;
import dev.dsf.bpe.v1.variables.Variables;

public class StoreReceipt extends AbstractServiceDelegate implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(StoreReceipt.class);

	private final ReportStatusGenerator statusGenerator;
	private final MailService mailService;

	public StoreReceipt(ProcessPluginApi api, ReportStatusGenerator statusGenerator, MailService mailService)
	{
		super(api);

		this.statusGenerator = statusGenerator;
		this.mailService = mailService;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(statusGenerator, "statusGenerator");
		Objects.requireNonNull(mailService, "mailService");
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
			api.getFhirWebserviceClientProvider().getLocalWebserviceClient().update(startTask);
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
		startTask.getOutput().stream().filter(o -> o.getValue() instanceof Coding).map(o -> (Coding) o.getValue())
				.filter(c -> ConstantsReport.CODESYSTEM_REPORT_STATUS.equals(c.getSystem()))
				.forEach(c -> doWriteStatusLogAndSendMail(c, startTask.getId(), reportLocation));
	}

	private void doWriteStatusLogAndSendMail(Coding status, String startTaskId, String reportLocation)
	{
		String code = status.getCode();
		String extension = status.hasExtension()
				? " and extension '" + status.getExtensionFirstRep().getUrl() + "|"
						+ status.getExtensionFirstRep().getValueAsPrimitive().getValueAsString() + "'"
				: "";

		if (ConstantsReport.CODESYSTEM_REPORT_STATUS_VALUE_RECEIPT_OK.equals(code))
		{
			logger.info("Task with id '{}' has report-status code '{}'{}", startTaskId, code, extension);
			sendSuccessfulMail(reportLocation, code, extension);
		}
		else
		{
			logger.warn("Task with id '{}' has report-status code '{}'{}", startTaskId, code, extension);
			sendErrorMail(startTaskId, reportLocation, code, extension);
		}
	}

	private void sendSuccessfulMail(String reportLocation, String code, String extension)
	{
		String subject = "New successful report in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "'";
		String message = "A new report has been successfully created and retrieved by the HRP with status code '" + code
				+ "'" + extension + " in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "' and can be accessed using the following link:\n" + "- " + reportLocation;

		mailService.send(subject, message);
	}

	private void sendErrorMail(String leadingTaskId, String reportLocation, String code, String extension)
	{
		String subject = "Error in report process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND + "'";
		String message = "A new report could not be created and retrieved by the HRP, status code is '" + code + "'"
				+ extension + " in process '" + ConstantsReport.PROCESS_NAME_FULL_REPORT_SEND
				+ "' belonging to Task with id '" + leadingTaskId
				+ "' and can possibly be accessed using the following link:\n" + "- " + reportLocation;

		mailService.send(subject, message);
	}
}
