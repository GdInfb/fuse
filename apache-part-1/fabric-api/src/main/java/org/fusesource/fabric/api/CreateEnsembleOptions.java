/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.api;

import org.codehaus.jackson.annotate.JsonProperty;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CreateEnsembleOptions extends ContainerOptions {

    public static String ZOOKEEPER_SERVER_PORT = "zookeeper.server.port";
    public static String ZOOKEEPER_SERVER_CONNECTION_PORT = "zookeeper.server.connection.port";
    public static final String ROLE_DELIMITER = ",";

    public static class Builder<B extends Builder> extends ContainerOptions.Builder<B> {

        @JsonProperty
        int zooKeeperServerPort = 2181;
        @JsonProperty
        int zooKeeperServerConnectionPort = 2181;
        @JsonProperty
        String zookeeperPassword = generatePassword();
        @JsonProperty
        boolean agentEnabled = true;
        @JsonProperty
        boolean autoImportEnabled = true;
        @JsonProperty
        String importPath = System.getProperty("karaf.home", ".") + File.separatorChar + "fabric" + File.separatorChar + "import";
        @JsonProperty
        Map<String, String> users = new HashMap<String, String>();

        @Override
        public B fromSystemProperties() {
            super.fromSystemProperties();
            this.zooKeeperServerPort = Integer.parseInt(System.getProperty(ZOOKEEPER_SERVER_PORT, "2181"));
            this.zooKeeperServerConnectionPort = Integer.parseInt(System.getProperty(ZOOKEEPER_SERVER_CONNECTION_PORT, "2181"));
            return (B) this;
        }

        public B zooKeeperServerPort(int zooKeeperServerPort) {
            this.zooKeeperServerPort = zooKeeperServerPort;
            return (B) this;
        }

        public B zooKeeperServerPort(Integer zooKeeperServerPort) {
            this.zooKeeperServerPort = zooKeeperServerPort;
            return (B) this;
        }

        public B zooKeeperServerPort(Long zooKeeperServerPort) {
            this.zooKeeperServerConnectionPort = zooKeeperServerPort.intValue();
            return (B) this;
        }

        public B zooKeeperServerConnectionPort(int zooKeeperServerConnectionPort) {
            this.zooKeeperServerConnectionPort = zooKeeperServerConnectionPort;
            return (B) this;
        }

        public B zooKeeperServerConnectionPort(Integer zooKeeperServerConnectionPort) {
            this.zooKeeperServerConnectionPort = zooKeeperServerConnectionPort;
            return (B) this;
        }

        public B zooKeeperServerConnectionPort(Long zooKeeperServerConnectionPort) {
            this.zooKeeperServerConnectionPort = zooKeeperServerConnectionPort.intValue();
            return (B) this;
        }


        public B zookeeperPassword(final String zookeeperPassword) {
            this.zookeeperPassword = zookeeperPassword;
            return (B) this;
        }

        public B users(final Map<String, String> users) {
            this.users = users;
            return (B) this;
        }

        public B withUser(String user, String password, String role) {
            this.users.put(user, password + ROLE_DELIMITER + role);
            return (B) this;
        }


        public B agentEnabled(boolean agentEnabled) {
            this.agentEnabled = agentEnabled;
            return (B) this;
        }

        public B agentEnabled(Boolean agentEnabled) {
            this.agentEnabled = agentEnabled;
            return (B) this;
        }

        public B autoImportEnabled(boolean autoImportEnabled) {
            this.autoImportEnabled = autoImportEnabled;
            return (B) this;
        }

        public B autoImportEnabled(Boolean autoImportEnabled) {
            this.autoImportEnabled = autoImportEnabled;
            return (B) this;
        }


        public B importPath(final String importPath) {
            this.importPath = importPath;
            return (B) this;
        }

        public void setZooKeeperServerPort(int zooKeeperServerPort) {
            this.zooKeeperServerPort = zooKeeperServerPort;
        }

        public void setZookeeperPassword(String zookeeperPassword) {
            this.zookeeperPassword = zookeeperPassword;
        }

        public void setAgentEnabled(boolean agentEnabled) {
            this.agentEnabled = agentEnabled;
        }

        public void setAutoImportEnabled(boolean autoImportEnabled) {
            this.autoImportEnabled = autoImportEnabled;
        }

        public void setImportPath(String importPath) {
            this.importPath = importPath;
        }

        public void setUsers(Map<String, String> users) {
            this.users = users;
        }

        public int getZooKeeperServerPort() {
            return zooKeeperServerPort;
        }

        public int getZooKeeperServerConnectionPort() {
            return zooKeeperServerConnectionPort;
        }

        public String getZookeeperPassword() {
            return zookeeperPassword;
        }

        public boolean isAgentEnabled() {
            return agentEnabled;
        }

        public boolean isAutoImportEnabled() {
            return autoImportEnabled;
        }

        public String getImportPath() {
            return importPath;
        }

        public Map<String, String> getUsers() {
            return users;
        }

        /**
         * Generate a random String that can be used as a Zookeeper password.
         *
         * @return
         */
        private static String generatePassword() {
            StringBuilder password = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                long l = Math.round(Math.floor(Math.random() * (26 * 2 + 10)));
                if (l < 10) {
                    password.append((char) ('0' + l));
                } else if (l < 36) {
                    password.append((char) ('A' + l - 10));
                } else {
                    password.append((char) ('a' + l - 36));
                }
            }
            return password.toString();
        }


        public CreateEnsembleOptions build() {
            return new CreateEnsembleOptions(bindAddress, resolver, globalResolver, manualIp, minimumPort, maximumPort, profiles, version, dataStoreProperties, zooKeeperServerPort, zooKeeperServerConnectionPort, zookeeperPassword, agentEnabled, autoImportEnabled, importPath, users);
        }
    }

    @JsonProperty
    final int zooKeeperServerPort;
    @JsonProperty
    final int zooKeeperServerConnectionPort;
    @JsonProperty
    final String zookeeperPassword;
    @JsonProperty
    final boolean agentEnabled;
    @JsonProperty
    final boolean autoImportEnabled;
    @JsonProperty
    final String importPath;
    @JsonProperty
    final Map<String, String> users;

    public static Builder<? extends Builder> builder() {
        return new Builder<Builder>();
    }

    CreateEnsembleOptions(String bindAddress, String resolver, String globalResolver, String manualIp, int minimumPort, int maximumPort, Set<String> profiles, String version, Map<String, String> dataStoreProperties, int zooKeeperServerPort, int zooKeeperServerConnectionPort, String zookeeperPassword, boolean agentEnabled, boolean autoImportEnabled, String importPath, Map<String, String> users) {
        super(bindAddress, resolver, globalResolver, manualIp, minimumPort, maximumPort, profiles, version, dataStoreProperties);
        this.zooKeeperServerPort = zooKeeperServerPort;
        this.zooKeeperServerConnectionPort = zooKeeperServerConnectionPort;
        this.zookeeperPassword = zookeeperPassword;
        this.agentEnabled = agentEnabled;
        this.autoImportEnabled = autoImportEnabled;
        this.importPath = importPath;
        this.users = users;
    }

    public int getZooKeeperServerPort() {
        return zooKeeperServerPort;
    }

    public int getZooKeeperServerConnectionPort() {
        return zooKeeperServerConnectionPort;
    }

    public String getZookeeperPassword() {
        return zookeeperPassword;
    }

    public boolean isAgentEnabled() {
        return agentEnabled;
    }

    public boolean isAutoImportEnabled() {
        return autoImportEnabled;
    }

    public String getImportPath() {
        return importPath;
    }

    public Map<String, String> getUsers() {
        return users;
    }

    @Override
    public String toString() {
        return super.toString() + " CreateEnsembleOptions{" +
                "zooKeeperServerPort=" + zooKeeperServerPort +
                ", zookeeperPassword='" + zookeeperPassword + '\'' +
                ", agentEnabled=" + agentEnabled +
                ", autoImportEnabled=" + autoImportEnabled +
                ", importPath='" + importPath + '\'' +
                ", users=" + users +
                '}';
    }
}
