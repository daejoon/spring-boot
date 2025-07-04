/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.embedded.tomcat;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.AprLifecycleListener;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.Tomcat.FixContextListener;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.scan.StandardJarScanFilter;

import org.springframework.boot.util.LambdaSafe;
import org.springframework.boot.web.reactive.server.AbstractReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.TomcatHttpHandlerAdapter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ReactiveWebServerFactory} that can be used to create a {@link TomcatWebServer}.
 *
 * @author Brian Clozel
 * @author HaiTao Zhang
 * @author Moritz Halbritter
 * @author Scott Frederick
 * @since 2.0.0
 */
public class TomcatReactiveWebServerFactory extends AbstractReactiveWebServerFactory
		implements ConfigurableTomcatWebServerFactory {

	private static final Log logger = LogFactory.getLog(TomcatReactiveWebServerFactory.class);

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	/**
	 * The class name of default protocol used.
	 */
	public static final String DEFAULT_PROTOCOL = "org.apache.coyote.http11.Http11NioProtocol";

	private File baseDirectory;

	private final List<Valve> engineValves = new ArrayList<>();

	private List<LifecycleListener> contextLifecycleListeners = new ArrayList<>();

	private Set<TomcatContextCustomizer> tomcatContextCustomizers = new LinkedHashSet<>();

	private Set<TomcatConnectorCustomizer> tomcatConnectorCustomizers = new LinkedHashSet<>();

	private Set<TomcatProtocolHandlerCustomizer<?>> tomcatProtocolHandlerCustomizers = new LinkedHashSet<>();

	private final List<Connector> additionalTomcatConnectors = new ArrayList<>();

	private String protocol = DEFAULT_PROTOCOL;

	private Charset uriEncoding = DEFAULT_CHARSET;

	private int backgroundProcessorDelay;

	private boolean disableMBeanRegistry = true;

	private boolean useApr;

	/**
	 * Create a new {@link TomcatReactiveWebServerFactory} instance.
	 */
	public TomcatReactiveWebServerFactory() {
	}

	/**
	 * Create a new {@link TomcatReactiveWebServerFactory} that listens for requests using
	 * the specified port.
	 * @param port the port to listen on
	 */
	public TomcatReactiveWebServerFactory(int port) {
		super(port);
	}

	private List<LifecycleListener> getDefaultServerLifecycleListeners() {
		ArrayList<LifecycleListener> lifecycleListeners = new ArrayList<>();
		if (this.useApr) {
			lifecycleListeners.add(new AprLifecycleListener());
		}
		return lifecycleListeners;
	}

	@Override
	public WebServer getWebServer(HttpHandler httpHandler) {
		if (this.disableMBeanRegistry) {
			Registry.disableRegistry();
		}
		Tomcat tomcat = new Tomcat();
		File baseDir = (this.baseDirectory != null) ? this.baseDirectory : createTempDir("tomcat");
		tomcat.setBaseDir(baseDir.getAbsolutePath());
		for (LifecycleListener listener : getDefaultServerLifecycleListeners()) {
			tomcat.getServer().addLifecycleListener(listener);
		}
		Connector connector = new Connector(this.protocol);
		connector.setThrowOnFailure(true);
		tomcat.getService().addConnector(connector);
		customizeConnector(connector);
		tomcat.setConnector(connector);
		registerConnectorExecutor(tomcat, connector);
		tomcat.getHost().setAutoDeploy(false);
		configureEngine(tomcat.getEngine());
		for (Connector additionalConnector : this.additionalTomcatConnectors) {
			tomcat.getService().addConnector(additionalConnector);
			registerConnectorExecutor(tomcat, additionalConnector);
		}
		TomcatHttpHandlerAdapter servlet = new TomcatHttpHandlerAdapter(httpHandler);
		prepareContext(tomcat.getHost(), servlet);
		return getTomcatWebServer(tomcat);
	}

	private void registerConnectorExecutor(Tomcat tomcat, Connector connector) {
		if (connector.getProtocolHandler().getExecutor() instanceof Executor executor) {
			tomcat.getService().addExecutor(executor);
		}
	}

	private void configureEngine(Engine engine) {
		engine.setBackgroundProcessorDelay(this.backgroundProcessorDelay);
		for (Valve valve : this.engineValves) {
			engine.getPipeline().addValve(valve);
		}
	}

	protected void prepareContext(Host host, TomcatHttpHandlerAdapter servlet) {
		File docBase = createTempDir("tomcat-docbase");
		TomcatEmbeddedContext context = new TomcatEmbeddedContext();
		StandardRoot resourcesRoot = new StandardRoot();
		resourcesRoot.setReadOnly(true);
		context.setResources(resourcesRoot);
		context.setPath("");
		context.setDocBase(docBase.getAbsolutePath());
		context.addLifecycleListener(new FixContextListener());
		ClassLoader parentClassLoader = ClassUtils.getDefaultClassLoader();
		context.setParentClassLoader(parentClassLoader);
		skipAllTldScanning(context);
		WebappLoader loader = new WebappLoader();
		loader.setLoaderInstance(new TomcatEmbeddedWebappClassLoader(parentClassLoader));
		loader.setDelegate(true);
		context.setLoader(loader);
		Tomcat.addServlet(context, "httpHandlerServlet", servlet).setAsyncSupported(true);
		context.addServletMappingDecoded("/", "httpHandlerServlet");
		host.addChild(context);
		configureContext(context);
	}

	private void skipAllTldScanning(TomcatEmbeddedContext context) {
		StandardJarScanFilter filter = new StandardJarScanFilter();
		filter.setTldSkip("*.jar");
		context.getJarScanner().setJarScanFilter(filter);
	}

	/**
	 * Configure the Tomcat {@link Context}.
	 * @param context the Tomcat context
	 */
	protected void configureContext(Context context) {
		this.contextLifecycleListeners.forEach(context::addLifecycleListener);
		new DisableReferenceClearingContextCustomizer().customize(context);
		this.tomcatContextCustomizers.forEach((customizer) -> customizer.customize(context));
	}

	protected void customizeConnector(Connector connector) {
		int port = Math.max(getPort(), 0);
		connector.setPort(port);
		if (StringUtils.hasText(getServerHeader())) {
			connector.setProperty("server", getServerHeader());
		}
		if (connector.getProtocolHandler() instanceof AbstractProtocol<?> abstractProtocol) {
			customizeProtocol(abstractProtocol);
		}
		invokeProtocolHandlerCustomizers(connector.getProtocolHandler());
		if (getUriEncoding() != null) {
			connector.setURIEncoding(getUriEncoding().name());
		}
		if (getHttp2() != null && getHttp2().isEnabled()) {
			connector.addUpgradeProtocol(new Http2Protocol());
		}
		if (Ssl.isEnabled(getSsl())) {
			customizeSsl(connector);
		}
		TomcatConnectorCustomizer compression = new CompressionConnectorCustomizer(getCompression());
		compression.customize(connector);
		for (TomcatConnectorCustomizer customizer : this.tomcatConnectorCustomizers) {
			customizer.customize(connector);
		}
	}

	@SuppressWarnings("unchecked")
	private void invokeProtocolHandlerCustomizers(ProtocolHandler protocolHandler) {
		LambdaSafe
			.callbacks(TomcatProtocolHandlerCustomizer.class, this.tomcatProtocolHandlerCustomizers, protocolHandler)
			.invoke((customizer) -> customizer.customize(protocolHandler));
	}

	private void customizeProtocol(AbstractProtocol<?> protocol) {
		if (getAddress() != null) {
			protocol.setAddress(getAddress());
		}
	}

	private void customizeSsl(Connector connector) {
		SslConnectorCustomizer customizer = new SslConnectorCustomizer(logger, connector, getSsl().getClientAuth());
		customizer.customize(getSslBundle(), getServerNameSslBundles());
		addBundleUpdateHandler(null, getSsl().getBundle(), customizer);
		getSsl().getServerNameBundles()
			.forEach((serverNameSslBundle) -> addBundleUpdateHandler(serverNameSslBundle.serverName(),
					serverNameSslBundle.bundle(), customizer));
	}

	private void addBundleUpdateHandler(String serverName, String sslBundleName, SslConnectorCustomizer customizer) {
		if (StringUtils.hasText(sslBundleName)) {
			getSslBundles().addBundleUpdateHandler(sslBundleName,
					(sslBundle) -> customizer.update(serverName, sslBundle));
		}
	}

	@Override
	public void setBaseDirectory(File baseDirectory) {
		this.baseDirectory = baseDirectory;
	}

	@Override
	public void setBackgroundProcessorDelay(int delay) {
		this.backgroundProcessorDelay = delay;
	}

	/**
	 * Set {@link TomcatContextCustomizer}s that should be applied to the Tomcat
	 * {@link Context}. Calling this method will replace any existing customizers.
	 * @param tomcatContextCustomizers the customizers to set
	 */
	public void setTomcatContextCustomizers(Collection<? extends TomcatContextCustomizer> tomcatContextCustomizers) {
		Assert.notNull(tomcatContextCustomizers, "'tomcatContextCustomizers' must not be null");
		this.tomcatContextCustomizers = new LinkedHashSet<>(tomcatContextCustomizers);
	}

	/**
	 * Returns a mutable collection of the {@link TomcatContextCustomizer}s that will be
	 * applied to the Tomcat {@link Context}.
	 * @return the listeners that will be applied
	 */
	public Collection<TomcatContextCustomizer> getTomcatContextCustomizers() {
		return this.tomcatContextCustomizers;
	}

	/**
	 * Add {@link TomcatContextCustomizer}s that should be added to the Tomcat
	 * {@link Context}.
	 * @param tomcatContextCustomizers the customizers to add
	 */
	@Override
	public void addContextCustomizers(TomcatContextCustomizer... tomcatContextCustomizers) {
		Assert.notNull(tomcatContextCustomizers, "'tomcatContextCustomizers' must not be null");
		this.tomcatContextCustomizers.addAll(Arrays.asList(tomcatContextCustomizers));
	}

	/**
	 * Set {@link TomcatConnectorCustomizer}s that should be applied to the Tomcat
	 * {@link Connector}. Calling this method will replace any existing customizers.
	 * @param tomcatConnectorCustomizers the customizers to set
	 */
	public void setTomcatConnectorCustomizers(
			Collection<? extends TomcatConnectorCustomizer> tomcatConnectorCustomizers) {
		Assert.notNull(tomcatConnectorCustomizers, "'tomcatConnectorCustomizers' must not be null");
		this.tomcatConnectorCustomizers = new LinkedHashSet<>(tomcatConnectorCustomizers);
	}

	/**
	 * Add {@link TomcatConnectorCustomizer}s that should be added to the Tomcat
	 * {@link Connector}.
	 * @param tomcatConnectorCustomizers the customizers to add
	 */
	@Override
	public void addConnectorCustomizers(TomcatConnectorCustomizer... tomcatConnectorCustomizers) {
		Assert.notNull(tomcatConnectorCustomizers, "'tomcatConnectorCustomizers' must not be null");
		this.tomcatConnectorCustomizers.addAll(Arrays.asList(tomcatConnectorCustomizers));
	}

	/**
	 * Returns a mutable collection of the {@link TomcatConnectorCustomizer}s that will be
	 * applied to the Tomcat {@link Connector}.
	 * @return the customizers that will be applied
	 */
	public Collection<TomcatConnectorCustomizer> getTomcatConnectorCustomizers() {
		return this.tomcatConnectorCustomizers;
	}

	/**
	 * Set {@link TomcatProtocolHandlerCustomizer}s that should be applied to the Tomcat
	 * {@link Connector}. Calling this method will replace any existing customizers.
	 * @param tomcatProtocolHandlerCustomizers the customizers to set
	 * @since 2.2.0
	 */
	public void setTomcatProtocolHandlerCustomizers(
			Collection<? extends TomcatProtocolHandlerCustomizer<?>> tomcatProtocolHandlerCustomizers) {
		Assert.notNull(tomcatProtocolHandlerCustomizers, "'tomcatProtocolHandlerCustomizers' must not be null");
		this.tomcatProtocolHandlerCustomizers = new LinkedHashSet<>(tomcatProtocolHandlerCustomizers);
	}

	/**
	 * Add {@link TomcatProtocolHandlerCustomizer}s that should be added to the Tomcat
	 * {@link Connector}.
	 * @param tomcatProtocolHandlerCustomizers the customizers to add
	 * @since 2.2.0
	 */
	@Override
	public void addProtocolHandlerCustomizers(TomcatProtocolHandlerCustomizer<?>... tomcatProtocolHandlerCustomizers) {
		Assert.notNull(tomcatProtocolHandlerCustomizers, "'tomcatProtocolHandlerCustomizers' must not be null");
		this.tomcatProtocolHandlerCustomizers.addAll(Arrays.asList(tomcatProtocolHandlerCustomizers));
	}

	/**
	 * Returns a mutable collection of the {@link TomcatProtocolHandlerCustomizer}s that
	 * will be applied to the Tomcat {@link Connector}.
	 * @return the customizers that will be applied
	 * @since 2.2.0
	 */
	public Collection<TomcatProtocolHandlerCustomizer<?>> getTomcatProtocolHandlerCustomizers() {
		return this.tomcatProtocolHandlerCustomizers;
	}

	/**
	 * Add {@link Connector}s in addition to the default connector, e.g. for SSL or AJP.
	 * <p>
	 * {@link #getTomcatConnectorCustomizers Connector customizers} are not applied to
	 * connectors added this way.
	 * @param connectors the connectors to add
	 * @since 2.2.0
	 */
	public void addAdditionalTomcatConnectors(Connector... connectors) {
		Assert.notNull(connectors, "'connectors' must not be null");
		this.additionalTomcatConnectors.addAll(Arrays.asList(connectors));
	}

	/**
	 * Returns a mutable collection of the {@link Connector}s that will be added to the
	 * Tomcat.
	 * @return the additionalTomcatConnectors
	 * @since 2.2.0
	 */
	public List<Connector> getAdditionalTomcatConnectors() {
		return this.additionalTomcatConnectors;
	}

	@Override
	public void addEngineValves(Valve... engineValves) {
		Assert.notNull(engineValves, "'engineValves' must not be null");
		this.engineValves.addAll(Arrays.asList(engineValves));
	}

	/**
	 * Returns a mutable collection of the {@link Valve}s that will be applied to the
	 * Tomcat {@link Engine}.
	 * @return the engine valves that will be applied
	 */
	public List<Valve> getEngineValves() {
		return this.engineValves;
	}

	/**
	 * Set the character encoding to use for URL decoding. If not specified 'UTF-8' will
	 * be used.
	 * @param uriEncoding the uri encoding to set
	 */
	@Override
	public void setUriEncoding(Charset uriEncoding) {
		this.uriEncoding = uriEncoding;
	}

	/**
	 * Returns the character encoding to use for URL decoding.
	 * @return the URI encoding
	 */
	public Charset getUriEncoding() {
		return this.uriEncoding;
	}

	/**
	 * Set {@link LifecycleListener}s that should be applied to the Tomcat
	 * {@link Context}. Calling this method will replace any existing listeners.
	 * @param contextLifecycleListeners the listeners to set
	 */
	public void setContextLifecycleListeners(Collection<? extends LifecycleListener> contextLifecycleListeners) {
		Assert.notNull(contextLifecycleListeners, "'contextLifecycleListeners' must not be null");
		this.contextLifecycleListeners = new ArrayList<>(contextLifecycleListeners);
	}

	/**
	 * Returns a mutable collection of the {@link LifecycleListener}s that will be applied
	 * to the Tomcat {@link Context}.
	 * @return the context lifecycle listeners that will be applied
	 */
	public Collection<LifecycleListener> getContextLifecycleListeners() {
		return this.contextLifecycleListeners;
	}

	/**
	 * Add {@link LifecycleListener}s that should be added to the Tomcat {@link Context}.
	 * @param contextLifecycleListeners the listeners to add
	 */
	public void addContextLifecycleListeners(LifecycleListener... contextLifecycleListeners) {
		Assert.notNull(contextLifecycleListeners, "'contextLifecycleListeners' must not be null");
		this.contextLifecycleListeners.addAll(Arrays.asList(contextLifecycleListeners));
	}

	/**
	 * Factory method called to create the {@link TomcatWebServer}. Subclasses can
	 * override this method to return a different {@link TomcatWebServer} or apply
	 * additional processing to the Tomcat server.
	 * @param tomcat the Tomcat server.
	 * @return a new {@link TomcatWebServer} instance
	 */
	protected TomcatWebServer getTomcatWebServer(Tomcat tomcat) {
		return new TomcatWebServer(tomcat, getPort() >= 0, getShutdown());
	}

	/**
	 * The Tomcat protocol to use when create the {@link Connector}.
	 * @param protocol the protocol
	 * @see Connector#Connector(String)
	 */
	public void setProtocol(String protocol) {
		Assert.hasLength(protocol, "'protocol' must not be empty");
		this.protocol = protocol;
	}

	/**
	 * Set whether the factory should disable Tomcat's MBean registry prior to creating
	 * the server.
	 * @param disableMBeanRegistry whether to disable the MBean registry
	 * @since 2.2.0
	 */
	public void setDisableMBeanRegistry(boolean disableMBeanRegistry) {
		this.disableMBeanRegistry = disableMBeanRegistry;
	}

	/**
	 * Whether to use APR.
	 * @param useApr whether to use APR
	 * @since 3.4.4
	 */
	public void setUseApr(boolean useApr) {
		this.useApr = useApr;
	}

}
