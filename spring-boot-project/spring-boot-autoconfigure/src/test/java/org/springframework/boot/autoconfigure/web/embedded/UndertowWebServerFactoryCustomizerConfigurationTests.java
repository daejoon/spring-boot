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

package org.springframework.boot.autoconfigure.web.embedded;

import io.undertow.servlet.api.DeploymentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration.UndertowWebServerFactoryCustomizerConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UndertowWebServerFactoryCustomizerConfiguration}.
 *
 * @author Moritz Halbritter
 */
class UndertowWebServerFactoryCustomizerConfigurationTests {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner(
			AnnotationConfigServletWebApplicationContext::new)
		.withConfiguration(AutoConfigurations.of(EmbeddedWebServerFactoryCustomizerAutoConfiguration.class));

	@EnabledForJreRange(min = JRE.JAVA_21)
	@Test
	void shouldUseVirtualThreadsIfEnabled() {
		this.contextRunner.withPropertyValues("spring.threads.virtual.enabled=true").run((context) -> {
			assertThat(context).hasSingleBean(UndertowDeploymentInfoCustomizer.class);
			assertThat(context).hasBean("virtualThreadsUndertowDeploymentInfoCustomizer");
			UndertowDeploymentInfoCustomizer customizer = context.getBean(UndertowDeploymentInfoCustomizer.class);
			DeploymentInfo deploymentInfo = new DeploymentInfo();
			customizer.customize(deploymentInfo);
			assertThat(deploymentInfo.getExecutor()).isInstanceOf(VirtualThreadTaskExecutor.class);
		});
	}

}
