package de.medizininformatik_initiative.process.report.message;

import dev.dsf.bpe.v1.ProcessPluginApi;
import dev.dsf.bpe.v1.activity.AbstractTaskMessageSend;

public class StartSendReport extends AbstractTaskMessageSend
{
	public StartSendReport(ProcessPluginApi api)
	{
		super(api);
	}
}
