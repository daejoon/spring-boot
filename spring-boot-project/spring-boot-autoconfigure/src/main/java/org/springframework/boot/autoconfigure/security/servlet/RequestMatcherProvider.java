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

package org.springframework.boot.autoconfigure.security.servlet;

import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Interface that can be used to provide a {@link RequestMatcher} that can be used with
 * Spring Security.
 *
 * @author Madhura Bhave
 * @since 2.0.5
 * @deprecated since 3.5.0 for removal in 4.0.0 in favor of
 * {@code org.springframework.boot.actuate.autoconfigure.security.servlet.RequestMatcherProvider}
 */
@Deprecated(since = "3.5.0", forRemoval = true)
@FunctionalInterface
public interface RequestMatcherProvider {

	/**
	 * Return the {@link RequestMatcher} to be used for the specified pattern.
	 * @param pattern the request pattern
	 * @return a request matcher
	 */
	RequestMatcher getRequestMatcher(String pattern);

}
