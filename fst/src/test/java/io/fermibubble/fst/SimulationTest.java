package io.fermibubble.fst;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.fermibubble.fst.Simulator.ts;
import static java.lang.String.format;

public class SimulationTest {
    // @Test
    public void simulateConstantRateStepBF() throws IOException {
        final int timeStepSec = 1;
        final String fileSuffix = "baseline";
        final List<Simulator.TimeStep> serverLoad = ImmutableList.of(ts(0, 200)
                , ts(500e9, 30), ts(1000e9, 200));
        final List<Double> clientRequestTps = ImmutableList.of(150.0d, 150.0d, 150.0d, 10.0d);
        final SimulationConfig config = SimulationConfig.Builder.aSimulationConfig()
                .withClientRequestTps(clientRequestTps)
                .withServerGoodput(Lists.newLinkedList(serverLoad))
                .withOutputFile(format("StepBF_%s_timestep-%d-sec.csv", fileSuffix, timeStepSec))
                .withRunUntil(1800e9)
                .withBuckets(17)
                .withFairThrottleType(SimulationConfig.FairThrottleType.BLOOM_FILTER_FAIR_THROTTLE)
                .withTimeStepSec(timeStepSec)
                .withServerConstantFailureRate(0.0d)
                .build();
        final Simulator simulator = new Simulator();
        simulator.runSimulation(config);
        final SimulationConfig config2 = SimulationConfig.Builder.aSimulationConfig()
                .withClientRequestTps(clientRequestTps)
                .withServerGoodput(Lists.newLinkedList(serverLoad))
                .withOutputFile(format("StepBFNoFairness_%s_timestep-%d-sec.csv", fileSuffix, timeStepSec))
                .withRunUntil(1800e9)
                .withBuckets(1)
                .withFairThrottleType(SimulationConfig.FairThrottleType.BLOOM_FILTER_FAIR_THROTTLE)
                .withTimeStepSec(timeStepSec)
                .withServerConstantFailureRate(0.0)
                .build();
        simulator.runSimulation(config2);
    }

    // @Test
    public void simulateConstantRateStepSFQ() throws IOException {
        final int timeStepSec = 1;
        final String fileSuffix = "baseline";
        final List<Simulator.TimeStep> serverLoad = ImmutableList.of(ts(0,200)
                , ts(500e9, 30), ts(1000e9, 200));
        final List<Double> clientRequestTps = ImmutableList.of(150.0d, 150.0d, 150.0d, 10.0d);
        final SimulationConfig config = SimulationConfig.Builder.aSimulationConfig()
                .withClientRequestTps(clientRequestTps)
                .withServerGoodput(Lists.newLinkedList(serverLoad))
                .withOutputFile(format("StepSFQ_%s_timestep-%d-sec.csv", fileSuffix, timeStepSec))
                .withRunUntil(1800e9)
                .withBuckets(17)
                .withFairThrottleType(SimulationConfig.FairThrottleType.STOCHASTIC_FAIR_THROTTLE)
                .withTimeStepSec(timeStepSec)
                .withServerConstantFailureRate(0.0d)
                .build();
        new Simulator().runSimulation(config);
    }

    //@Test
    public void simulateThousandClients() throws IOException {
        final int timeStepSec = 60;
        final String fileSuffix = "baseline";
        final List<Simulator.TimeStep> serverLoad = ImmutableList.of(ts(0, 2000)
                , ts(500e9, 300), ts(1000e9, 2000));
        final List<Double> clientRequestTps = new ArrayList<>();
        for(int i = 0; i < 1000; i++) clientRequestTps.add(10.0d);
        final SimulationConfig config = SimulationConfig.Builder.aSimulationConfig()
                .withClientRequestTps(clientRequestTps)
                .withServerGoodput(Lists.newLinkedList(serverLoad))
                .withOutputFile(format("StepSFQ1000Clients_%s_timestep-%d-sec.csv", fileSuffix, timeStepSec))
                .withRunUntil(1800e9)
                .withBuckets(17)
                .withFairThrottleType(SimulationConfig.FairThrottleType.STOCHASTIC_FAIR_THROTTLE)
                .withTimeStepSec(timeStepSec)
                .withServerConstantFailureRate(0.0d)
                .build();
        final Simulator simulator = new Simulator();
        simulator.runSimulation(config);
    }


}
