/*
 * Copyright 2024 Aristide Cittadino
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package it.water.gradle.plugins.workspace.pin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in catalog of standard Water Framework PINs.
 * Modules reference them via {@code standardPin 'shorthand'} in the DSL
 * instead of re-declaring all properties each time.
 */
public class StandardPins {

    private static final Map<String, OutputPinSpec> CATALOG;

    static {
        Map<String, OutputPinSpec> m = new LinkedHashMap<>();
        m.put("jdbc",                    jdbc());
        m.put("api-gateway",             apiGateway());
        m.put("service-discovery",       serviceDiscovery());
        m.put("cluster-coordinator",     clusterCoordinator());
        m.put("authentication-issuer",   authenticationIssuer());
        CATALOG = Collections.unmodifiableMap(m);
    }

    private StandardPins() {}

    /**
     * Returns a copy of the standard PIN spec for the given shorthand,
     * or {@code null} if the shorthand is not known.
     */
    public static OutputPinSpec get(String shorthand) {
        OutputPinSpec spec = CATALOG.get(shorthand);
        return spec != null ? spec.copy() : null;
    }

    // -------------------------------------------------------------------------
    // Catalog entries
    // -------------------------------------------------------------------------

    private static OutputPinSpec jdbc() {
        OutputPinSpec p = new OutputPinSpec("it.water.data.persistence");
        p.setRequired(true);
        p.addProperty("db.driver-class-name", "Driver Class Name", false, false, "", "JDBC driver class name (e.g. org.postgresql.Driver)");
        p.addProperty("db.url",       "JDBC URL",         false, false, "",     "Full JDBC URL (e.g. jdbc:postgresql://host:5432/db)");
        p.addProperty("db.host",      "Host",             true,  false, "",     "Database hostname");
        p.addProperty("db.port",      "Port",             true,  false, "5432", "Database port");
        p.addProperty("db.username",  "Username",         true,  false, "",     "Database username");
        p.addProperty("db.password",  "Password",         true,  true,  "",     "Database password");
        p.addProperty("db.pool.size", "Connection Pool Size", false, false, "10", "Connection pool size");
        return p;
    }

    private static OutputPinSpec apiGateway() {
        OutputPinSpec p = new OutputPinSpec("it.water.api-gateway");
        p.setRequired(false);
        p.addProperty("gateway.base.url",       "Base URL",          true,  false, "",      "API Gateway base URL");
        p.addProperty("gateway.admin.url",      "Admin URL",         false, false, "",      "API Gateway admin URL");
        p.addProperty("gateway.timeout.millis", "Timeout (ms)",      false, false, "30000", "Connection timeout in milliseconds");
        return p;
    }

    private static OutputPinSpec serviceDiscovery() {
        OutputPinSpec p = new OutputPinSpec("it.water.service-discovery");
        p.setRequired(false);
        p.addProperty("service.name",                  "Service Name",          true,  false, "",                 "Logical service name");
        p.addProperty("service.instance-id",           "Instance ID",           false, false, "",                 "Unique instance ID (auto UUID if empty)");
        p.addProperty("service.endpoint",              "Service Endpoint",      true,  false, "",                 "Service endpoint URL");
        p.addProperty("service.protocol",              "Protocol",              false, false, "HTTP",              "Communication protocol");
        p.addProperty("service.health-check.endpoint", "Health Check Path",     false, false, "/actuator/health", "Health check endpoint path");
        p.addProperty("service.health-check.interval", "Health Check Interval", false, false, "30",               "Health check interval in seconds");
        return p;
    }

    private static OutputPinSpec clusterCoordinator() {
        OutputPinSpec p = new OutputPinSpec("it.water.cluster.coordinator");
        p.setRequired(false);
        p.addProperty("it.water.connectors.zookeeper.url",       "ZooKeeper URL",       true,  false, "localhost:2181",          "Zookeeper ensemble connection string");
        p.addProperty("it.water.connectors.zookeeper.base.path", "ZooKeeper Base Path", false, false, "/water-framework/layers", "Zookeeper base path for Water cluster data");
        p.addProperty("water.core.cluster.node.id",              "Node ID",             true,  false, "",                        "Cluster node unique ID");
        p.addProperty("water.core.cluster.node.layer.id",        "Layer ID",            true,  false, "",                        "Cluster layer identifier");
        p.addProperty("water.core.cluster.node.host",            "Node Hostname",       false, false, "",                        "Node hostname");
        p.addProperty("water.core.cluster.node.ip",              "Node IP",             false, false, "",                        "Node IP address");
        p.addProperty("water.core.cluster.node.use-ip",          "Use IP",              false, false, "false",                   "Use IP instead of hostname for cluster registration");
        p.addProperty("water.core.cluster.mode.enabled",         "Cluster Mode",        false, false, "false",                   "Enable cluster mode");
        return p;
    }

    private static OutputPinSpec authenticationIssuer() {
        OutputPinSpec p = new OutputPinSpec("it.water.integration.authentication-issuer");
        p.setRequired(true);
        p.addProperty("water.authentication.service.issuer", "Issuer Name", true, false, "water", "Issuer name for JWT tokens");
        return p;
    }
}
