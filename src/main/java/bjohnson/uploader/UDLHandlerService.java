package bjohnson.uploader;

import org.apache.commons.math3.util.Pair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;



public class UDLHandlerService {

    public static final String UDL_SCHEME = "UDL_SCHEME";
    public static final String UDL_USR = "UDL_USR";
    public static final String UDL_PWD = "UDL_PWD";
    public static final String UDL_HOST = "UDL_HOST";


    private static final int MAX_INPUT_SIZE = 1000000; //1MB

    static final Logger logger = LoggerFactory.getLogger(UDLHandlerService.class);

    CloseableHttpClient httpClient;

    HttpClientContext httpClientContext;

    private String hostName;
    private String protocol;

    private EnvironmentVariables environmentVariables;

    public UDLHandlerService(String hostname, String scheme, String user, String pass) {
        this.environmentVariables=EnvironmentVariables.getInstance();
        setupClientAndInitialize(hostname, scheme, user, pass);
    }

    private void setupClientAndInitialize(String hostname, String scheme, String user, String pass) {


        scheme = setUdlScheme(scheme);
        hostname = getEnvHostname(hostname);

        try {
            httpClient = createTrustAllClient(0);
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            logger.error(e.getMessage(),e);
        }
        String pwd = environmentVariables.get(UDL_PWD);
        String username = environmentVariables.get(UDL_USR);
        if (user != null && !user.isEmpty()) {
            username=user;
        }
        if (pass != null && !pass.isEmpty()) {
            pwd=pass;
        }
        setup(username, pwd, hostname, scheme);
    }
    private String setUdlScheme(String scheme) {
        if (scheme==null || scheme.isEmpty()) {
            scheme = environmentVariables.get(UDL_SCHEME);
        }
        if (null == scheme) {
            logger.warn("UDL scheme was null");
            scheme ="http";
        }
        return scheme;
    }

    private String getEnvHostname(String hostname) {
        if (hostname==null || hostname.isEmpty()) {
                hostname=environmentVariables.get(UDL_HOST);
        }
        if (hostname==null || hostname.isEmpty()) {
                logger.warn("UDL hostname was null");
                hostname ="localhost";
        }
        return hostname;
    }

    public void setup(String username, String pwd, String hostname, String scheme) {
        this.hostName =hostname;
        this.protocol =scheme;

        if (username != null) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(hostname, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(username, pwd));
            httpClientContext = HttpClientContext.create();
            httpClientContext.setCredentialsProvider(credsProvider);
        }
    }

    public Pair<Integer,String> send(String method, String jsonText, String path) throws URISyntaxException, HTTPException {
        URI uri = new URIBuilder()
                .setScheme(protocol)
                .setHost(hostName)
                .setPath(path)
                .build();
        StringEntity requestEntity = new StringEntity(jsonText, ContentType.APPLICATION_JSON);
        HttpUriRequest httpReq=null;
        if (method.equalsIgnoreCase("POST")) {
            httpReq = new HttpPost(uri);
            ((HttpPost)httpReq).setEntity(requestEntity);
        }else{
            httpReq = new HttpGet(uri);
        }


        String ip;
        try {
            ip = InetAddress.getByName(httpReq.getURI().getHost()).getHostAddress();
            logger.info("Sending {} request to {} at URL {}",method,ip,uri);
        } catch (UnknownHostException e1) {
            logger.error(e1.getMessage());
        }

        try (CloseableHttpResponse response = httpClient.execute(httpReq, httpClientContext)) {
            logger.info("Got response: {}",response);

            long contentLength = response.getEntity().getContentLength();
            if(contentLength > MAX_INPUT_SIZE) {
                throw new HTTPException("Received " + contentLength + " bytes.");
            }
            String content= EntityUtils.toString(response.getEntity());
            int code=response.getStatusLine().getStatusCode();
            if (code > 300) {
                throw new HTTPException("Got an error response code of "+code+"! Response = \n"+content);
            }
            return new Pair<>(code, content);
        } catch (IOException e) {
            logger.error(e.getMessage());
            return new Pair<>(502, "Server connection failed");
        }
    }




    public static CloseableHttpClient createTrustAllClient(int timeout) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

        SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(
                SSLContextBuilder
                    .create()
                    .loadTrustMaterial(new TrustAllStrategy())
                    .build(),
                new NoopHostnameVerifier());

        int timeoutSeconds = timeout*1000;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeoutSeconds)
                .setConnectionRequestTimeout(timeoutSeconds)
                .setSocketTimeout(timeoutSeconds).build();

        return HttpClients
                .custom()
                .setSSLSocketFactory(connectionFactory)
                .setDefaultRequestConfig(config)
                .build();
    }


}
