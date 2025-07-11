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

package smoketest.propertyvalidation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link SamplePropertyValidationApplication}.
 *
 * @author Lucas Saldanha
 * @author Stephane Nicoll
 */
class SamplePropertyValidationApplicationTests {

	private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	void closeContext() {
		this.context.close();
	}

	@Test
	void bindValidProperties() {
		this.context.register(SamplePropertyValidationApplication.class);
		TestPropertyValues.of("sample.host:192.168.0.1", "sample.port:9090").applyTo(this.context);
		this.context.refresh();
		SampleProperties properties = this.context.getBean(SampleProperties.class);
		assertThat(properties.getHost()).isEqualTo("192.168.0.1");
		assertThat(properties.getPort()).isEqualTo(Integer.valueOf(9090));
	}

	@Test
	void bindInvalidHost() {
		this.context.register(SamplePropertyValidationApplication.class);
		TestPropertyValues.of("sample.host:xxxxxx", "sample.port:9090").applyTo(this.context);
		assertThatExceptionOfType(BeanCreationException.class).isThrownBy(this.context::refresh)
			.havingRootCause()
			.isInstanceOf(BindValidationException.class);
	}

	@Test
	void bindNullHost() {
		this.context.register(SamplePropertyValidationApplication.class);
		assertThatExceptionOfType(BeanCreationException.class).isThrownBy(this.context::refresh)
			.havingRootCause()
			.isInstanceOf(BindValidationException.class);
	}

	@Test
	void validatorOnlyCalledOnSupportedClass() {
		this.context.register(SamplePropertyValidationApplication.class);
		this.context.register(ServerProperties.class); // our validator will not apply
		TestPropertyValues.of("sample.host:192.168.0.1", "sample.port:9090").applyTo(this.context);
		this.context.refresh();
		SampleProperties properties = this.context.getBean(SampleProperties.class);
		assertThat(properties.getHost()).isEqualTo("192.168.0.1");
		assertThat(properties.getPort()).isEqualTo(Integer.valueOf(9090));
	}

}
