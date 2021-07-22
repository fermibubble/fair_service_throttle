package io.fermibubble.fst;

import java.util.List;
import java.util.Queue;

public class SimulationConfig {
    private final List<Double> clientRequestTps;
    private final Queue<Simulator.TimeStep> serverGoodput;
    private final String outputFile;
    private final double runUntil;
    private final int buckets;
    private final FairThrottleType fairThrottleType;
    private final int timeStepSec;
    private final double serverConstantFailureRate;

    private SimulationConfig(final Builder builder) {
        this.clientRequestTps = builder.clientRequestTps;
        this.serverGoodput = builder.serverGoodput;
        this.outputFile = builder.outputFile;
        this.runUntil = builder.runUntil;
        this.buckets = builder.buckets;
        this.fairThrottleType = builder.fairThrottleType;
        this.timeStepSec = builder.timeStepSec;
        this.serverConstantFailureRate = builder.serverConstantFailureRate;
    }

    public List<Double> getClientRequestTps() {
        return clientRequestTps;
    }

    public Queue<Simulator.TimeStep> getServerGoodput() {
        return serverGoodput;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public double getRunUntil() {
        return runUntil;
    }

    public int getBuckets() {
        return buckets;
    }

    public FairThrottleType getFairThrottleType() {
        return fairThrottleType;
    }

    public int getTimeStepSec() {
        return timeStepSec;
    }

    public double getServerConstantFailureRate() {
        return serverConstantFailureRate;
    }

    public enum FairThrottleType {
        STOCHASTIC_FAIR_THROTTLE, BLOOM_FILTER_FAIR_THROTTLE
    }

    public static final class Builder {
        private List<Double> clientRequestTps;
        private Queue<Simulator.TimeStep> serverGoodput;
        private String outputFile;
        private double runUntil;
        private int buckets;
        private FairThrottleType fairThrottleType;
        private int timeStepSec;
        private double serverConstantFailureRate;

        private Builder() {
        }

        public static Builder aSimulationConfig() {
            return new Builder();
        }

        public Builder withClientRequestTps(List<Double> clientRequestTps) {
            this.clientRequestTps = clientRequestTps;
            return this;
        }

        public Builder withServerGoodput(Queue<Simulator.TimeStep> serverGoodput) {
            this.serverGoodput = serverGoodput;
            return this;
        }

        public Builder withOutputFile(String outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder withRunUntil(double runUntil) {
            this.runUntil = runUntil;
            return this;
        }

        public Builder withBuckets(int buckets) {
            this.buckets = buckets;
            return this;
        }

        public Builder withFairThrottleType(FairThrottleType fairThrottleType) {
            this.fairThrottleType = fairThrottleType;
            return this;
        }

        public Builder withTimeStepSec(int timeStepSec) {
            this.timeStepSec = timeStepSec;
            return this;
        }

        public Builder withServerConstantFailureRate(double serverConstantFailureRate) {
            this.serverConstantFailureRate = serverConstantFailureRate;
            return this;
        }

        public SimulationConfig build() {
            return new SimulationConfig(this);
        }
    }
}
