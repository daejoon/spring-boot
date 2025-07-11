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

package org.springframework.boot.autoconfigure.web.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateRequestCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link RestTemplate} (via
 * {@link RestTemplateBuilder}).
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @since 1.4.0
 */
@AutoConfiguration(after = { HttpClientAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class })
@ConditionalOnClass(RestTemplate.class)
@Conditional(NotReactiveWebApplicationCondition.class)
public class RestTemplateAutoConfiguration {

	@Bean
	@Lazy
	public RestTemplateBuilderConfigurer restTemplateBuilderConfigurer(
			ObjectProvider<ClientHttpRequestFactoryBuilder<?>> clientHttpRequestFactoryBuilder,
			ObjectProvider<ClientHttpRequestFactorySettings> clientHttpRequestFactorySettings,
			ObjectProvider<HttpMessageConverters> messageConverters,
			ObjectProvider<RestTemplateCustomizer> restTemplateCustomizers,
			ObjectProvider<RestTemplateRequestCustomizer<?>> restTemplateRequestCustomizers) {
		RestTemplateBuilderConfigurer configurer = new RestTemplateBuilderConfigurer();
		configurer.setRequestFactoryBuilder(clientHttpRequestFactoryBuilder.getIfAvailable());
		configurer.setRequestFactorySettings(clientHttpRequestFactorySettings.getIfAvailable());
		configurer.setHttpMessageConverters(messageConverters.getIfUnique());
		configurer.setRestTemplateCustomizers(restTemplateCustomizers.orderedStream().toList());
		configurer.setRestTemplateRequestCustomizers(restTemplateRequestCustomizers.orderedStream().toList());
		return configurer;
	}

	@Bean
	@Lazy
	@ConditionalOnMissingBean
	public RestTemplateBuilder restTemplateBuilder(RestTemplateBuilderConfigurer restTemplateBuilderConfigurer) {
		return restTemplateBuilderConfigurer.configure(new RestTemplateBuilder());
	}

}
