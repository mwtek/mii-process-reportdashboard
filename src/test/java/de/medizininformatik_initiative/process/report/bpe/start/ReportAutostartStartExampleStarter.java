package de.medizininformatik_initiative.process.report.bpe.start;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.TimeType;

import de.medizininformatik_initiative.process.report.ConstantsReport;
import de.medizininformatik_initiative.process.report.ReportProcessPluginDefinition;
import dev.dsf.bpe.start.ExampleStarter;
import dev.dsf.bpe.v1.constants.CodeSystems;
import dev.dsf.bpe.v1.constants.NamingSystems;

public class ReportAutostartStartExampleStarter
{
	private static final String DIC_URL = "https://dic1/fhir";
	private static final String DIC_IDENTIFIER = "Test_DIC1";

	private static final boolean USE_HRP_IDENTIFIER_INPUT = false;
	private static final String HRP_IDENTIFIER = "Test_HRP";

	private static final int MINUTES_TO_ADD = 5;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	public static void main(String[] args) throws Exception
	{
		ExampleStarter.forServer(args, DIC_URL).startWith(task());
	}

	private static Task task()
	{
		var def = new ReportProcessPluginDefinition();

		Task task = new Task();
		task.setIdElement(new IdType("urn:uuid:" + UUID.randomUUID().toString()));

		task.getMeta().addProfile(ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_START + "|" + def.getResourceVersion());
		task.setInstantiatesCanonical(
				ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_START_PROCESS_URI + "|" + def.getResourceVersion());
		task.setStatus(Task.TaskStatus.REQUESTED);
		task.setIntent(Task.TaskIntent.ORDER);
		task.setAuthoredOn(new Date());
		task.getRequester().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(DIC_IDENTIFIER));
		task.getRestriction().addRecipient().setType(ResourceType.Organization.name())
				.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(DIC_IDENTIFIER));

		task.addInput().setValue(new StringType(ConstantsReport.PROFILE_TASK_REPORT_AUTOSTART_START_MESSAGE_NAME))
				.getType().addCoding(CodeSystems.BpmnMessage.messageName());

		task.addInput().setValue(new StringType("PT5M")).getType().addCoding()
				.setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_TIMER_INTERVAL);

		String time = LocalTime.now().withSecond(0).plusMinutes(MINUTES_TO_ADD).format(TIME_FORMAT);
		task.addInput().setValue(new TimeType(time)).getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
				.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_FIRST_EXECUTION);

		if (USE_HRP_IDENTIFIER_INPUT)
		{
			task.addInput()
					.setValue(new Reference()
							.setIdentifier(NamingSystems.OrganizationIdentifier.withValue(HRP_IDENTIFIER))
							.setType(ResourceType.Organization.name()))
					.getType().addCoding().setSystem(ConstantsReport.CODESYSTEM_REPORT)
					.setCode(ConstantsReport.CODESYSTEM_REPORT_VALUE_HRP_IDENTIFIER);
		}

		return task;
	}
}
