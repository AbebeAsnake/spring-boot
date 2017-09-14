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

package org.springframework.boot.actuate.metrics.scheduling;

import java.util.concurrent.CountDownLatch;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScheduledMethodMetrics}.
 *
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@TestPropertySource(properties = "metrics.useGlobalRegistry=false")
public class ScheduledMethodMetricsTests {

	private static final CountDownLatch longTaskStarted = new CountDownLatch(1);

	private static final CountDownLatch longTaskShouldComplete = new CountDownLatch(1);

	private static final CountDownLatch shortBeepsExecuted = new CountDownLatch(1);

	@Autowired
	private MeterRegistry registry;

	@Autowired
	private ThreadPoolTaskScheduler scheduler;

	@Test
	public void shortTasksAreInstrumented() throws InterruptedException {
		ScheduledMethodMetricsTests.shortBeepsExecuted.await();
		while (this.scheduler.getActiveCount() > 0) {
			// Tight loop
		}
		assertThat(this.registry.find("beeper").value(Statistic.Count, 1.0).timer())
				.isPresent();
		assertThat(this.registry.find("beeper").tags("quantile", "0.5").gauge())
				.isNotEmpty();
		assertThat(this.registry.find("beeper").tags("quantile", "0.95").gauge())
				.isNotEmpty();
	}

	@Test
	public void longTasksAreInstrumented() throws InterruptedException {
		ScheduledMethodMetricsTests.longTaskStarted.await();
		assertThat(this.registry.find("long.beep").value(Statistic.Count, 1.0)
				.longTaskTimer()).isPresent();
		// make sure longBeep continues running until we have a chance to observe it in
		// the active state
		ScheduledMethodMetricsTests.longTaskShouldComplete.countDown();
		while (this.scheduler.getActiveCount() > 0) {
			// Tight loop
		}
		assertThat(this.registry.find("long.beep").value(Statistic.Count, 0.0)
				.longTaskTimer()).isPresent();
	}

	@EnableScheduling
	@EnableAspectJAutoProxy
	@Configuration
	static class MetricsApp {

		@Bean
		MeterRegistry registry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		ThreadPoolTaskScheduler scheduler() {
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			// this way, executing longBeep doesn't block the short tasks from running
			scheduler.setPoolSize(6);
			return scheduler;
		}

		@Bean
		ScheduledMethodMetrics ScheduledMethodMetrics(MeterRegistry meterRegistry) {
			return new ScheduledMethodMetrics(meterRegistry);
		}

		@Timed(value = "long.beep", longTask = true)
		@Scheduled(fixedDelay = 100_000)
		void longBeep() throws InterruptedException {
			ScheduledMethodMetricsTests.longTaskStarted.countDown();
			ScheduledMethodMetricsTests.longTaskShouldComplete.await();
			System.out.println("beep");
		}

		@Timed(value = "beeper", quantiles = { 0.5, 0.95 })
		@Scheduled(fixedDelay = 100_000)
		void shortBeep() {
			ScheduledMethodMetricsTests.shortBeepsExecuted.countDown();
			System.out.println("beep");
		}

		@Scheduled(fixedDelay = 100_000) // not instrumented because it isn't @Timed
		void notTimed() {
			System.out.println("beep");
		}

	}

}
