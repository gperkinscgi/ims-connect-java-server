package com.cgi.icbc.imsconnect.pool;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interface for load balancing algorithms.
 */
public interface LoadBalancer {

    /**
     * Selects a backend for the next request.
     *
     * @return backend name or null if no backends available
     */
    String selectBackend();

    /**
     * Adds a backend to the load balancer.
     *
     * @param backendName the backend name
     */
    void addBackend(String backendName);

    /**
     * Removes a backend from the load balancer.
     *
     * @param backendName the backend name
     */
    void removeBackend(String backendName);

    /**
     * Gets the list of available backends.
     *
     * @return list of backend names
     */
    List<String> getBackends();
}

/**
 * Round-robin load balancing implementation.
 */
class RoundRobinLoadBalancer implements LoadBalancer {

    private final List<String> backends = new CopyOnWriteArrayList<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    @Override
    public String selectBackend() {
        List<String> currentBackends = backends;
        if (currentBackends.isEmpty()) {
            return null;
        }

        int index = currentIndex.getAndIncrement() % currentBackends.size();
        return currentBackends.get(index);
    }

    @Override
    public void addBackend(String backendName) {
        if (!backends.contains(backendName)) {
            backends.add(backendName);
        }
    }

    @Override
    public void removeBackend(String backendName) {
        backends.remove(backendName);
    }

    @Override
    public List<String> getBackends() {
        return List.copyOf(backends);
    }
}

/**
 * Weighted round-robin load balancing implementation.
 */
class WeightedRoundRobinLoadBalancer implements LoadBalancer {

    private final List<WeightedBackend> backends = new CopyOnWriteArrayList<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    @Override
    public String selectBackend() {
        if (backends.isEmpty()) {
            return null;
        }

        // Simple weighted selection - can be optimized
        int totalWeight = backends.stream().mapToInt(WeightedBackend::getWeight).sum();
        if (totalWeight == 0) {
            return null;
        }

        int target = currentIndex.getAndIncrement() % totalWeight;
        int currentWeight = 0;

        for (WeightedBackend backend : backends) {
            currentWeight += backend.getWeight();
            if (target < currentWeight) {
                return backend.getName();
            }
        }

        // Fallback to first backend
        return backends.get(0).getName();
    }

    @Override
    public void addBackend(String backendName) {
        addBackend(backendName, 1);
    }

    public void addBackend(String backendName, int weight) {
        removeBackend(backendName); // Remove if exists
        backends.add(new WeightedBackend(backendName, weight));
    }

    @Override
    public void removeBackend(String backendName) {
        backends.removeIf(backend -> backend.getName().equals(backendName));
    }

    @Override
    public List<String> getBackends() {
        return backends.stream().map(WeightedBackend::getName).toList();
    }

    private static class WeightedBackend {
        private final String name;
        private final int weight;

        public WeightedBackend(String name, int weight) {
            this.name = name;
            this.weight = weight;
        }

        public String getName() { return name; }
        public int getWeight() { return weight; }
    }
}