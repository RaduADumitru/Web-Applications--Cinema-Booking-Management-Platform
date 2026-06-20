package com.awbd.cinema.loadbalancing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Holds a stable, human-readable identifier for this running service instance, of the form
 * {@code <app-name>@<hostname>:<port>}. Under Docker the hostname is the container id, so each
 * replica gets a distinct id — which is what makes load balancing across instances observable.
 *
 * <p>Registered as a bean by {@link LoadBalancingConfig} (not component-scanned), so it always
 * loads together with {@link ServedByResponseFilter} and never leaves that filter with an
 * unsatisfied dependency in a {@code @WebMvcTest} slice.
 */
public class InstanceInfo {

    private static final Logger log = LoggerFactory.getLogger(InstanceInfo.class);

    private final String instanceId;

    public InstanceInfo(String appName, String port) {
        this.instanceId = buildInstanceId(appName, resolveHostname(), port);
        log.info("LB instance ready: {}", instanceId);
    }

    static String buildInstanceId(String appName, String hostname, String port) {
        return appName + "@" + hostname + ":" + port;
    }

    static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String envHost = System.getenv("HOSTNAME");
            return (envHost != null && !envHost.isBlank()) ? envHost : "unknown";
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
