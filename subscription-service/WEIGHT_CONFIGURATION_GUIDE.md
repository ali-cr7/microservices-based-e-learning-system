# Load Balancer Weight Configuration Guide

## **How Weights Work**

The load balancer reads weights from **Eureka instance metadata** that each service registers. The weight determines how much traffic each service instance receives.

### **Weight Formula:**
```
Traffic Distribution = Instance Weight / Total Weight of All Instances
```

**Example:**
- Service A: weight = 10
- Service B: weight = 20  
- Service C: weight = 30
- **Total Weight = 60**

**Traffic Distribution:**
- Service A: 10/60 = 16.7% of traffic
- Service B: 20/60 = 33.3% of traffic
- Service C: 30/60 = 50% of traffic

## **Configuration Methods**

### **Method 1: Properties File (Recommended)**

Add to each service's `application.properties`:

```properties
# Load Balancer Weight Configuration
eureka.instance.metadata-map.weight=10
```

### **Method 2: YAML Configuration**

Add to each service's `application.yml`:

```yaml
eureka:
  instance:
    metadata-map:
      weight: 10
```

### **Method 3: Environment Variables**

```bash
# Set environment variable
export EUREKA_INSTANCE_METADATA_MAP_WEIGHT=10

# Or in Docker
docker run -e EUREKA_INSTANCE_METADATA_MAP_WEIGHT=10 your-service
```

### **Method 4: Command Line Arguments**

```bash
java -jar your-service.jar --eureka.instance.metadata-map.weight=10
```

## **Current Weight Configurations in Your System**

Based on your current setup:

```properties
# subscription-service/src/main/resources/application-with-weight.properties
eureka.instance.metadata-map.weight=2

# course-service/src/main/resources/application.properties  
eureka.instance.metadata-map.weight=3
```

## **Recommended Weight Configurations**

### **For Development/Testing:**

```properties
# All services get equal weight
eureka.instance.metadata-map.weight=10
```

### **For Production (Based on Service Capacity):**

```properties
# High-capacity services get more weight
eureka.instance.metadata-map.weight=30  # course-service (handles file uploads)
eureka.instance.metadata-map.weight=20  # assessment-service (CPU intensive)
eureka.instance.metadata-map.weight=15  # subscription-service (medium load)
eureka.instance.metadata-map.weight=10  # user-service (light load)
```

### **For Load Testing:**

```properties
# Uneven distribution to test load balancing
eureka.instance.metadata-map.weight=50  # Primary instance
eureka.instance.metadata-map.weight=25  # Secondary instance
eureka.instance.metadata-map.weight=25  # Tertiary instance
```

## **Weight Configuration by Service**

### **1. User Service**
```properties
# user-service/src/main/resources/application.properties
eureka.instance.metadata-map.weight=10
```

### **2. Course Service**
```properties
# course-service/src/main/resources/application.properties
eureka.instance.metadata-map.weight=20  # Higher weight for file handling
```

### **3. Assessment Service**
```properties
# assessment-service/src/main/resources/application.properties
eureka.instance.metadata-map.weight=15  # Medium weight for calculations
```

### **4. Subscription Service**
```properties
# subscription-service/src/main/resources/application.properties
eureka.instance.metadata-map.weight=10  # Standard weight
```

### **5. API Gateway**
```properties
# api-gateway/src/main/resources/application.properties
eureka.instance.metadata-map.weight=5   # Lower weight, mainly routing
```

## **Weight Validation Rules**

The load balancer enforces these rules:

```java
// From CustomLoadBalancer.java
private static final int MAX_WEIGHT = 100;
private static final int MIN_WEIGHT = 1;
private static final int DEFAULT_WEIGHT = 1;
```

- **Minimum Weight**: 1
- **Maximum Weight**: 100
- **Default Weight**: 1 (if not specified)
- **Invalid Weight**: Falls back to default

## **Dynamic Weight Adjustment**

### **Using Advanced Load Balancer**

The `AdvancedLoadBalancer` can adjust weights based on performance:

```java
// Weights are automatically adjusted based on:
// - Error rates (>10% errors = 50% weight reduction)
// - Response times (slow responses = weight reduction)
// - Health status (unhealthy = weight reduction)
```

### **Manual Weight Updates**

You can update weights without restarting:

```properties
# Update weight and restart service
eureka.instance.metadata-map.weight=25
```

## **Testing Weight Configuration**

### **1. Check Current Weights**

Look at the Eureka dashboard or logs:

```bash
# Check Eureka dashboard at http://localhost:8761
# Look for "weight" in instance metadata
```

### **2. Monitor Traffic Distribution**

Add logging to see weight distribution:

```java
// The load balancer logs weight information:
logger.debug("Selected instance: {}:{} with weight: {}", 
    instance.getHost(), instance.getPort(), weight);
```

### **3. Load Testing**

```bash
# Test with different weight configurations
# Monitor traffic distribution in logs
# Verify that traffic is proportional to weights
```

## **Best Practices**

### **1. Weight Guidelines**
- **Start with equal weights** (10 for all services)
- **Adjust based on capacity** (CPU, memory, I/O)
- **Monitor performance** and adjust accordingly
- **Use increments of 5-10** for easier management

### **2. Weight Ranges**
- **Light Load Services**: 5-15
- **Medium Load Services**: 15-30
- **Heavy Load Services**: 30-50
- **Critical Services**: 50-100

### **3. Weight Distribution Examples**

**Equal Distribution:**
```properties
Service A: weight=10
Service B: weight=10
Service C: weight=10
# Result: 33.3% each
```

**Weighted Distribution:**
```properties
Service A: weight=20  # 40% traffic
Service B: weight=15  # 30% traffic
Service C: weight=15  # 30% traffic
```

**High Availability:**
```properties
Primary: weight=50    # 50% traffic
Secondary: weight=30  # 30% traffic
Tertiary: weight=20   # 20% traffic
```

## **Troubleshooting**

### **Common Issues:**

1. **No Weight Specified**
   ```
   Solution: Add eureka.instance.metadata-map.weight=10
   ```

2. **Invalid Weight Value**
   ```
   Error: Weight 150 exceeds maximum, using 100
   Solution: Use weight between 1-100
   ```

3. **Weight Not Applied**
   ```
   Check: Eureka registration, service restart
   Solution: Restart service after weight change
   ```

4. **Uneven Traffic Distribution**
   ```
   Check: Total weight calculation
   Solution: Verify all instance weights
   ```

## **Monitoring Weight Effectiveness**

### **1. Log Analysis**
```bash
# Look for weight selection logs
grep "Selected instance.*with weight" application.log
```

### **2. Metrics Collection**
```java
// Advanced load balancer provides metrics
// - Total requests per instance
// - Error rates per instance
// - Weight adjustments
```

### **3. Health Checks**
```java
// Monitor instance health
// Unhealthy instances get reduced weight
```

## **Summary**

**Key Points:**
1. **Weights are configured in Eureka metadata**
2. **Range: 1-100 (default: 1)**
3. **Higher weight = more traffic**
4. **Weights can be adjusted dynamically**
5. **Advanced load balancer auto-adjusts based on performance**

**Quick Start:**
```properties
# Add to each service's application.properties
eureka.instance.metadata-map.weight=10
``` 