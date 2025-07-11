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

package org.springframework.boot.autoconfigure.web.servlet;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.server.WebServerFactoryCustomizerBeanPostProcessor;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext;
import org.springframework.boot.web.servlet.filter.OrderedFormContentFilter;
import org.springframework.boot.web.servlet.filter.OrderedHiddenHttpMethodFilter;
import org.springframework.boot.web.servlet.server.MockServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.HiddenHttpMethodFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link HttpEncodingAutoConfiguration}
 *
 * @author Stephane Nicoll
 */
class HttpEncodingAutoConfigurationTests {

	private AnnotationConfigServletWebApplicationContext context;

	@AfterEach
	void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	void defaultConfiguration() {
		load(EmptyConfiguration.class);
		CharacterEncodingFilter filter = this.context.getBean(CharacterEncodingFilter.class);
		assertCharacterEncodingFilter(filter, "UTF-8", true, false);
	}

	@Test
	void disableConfiguration() {
		load(EmptyConfiguration.class, "spring.servlet.encoding.enabled:false");
		assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
			.isThrownBy(() -> this.context.getBean(CharacterEncodingFilter.class));
	}

	@Test
	void customConfiguration() {
		load(EmptyConfiguration.class, "spring.servlet.encoding.charset:ISO-8859-15",
				"spring.servlet.encoding.force:false");
		CharacterEncodingFilter filter = this.context.getBean(CharacterEncodingFilter.class);
		assertCharacterEncodingFilter(filter, "ISO-8859-15", false, false);
	}

	@Test
	void customFilterConfiguration() {
		load(FilterConfiguration.class, "spring.servlet.encoding.charset:ISO-8859-15",
				"spring.servlet.encoding.force:false");
		CharacterEncodingFilter filter = this.context.getBean(CharacterEncodingFilter.class);
		assertCharacterEncodingFilter(filter, "US-ASCII", false, false);
	}

	@Test
	void forceRequest() {
		load(EmptyConfiguration.class, "spring.servlet.encoding.force-request:false");
		CharacterEncodingFilter filter = this.context.getBean(CharacterEncodingFilter.class);
		assertCharacterEncodingFilter(filter, "UTF-8", false, false);
	}

	@Test
	void forceResponse() {
		load(EmptyConfiguration.class, "spring.servlet.encoding.force-response:true");
		CharacterEncodingFilter filter = this.context.getBean(CharacterEncodingFilter.class);
		assertCharacterEncodingFilter(filter, "UTF-8", true, true);
	}

	@Test
	void forceRequestOverridesForce() {
		load(EmptyConfiguration.class, "spring.servlet.encoding.force:true",
				"spring.servlet.encoding.force-request:false");
		CharacterEncodingFilter filter = this.context.getBean(CharacterEncodingFilter.class);
		assertCharacterEncodingFilter(filter, "UTF-8", false, true);
	}

	@Test
	void forceResponseOverridesForce() {
		load(EmptyConfiguration.class, "spring.servlet.encoding.force:true",
				"spring.servlet.encoding.force-response:false");
		CharacterEncodingFilter filter = this.context.getBean(CharacterEncodingFilter.class);
		assertCharacterEncodingFilter(filter, "UTF-8", true, false);
	}

	@Test
	void filterIsOrderedHighest() {
		load(OrderedConfiguration.class);
		List<Filter> beans = new ArrayList<>(this.context.getBeansOfType(Filter.class).values());
		AnnotationAwareOrderComparator.sort(beans);
		assertThat(beans.get(0)).isInstanceOf(CharacterEncodingFilter.class);
		assertThat(beans.get(1)).isInstanceOf(HiddenHttpMethodFilter.class);
	}

	private void assertCharacterEncodingFilter(CharacterEncodingFilter actual, String encoding,
			boolean forceRequestEncoding, boolean forceResponseEncoding) {
		assertThat(actual.getEncoding()).isEqualTo(encoding);
		assertThat(actual.isForceRequestEncoding()).isEqualTo(forceRequestEncoding);
		assertThat(actual.isForceResponseEncoding()).isEqualTo(forceResponseEncoding);
	}

	private void load(Class<?> config, String... environment) {
		this.context = doLoad(new Class<?>[] { config }, environment);
	}

	private AnnotationConfigServletWebApplicationContext doLoad(Class<?>[] configs, String... environment) {
		AnnotationConfigServletWebApplicationContext applicationContext = new AnnotationConfigServletWebApplicationContext();
		TestPropertyValues.of(environment).applyTo(applicationContext);
		applicationContext.register(configs);
		applicationContext.register(MinimalWebAutoConfiguration.class, HttpEncodingAutoConfiguration.class);
		applicationContext.setServletContext(new MockServletContext());
		applicationContext.refresh();
		return applicationContext;
	}

	@Configuration(proxyBeanMethods = false)
	static class EmptyConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	static class FilterConfiguration {

		@Bean
		CharacterEncodingFilter myCharacterEncodingFilter() {
			CharacterEncodingFilter filter = new CharacterEncodingFilter();
			filter.setEncoding("US-ASCII");
			filter.setForceEncoding(false);
			return filter;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class OrderedConfiguration {

		@Bean
		OrderedHiddenHttpMethodFilter hiddenHttpMethodFilter() {
			return new OrderedHiddenHttpMethodFilter();
		}

		@Bean
		OrderedFormContentFilter formContentFilter() {
			return new OrderedFormContentFilter();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class MinimalWebAutoConfiguration {

		@Bean
		MockServletWebServerFactory mockServletWebServerFactory() {
			return new MockServletWebServerFactory();
		}

		@Bean
		static WebServerFactoryCustomizerBeanPostProcessor servletWebServerCustomizerBeanPostProcessor() {
			return new WebServerFactoryCustomizerBeanPostProcessor();
		}

	}

}
