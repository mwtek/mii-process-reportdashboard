package de.medizininformatik_initiative.process.report.spring.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import de.medizininformatik_initiative.processes.common.fhir.client.logging.DataLogger;
import dev.dsf.bpe.v1.documentation.ProcessDocumentation;

@Configuration
public class FhirClientConfig
{
	// TODO: use default proxy config from DSF
	@Autowired
	private FhirContext fhirContext;

	@ProcessDocumentation(required = true, processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "The base address of the FHIR server to read/store FHIR resources", example = "http://foo.bar/fhir")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.base.url:#{null}}")
	private String fhirStoreBaseUrl;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "PEM encoded file with one or more trusted root certificate to validate the FHIR server certificate when connecting via https", recommendation = "Use docker secret file to configure", example = "/run/secrets/hospital_ca.pem")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.trust.certificates:#{null}}")
	private String fhirStoreTrustStore;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "PEM encoded file with client-certificate, if FHIR server requires mutual TLS authentication", recommendation = "Use docker secret file to configure", example = "/run/secrets/fhir_server_client_certificate.pem")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.certificate:#{null}}")
	private String fhirStoreCertificate;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Private key corresponding to the FHIR server client-certificate as PEM encoded file. Use *${env_variable}_PASSWORD* or *${env_variable}_PASSWORD_FILE* if private key is encrypted", recommendation = "Use docker secret file to configure", example = "/run/secrets/fhir_server_private_key.pem")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.private.key:#{null}}")
	private String fhirStorePrivateKey;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Password to decrypt the FHIR server client-certificate encrypted private key", recommendation = "Use docker secret file to configure by using *${env_variable}_FILE*", example = "/run/secrets/fhir_server_private_key.pem.password")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.private.key.password:#{null}}")
	private char[] fhirStorePrivateKeyPassword;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Basic authentication username, set if the server containing the FHIR data requests authentication using basic auth")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.basicauth.username:#{null}}")
	private String fhirStoreUsername;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Basic authentication password, set if the server containing the FHIR data requests authentication using basic auth", recommendation = "Use docker secret file to configure by using *${env_variable}_FILE*", example = "/run/secrets/fhir_server_basicauth.password")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.basicauth.password:#{null}}")
	private String fhirStorePassword;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Bearer token for authentication, set if the server containing the FHIR data requests authentication using a bearer token, cannot be set using docker secrets")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.bearer.token:#{null}}")
	private String fhirStoreBearerToken;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "The timeout in milliseconds until a connection is established between the client and the FHIR server", recommendation = "Change default value only if timeout exceptions occur")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.timeout.connect:20000}")
	private int fhirStoreConnectTimeout;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "The timeout in milliseconds used when requesting a connection from the connection manager between the client and the FHIR server", recommendation = "Change default value only if timeout exceptions occur")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.timeout.connection.request:20000}")
	private int fhirStoreConnectionRequestTimeout;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Maximum period of inactivity in milliseconds between two consecutive data packets of the client and the FHIR server", recommendation = "Change default value only if timeout exceptions occur")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.timeout.socket:60000}")
	private int fhirStoreSocketTimeout;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "The client will log additional debug output", recommendation = "Change default value only if exceptions occur")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.client.verbose:false}")
	private boolean fhirStoreHapiClientVerbose;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Proxy location, set if the server containing the FHIR data can only be reached through a proxy", example = "http://proxy.foo:8080")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.proxy.url:#{null}}")
	private String fhirStoreProxyUrl;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Proxy username, set if the server containing the FHIR data can only be reached through a proxy which requests authentication")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.proxy.username:#{null}}")
	private String fhirStoreProxyUsername;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "Proxy password, set if the server containing the FHIR data can only be reached through a proxy which requests authentication", recommendation = "Use docker secret file to configure by using *${env_variable}_FILE*")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.server.proxy.password:#{null}}")
	private String fhirStoreProxyPassword;

	@ProcessDocumentation(processNames = {
			"medizininformatik-initiativede_reportSend" }, description = "To enable debug logging of FHIR resources set to `true`")
	@Value("${de.medizininformatik.initiative.report.dic.fhir.dataLoggingEnabled:false}")
	private boolean fhirDataLoggingEnabled;

	@Value("${dev.dsf.bpe.fhir.server.organization.identifier.value}")
	private String localIdentifierValue;

	public FhirClientFactory fhirClientFactory()
	{
		Path trustStorePath = checkExists(fhirStoreTrustStore);
		Path certificatePath = checkExists(fhirStoreCertificate);
		Path privateKeyPath = checkExists(fhirStorePrivateKey);

		return new FhirClientFactory(trustStorePath, certificatePath, privateKeyPath, fhirStorePrivateKeyPassword,
				fhirStoreConnectTimeout, fhirStoreSocketTimeout, fhirStoreConnectionRequestTimeout, fhirStoreBaseUrl,
				fhirStoreUsername, fhirStorePassword, fhirStoreBearerToken, fhirStoreProxyUrl, fhirStoreProxyUsername,
				fhirStoreProxyPassword, fhirStoreHapiClientVerbose, fhirContext, localIdentifierValue, dataLogger());
	}

	public DataLogger dataLogger()
	{
		return new DataLogger(fhirDataLoggingEnabled, fhirContext);
	}

	private Path checkExists(String file)
	{
		if (file == null)
			return null;
		else
		{
			Path path = Paths.get(file);

			if (!Files.isReadable(path))
				throw new RuntimeException(path.toString() + " not readable");

			return path;
		}
	}
}
