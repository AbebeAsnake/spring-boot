/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.endpoint;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.endpoint.Endpoint;
import org.springframework.boot.endpoint.ReadOperation;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link Endpoint} to expose audit events.
 *
 * @author Andy Wilkinson
 * @since 2.0.0
 */
@Endpoint(id = "auditevents")
public class AuditEventsEndpoint {

	private final AuditEventRepository auditEventRepository;

	public AuditEventsEndpoint(AuditEventRepository auditEventRepository) {
		Assert.notNull(auditEventRepository, "AuditEventRepository must not be null");
		this.auditEventRepository = auditEventRepository;
	}

	@ReadOperation
	public List<AuditEvent> eventsWithPrincipalDateAfterAndType(String principal,
			String after, String type) {
		return this.auditEventRepository.find(principal, parseDate(after), type);
	}

	private Date parseDate(String date) {
		try {
			if (StringUtils.hasLength(date)) {
				OffsetDateTime offsetDateTime = OffsetDateTime.parse(date,
						DateTimeFormatter.ISO_OFFSET_DATE_TIME);
				return new Date(offsetDateTime.toEpochSecond() * 1000);
			}
			return null;
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

}
