package com.elearning.subscription_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(CustomLoadBalancer.class);
    private static final int MAX_WEIGHT = 100;
    private static final int MIN_WEIGHT = 1;
    private static final int DEFAULT_WEIGHT = 1;

    private final ServiceInstanceListSupplier serviceInstanceListSupplier;
    private final AtomicInteger position = new AtomicInteger(0);
    
    // Performance tracking for each instance (CPU and Memory only)
    private final ConcurrentHashMap<String, InstancePerformance> performanceMetrics = new ConcurrentHashMap<>();

    public CustomLoadBalancer(ServiceInstanceListSupplier serviceInstanceListSupplier) {
        this.serviceInstanceListSupplier = serviceInstanceListSupplier;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        return serviceInstanceListSupplier.get().next().map(this::selectInstance);
    }

    /**
     * Simplified instance selection with CPU and Memory metrics only
     */
    private Response<ServiceInstance> selectInstance(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            logger.warn("No service instances available for load balancing");
            System.out.println("❌ No service instances available for load balancing");
            return new EmptyResponse();
        }

        // Filter out overloaded instances based on CPU and Memory
        List<ServiceInstance> healthyInstances = filterHealthyInstances(instances);
        
        if (healthyInstances.isEmpty()) {
            logger.warn("No healthy instances available, using all instances");
            System.out.println("⚠️ No healthy instances available, using all instances");
            healthyInstances = instances;
        }

        // Calculate adaptive weights based on CPU and Memory only
        int totalWeight = healthyInstances.stream()
                .mapToInt(this::calculateAdaptiveWeight)
                .sum();
        System.out.println("totalWeight" + totalWeight);

        if (totalWeight <= 0) {
            logger.warn("Total adaptive weight is zero, falling back to round-robin");
            System.out.println("⚠️ Total adaptive weight is zero, falling back to round-robin");
            return fallbackToRoundRobin(healthyInstances);
        }

        // Use SWRR algorithm with adaptive weights
        int pos = Math.abs(position.getAndIncrement() % totalWeight);
        int cumulativeWeight = 0;
        
        for (ServiceInstance instance : healthyInstances) {
            int weight = calculateAdaptiveWeight(instance);
            cumulativeWeight += weight;
            
            if (pos < cumulativeWeight) {
                String instanceId = getInstanceId(instance);
                int port = instance.getPort();
                int baseWeight = parseBaseWeight(instance);
                
                // Get performance metrics (from recorded data or property files)
                InstancePerformance performance = performanceMetrics.get(instanceId);
                double cpuUsage = 0.0, memoryUsage = 0.0;
                String metricsSource = "No data available";
                
                if (performance != null) {
                    cpuUsage = performance.getCpuUsage();
                    memoryUsage = performance.getMemoryUsage();
                    metricsSource = "Recorded metrics";
                } else {
                    // Try to get from property files
                    cpuUsage = parseCpuUsage(instance);
                    memoryUsage = parseMemoryUsage(instance);
                    if (cpuUsage > 0 || memoryUsage > 0) {
                        metricsSource = "Property files";
                    }
                }
                
                // Print selected instance details to console
                System.out.println("🎯 Load Balancer Selected Instance:");
                System.out.println("   📍 Host: " + instance.getHost());
                System.out.println("   🔌 Port: " + port);
                System.out.println("   ⚖️ Base Weight: " + baseWeight);
                System.out.println("   🎚️ Adaptive Weight: " + weight);
                System.out.println("   🏷️ Instance ID: " + instanceId);

                
                // Print performance metrics
                if (cpuUsage > 0 || memoryUsage > 0) {
                    System.out.println("   📊 CPU Usage: " + String.format("%.1f", cpuUsage) + "% (" + metricsSource + ")");
                    System.out.println("   💾 Memory Usage: " + String.format("%.1f", memoryUsage) + "% (" + metricsSource + ")");
                } else {
                    System.out.println("   📊 CPU Usage: No data available");
                    System.out.println("   💾 Memory Usage: No data available");
                }
                System.out.println("   ──────────────────────────────────────");
                
                logger.debug("Selected instance: {}:{} with adaptive weight: {} (base weight: {})", 
                    instance.getHost(), port, weight, baseWeight);
                return new DefaultResponse(instance);
            }
        }

        // Fallback to first healthy instance
        logger.warn("Fallback to first healthy instance");
        System.out.println("🔄 Fallback to first healthy instance");
        ServiceInstance fallbackInstance = healthyInstances.get(0);
        System.out.println("   🔌 Selected Port: " + fallbackInstance.getPort());
        System.out.println("   ──────────────────────────────────────");
        return new DefaultResponse(fallbackInstance);
    }

    /**
     * Calculate adaptive weight based on CPU and Memory only
     */
    private int calculateAdaptiveWeight(ServiceInstance instance) {
        String instanceId = getInstanceId(instance);
        InstancePerformance performance = performanceMetrics.get(instanceId);
        
        int baseWeight = parseBaseWeight(instance);
        
        // First try to get metrics from recorded performance data
        if (performance != null) {
            double cpuFactor = calculateCpuFactor(instance, performance);
            double memoryFactor = calculateMemoryFactor(instance, performance);
            double totalFactor = cpuFactor * memoryFactor;
            int adaptiveWeight = (int) (baseWeight * totalFactor);
            return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, adaptiveWeight));
        }
        
        // Fallback: Read CPU and Memory from instance metadata (property files)
        double cpuUsage = parseCpuUsage(instance);
        double memoryUsage = parseMemoryUsage(instance);
        
        if (cpuUsage > 0 || memoryUsage > 0) {
            // Create temporary performance object from metadata
            InstancePerformance metadataPerformance = new InstancePerformance();
            metadataPerformance.recordMetrics(cpuUsage, memoryUsage);
            
            double cpuFactor = calculateCpuFactor(instance, metadataPerformance);
            double memoryFactor = calculateMemoryFactor(instance, metadataPerformance);
            double totalFactor = cpuFactor * memoryFactor;
            int adaptiveWeight = (int) (baseWeight * totalFactor);
            return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, adaptiveWeight));
        }
        
        // If no performance data available, return base weight
        return baseWeight;
    }

    /**
     * Filter instances based on CPU and Memory health
     */
    private List<ServiceInstance> filterHealthyInstances(List<ServiceInstance> instances) {
        return instances.stream()
                .filter(this::isHealthy)
                .toList();
    }

    /**
     * Check if instance is healthy based on CPU and Memory only
     */
    private boolean isHealthy(ServiceInstance instance) {
        String instanceId = getInstanceId(instance);
        InstancePerformance performance = performanceMetrics.get(instanceId);
        
        double cpuUsage, memoryUsage;
        
        if (performance != null) {
            // Use recorded performance data
            cpuUsage = performance.getCpuUsage();
            memoryUsage = performance.getMemoryUsage();
        } else {
            // Fallback: Read from instance metadata
            cpuUsage = parseCpuUsage(instance);
            memoryUsage = parseMemoryUsage(instance);
        }
        
        // If no metrics available, assume healthy
        if (cpuUsage <= 0 && memoryUsage <= 0) {
            return true;
        }
        
        // Get thresholds from instance metadata
        int maxCpuUsage = parseMaxCpuUsage(instance);
        int maxMemoryUsage = parseMaxMemoryUsage(instance);
        
        // Check if instance exceeds CPU or Memory thresholds
        boolean cpuHealthy = cpuUsage <= 0 || cpuUsage < maxCpuUsage;
        boolean memoryHealthy = memoryUsage <= 0 || memoryUsage < maxMemoryUsage;
        
        return cpuHealthy && memoryHealthy;
    }

    /**
     * Calculate CPU factor (lower CPU usage = higher factor)
     */
    private double calculateCpuFactor(ServiceInstance instance, InstancePerformance performance) {
        int maxCpuUsage = parseMaxCpuUsage(instance);
        double cpuUsage = performance.getCpuUsage();
        
        if (cpuUsage >= maxCpuUsage) {
            return 0.5; // Heavy penalty for overloaded instances
        } else if (cpuUsage >= maxCpuUsage * 0.8) {
            return 0.8; // Penalty for high usage
        } else if (cpuUsage <= maxCpuUsage * 0.5) {
            return 1.2; // Boost for low usage
        } else {
            return 1.0; // Normal factor
        }
    }

    /**
     * Calculate memory factor (lower memory usage = higher factor)
     */
    private double calculateMemoryFactor(ServiceInstance instance, InstancePerformance performance) {
        int maxMemoryUsage = parseMaxMemoryUsage(instance);
        double memoryUsage = performance.getMemoryUsage();
        
        if (memoryUsage >= maxMemoryUsage) {
            return 0.5; // Heavy penalty for high memory usage
        } else if (memoryUsage >= maxMemoryUsage * 0.8) {
            return 0.8; // Penalty for high usage
        } else if (memoryUsage <= maxMemoryUsage * 0.5) {
            return 1.2; // Boost for low usage
        } else {
            return 1.0; // Normal factor
        }
    }

    /**
     * Record CPU and Memory performance for an instance
     */
    public void recordPerformance(String instanceId, double cpuUsage, double memoryUsage) {
        InstancePerformance performance = performanceMetrics.computeIfAbsent(instanceId, 
            k -> new InstancePerformance());
        performance.recordMetrics(cpuUsage, memoryUsage);
        
        logger.debug("Recorded performance for {}: CPU={}%, Memory={}%", 
            instanceId, cpuUsage, memoryUsage);
    }

    /**
     * Fallback to simple round-robin
     */
    private Response<ServiceInstance> fallbackToRoundRobin(List<ServiceInstance> instances) {
        int index = Math.abs(position.getAndIncrement() % instances.size());
        ServiceInstance selected = instances.get(index);
        logger.debug("Fallback round-robin selected: {}:{}", 
            selected.getHost(), selected.getPort());
        return new DefaultResponse(selected);
    }

    /**
     * Parse base weight from instance metadata
     */
    private int parseBaseWeight(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String weightStr = metadata.getOrDefault("weight", String.valueOf(DEFAULT_WEIGHT));
        
        try {
            int weight = Integer.parseInt(weightStr);
            return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
        } catch (NumberFormatException e) {
            logger.warn("Invalid weight format '{}' for instance {}:{}, using default {}", 
                weightStr, instance.getHost(), instance.getPort(), DEFAULT_WEIGHT);
            return DEFAULT_WEIGHT;
        }
    }

    /**
     * Parse CPU and Memory thresholds from instance metadata
     */
    private int parseMaxCpuUsage(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String cpuStr = metadata.getOrDefault("max-cpu-usage", "80");
        try {
            return Integer.parseInt(cpuStr);
        } catch (NumberFormatException e) {
            return 80; // Default threshold
        }
    }

    private int parseMaxMemoryUsage(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String memoryStr = metadata.getOrDefault("max-memory-usage", "85");
        try {
            return Integer.parseInt(memoryStr);
        } catch (NumberFormatException e) {
            return 85; // Default threshold
        }
    }

    /**
     * Parse CPU usage from instance metadata
     */
    private double parseCpuUsage(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String cpuStr = metadata.get("cpu-usage");
        try {
            return cpuStr != null ? Double.parseDouble(cpuStr) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Parse memory usage from instance metadata
     */
    private double parseMemoryUsage(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        String memoryStr = metadata.get("memory-usage");
        try {
            return memoryStr != null ? Double.parseDouble(memoryStr) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Generate unique instance ID
     */
    private String getInstanceId(ServiceInstance instance) {
        return instance.getHost() + ":" + instance.getPort();
    }

    /**
     * Simplified performance metrics for CPU and Memory only
     */
    private static class InstancePerformance {
        private volatile double cpuUsage = 0.0;
        private volatile double memoryUsage = 0.0;

        public void recordMetrics(double cpuUsage, double memoryUsage) {
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
        }

        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
    }
}

