/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.netflix.titus.master.scheduler.systemselector;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.fenzo.TaskRequest;
import com.netflix.fenzo.TaskTrackerState;
import com.netflix.fenzo.VMTaskFitnessCalculator;
import com.netflix.fenzo.VirtualMachineCurrentState;
import io.netflix.titus.api.agent.service.AgentManagementService;
import io.netflix.titus.api.scheduler.model.Match;
import io.netflix.titus.api.scheduler.service.SchedulerException;
import io.netflix.titus.master.scheduler.SchedulerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netflix.titus.master.scheduler.systemselector.SystemSelectorFunctions.createContext;

/**
 *
 */
@Singleton
public class SystemSelectorFitnessCalculator implements VMTaskFitnessCalculator {

    private static final Logger logger = LoggerFactory.getLogger(SystemSelectorFitnessCalculator.class);
    private static final double NO_MATCH_SCORE = 0.01;

    private final SchedulerConfiguration schedulerConfiguration;
    private final SystemSelectorService systemSelectorService;
    private final SystemSelectorEvaluator systemSelectorEvaluator;
    private final AgentManagementService agentManagementService;

    @Inject
    public SystemSelectorFitnessCalculator(SchedulerConfiguration schedulerConfiguration,
                                           SystemSelectorService systemSelectorService,
                                           SystemSelectorEvaluator systemSelectorEvaluator,
                                           AgentManagementService agentManagementService) {
        this.schedulerConfiguration = schedulerConfiguration;
        this.systemSelectorService = systemSelectorService;
        this.systemSelectorEvaluator = systemSelectorEvaluator;
        this.agentManagementService = agentManagementService;
    }

    @Override
    public String getName() {
        return "System Selector Fitness Calculator";
    }

    @Override
    public double calculateFitness(TaskRequest taskRequest, VirtualMachineCurrentState targetVM, TaskTrackerState taskTrackerState) {
        List<Match> matches = systemSelectorService.getShouldMatchesForEvaluation();
        double totalMatchEvaluations = 0;
        double totalMatches = 0;
        for (Match match : matches) {
            String selectExpression = match.getSelectExpression();
            Map<String, Object> context = createContext(taskRequest, targetVM, agentManagementService, schedulerConfiguration);
            boolean selectResult = false;
            try {
                logger.debug("Evaluating select expression: {} for taskRequest: {} on targetVM: {}", selectExpression, taskRequest, targetVM);
                selectResult = systemSelectorEvaluator.evaluate(selectExpression, context);
                logger.debug("Evaluated select expression: {} for taskRequest: {} on targetVM: {} with result: {}",
                        selectExpression, taskRequest, targetVM, selectResult);
            } catch (SchedulerException ignored) {
            }
            if (selectResult) {
                String matchExpression = match.getMatchExpression();
                boolean matchResult = false;
                try {
                    logger.debug("Evaluating match expression: {} for taskRequest: {} on targetVM: {}", matchExpression, taskRequest, targetVM);
                    matchResult = systemSelectorEvaluator.evaluate(matchExpression, context);
                    logger.debug("Evaluated match expression: {} for taskRequest: {} on targetVM: {} with result: {}",
                            matchExpression, taskRequest, targetVM, matchResult);
                } catch (SchedulerException ignored) {
                }
                if (matchResult) {
                    totalMatches++;
                }
                totalMatchEvaluations++;
            }
        }
        if (totalMatchEvaluations == 0 || totalMatches == 0) {
            return NO_MATCH_SCORE;
        }

        return totalMatches / totalMatchEvaluations;
    }
}
