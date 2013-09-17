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
package org.fusesource.fabric.internal;

import org.apache.curator.framework.CuratorFramework;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.zookeeper.KeeperException;
import org.fusesource.fabric.api.CreateEnsembleOptions;
import org.fusesource.fabric.api.DataStore;
import org.fusesource.fabric.api.DataStoreRegistrationHandler;
import org.fusesource.fabric.api.FabricException;
import org.fusesource.fabric.api.FabricService;
import org.fusesource.fabric.api.ZooKeeperClusterBootstrap;
import org.fusesource.fabric.utils.HostUtils;
import org.fusesource.fabric.utils.OsgiUtils;
import org.fusesource.fabric.utils.SystemProperties;
import org.fusesource.fabric.zookeeper.ZkDefs;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import static org.fusesource.fabric.utils.BundleUtils.instalBundle;
import static org.fusesource.fabric.utils.BundleUtils.installOrStopBundle;
import static org.fusesource.fabric.utils.Ports.mapPortToRange;
import static org.fusesource.fabric.zookeeper.utils.ZooKeeperUtils.getStringData;
import static org.apache.felix.scr.annotations.ReferenceCardinality.MANDATORY_UNARY;

@Component(name = "org.fusesource.fabric.zookeeper.cluster.bootstrap",
           description = "Fabric ZooKeeper Cluster Bootstrap",
           immediate = true)
@Service(ZooKeeperClusterBootstrap.class)
public class ZooKeeperClusterBootstrapImpl  implements ZooKeeperClusterBootstrap {

    private static final Long FABRIC_SERVICE_TIMEOUT = 60000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperClusterBootstrapImpl.class);

    private final boolean ensembleAutoStart = Boolean.parseBoolean(System.getProperty(SystemProperties.ENSEMBLE_AUTOSTART));
    private final BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();

    @Reference(cardinality = MANDATORY_UNARY)
	private ConfigurationAdmin configurationAdmin;

    @Reference(cardinality = MANDATORY_UNARY,
            referenceInterface = DataStoreRegistrationHandler.class, bind = "bindDataStoreRegistrationHandler", unbind = "unbindDataStoreRegistrationHandler")
    private DataStoreRegistrationHandler dataStoreRegistrationHandler;

    private Map<String, String> configuration;

    @Activate
    public void init(Map<String,String> configuration) {
        this.configuration = configuration;
        if (ensembleAutoStart) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    create();
                }
            }).start();

        }
    }

    public void create() {
        org.apache.felix.utils.properties.Properties userProps = null;

        try {
            userProps = new org.apache.felix.utils.properties.Properties(new File(System.getProperty("karaf.home") + "/etc/users.properties"));
        } catch (IOException e) {
            LOGGER.warn("Failed to load users from etc/users.properties. No users will be imported.", e);
        }
        CreateEnsembleOptions createOpts = CreateEnsembleOptions.builder().fromSystemProperties().users(userProps).build();
        create(createOpts);
    }

    public void create(CreateEnsembleOptions options) {
        try {
            int minimumPort = options.getMinimumPort();
            int maximumPort = options.getMaximumPort();
            String zooKeeperServerHost = options.getBindAddress();
            int zooKeeperServerPort = options.getZooKeeperServerPort();
            int zooKeeperServerConnectionPort = options.getZooKeeperServerConnectionPort();
            int mappedPort = mapPortToRange(zooKeeperServerPort, minimumPort, maximumPort);
			String connectionUrl = getConnectionAddress() + ":" + zooKeeperServerConnectionPort;

            // Create configuration
            updateDataStoreConfig(options.getDataStoreProperties());
            createZooKeeeperServerConfig(zooKeeperServerHost, mappedPort, options);
            dataStoreRegistrationHandler.addRegistrationCallback(new DataStoreBootstrapTemplate(connectionUrl, configuration, options));

            // Create the client configuration
            createZooKeeeperConfig(connectionUrl, options);
            // Reset the autostart flag
            if (ensembleAutoStart) {
                System.setProperty(SystemProperties.ENSEMBLE_AUTOSTART, Boolean.FALSE.toString());
                File file = new File(System.getProperty("karaf.base") + "/etc/system.properties");
                org.apache.felix.utils.properties.Properties props = new org.apache.felix.utils.properties.Properties(file);
                props.put(SystemProperties.ENSEMBLE_AUTOSTART, Boolean.FALSE.toString());
                props.save();
            }
            startBundles(options);
            //Wait until Fabric Service becomes available.
            OsgiUtils.waitForSerice(FabricService.class, null, FABRIC_SERVICE_TIMEOUT );
		} catch (Exception e) {
			throw new FabricException("Unable to create zookeeper server configuration", e);
		}
	}

    public void clean() {
        try {
            Bundle bundleFabricZooKeeper = installOrStopBundle(bundleContext, "org.fusesource.fabric.fabric-zookeeper",
                    "mvn:org.fusesource.fabric/fabric-zookeeper/" + FabricConstants.FABRIC_VERSION);

            for (; ; ) {
                Configuration[] configs = configurationAdmin.listConfigurations("(|(service.factoryPid=org.fusesource.fabric.zookeeper.server)(service.pid=org.fusesource.fabric.zookeeper))");
                if (configs != null && configs.length > 0) {
                    for (Configuration config : configs) {
                        config.delete();
                    }
                    Thread.sleep(100);
                } else {
                    break;
                }
            }

            File zkDir = new File("data/zookeeper");
            if (zkDir.isDirectory()) {
                File newZkDir = new File("data/zookeeper." + System.currentTimeMillis());
                if (!zkDir.renameTo(newZkDir)) {
                    newZkDir = zkDir;
                }
                delete(newZkDir);
            }

            bundleFabricZooKeeper.start();
        } catch (Exception e) {
            throw new FabricException("Unable to delete zookeeper configuration", e);
        }
    }


    private void updateDataStoreConfig(Map<String, String> dataStoreConfiguration) throws IOException {
        boolean updated = false;
        Configuration config = configurationAdmin.getConfiguration(DataStore.DATASTORE_TYPE_PID);
        Dictionary<String, Object> properties = config.getProperties();
        for (Map.Entry<String, String> entry : dataStoreConfiguration.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!value.equals(properties.put(key, value))) {
                updated = true;
            }
        }
        if (updated) {
            //We unbind, so that we know exactly when we get the re-configured instance.
            //dataStoreRegistrationHandler.unbind();
            config.setBundleLocation(null);
            config.update(properties);
        }
    }

    /**
     * Creates ZooKeeper server configuration
     * @param serverHost
     * @param serverPort
     * @param options
     * @throws IOException
     */
    private void createZooKeeeperServerConfig(String serverHost, int serverPort, CreateEnsembleOptions options) throws IOException {
        Configuration config = configurationAdmin.createFactoryConfiguration("org.fusesource.fabric.zookeeper.server");
        Hashtable properties = new Hashtable<String, Object>();
        if (options.isAutoImportEnabled()) {
            loadPropertiesFrom(properties, options.getImportPath() + "/fabric/configs/versions/1.0/profiles/default/org.fusesource.fabric.zookeeper.server.properties");
        }
        properties.put("tickTime", "2000");
        properties.put("initLimit", "10");
        properties.put("syncLimit", "5");
        properties.put("dataDir", "data/zookeeper/0000");
        properties.put("clientPort", Integer.toString(serverPort));
        properties.put("clientPortAddress", serverHost);
        properties.put("fabric.zookeeper.pid", "org.fusesource.fabric.zookeeper.server-0000");
        config.setBundleLocation(null);
        config.update(properties);
    }

    /**
     * Creates ZooKeeper client configuration.
     * @param connectionUrl
     * @param options
     * @throws IOException
     */
    private void createZooKeeeperConfig(String connectionUrl, CreateEnsembleOptions options) throws IOException {
        Configuration config = configurationAdmin.getConfiguration("org.fusesource.fabric.zookeeper");
        Hashtable properties = new Hashtable<String, Object>();
        if (options.isAutoImportEnabled()) {
            loadPropertiesFrom(properties, options.getImportPath() + "/fabric/configs/versions/1.0/profiles/default/org.fusesource.fabric.zookeeper.properties");
        }
        properties.put("zookeeper.url", connectionUrl);
        properties.put("zookeeper.timeout", System.getProperties().containsKey("zookeeper.timeout") ? System.getProperties().getProperty("zookeeper.timeout") : "30000");
        properties.put("fabric.zookeeper.pid", "org.fusesource.fabric.zookeeper");
        properties.put("zookeeper.password", options.getZookeeperPassword());
        config.setBundleLocation(null);
        config.update(properties);
    }


    public void startBundles(CreateEnsembleOptions options) throws BundleException {
        // Install or stop the fabric-configadmin bridge
        Bundle bundleFabricAgent = instalBundle(bundleContext, "org.fusesource.fabric.fabric-agent",
                "mvn:org.fusesource.fabric/fabric-agent/" + FabricConstants.FABRIC_VERSION);
        Bundle bundleFabricConfigAdmin = instalBundle(bundleContext, "org.fusesource.fabric.fabric-configadmin",
                "mvn:org.fusesource.fabric/fabric-configadmin/" + FabricConstants.FABRIC_VERSION);
        Bundle bundleFabricCommands = instalBundle(bundleContext, "org.fusesource.fabric.fabric-commands  ",
                "mvn:org.fusesource.fabric/fabric-commands/" + FabricConstants.FABRIC_VERSION);

        bundleFabricCommands.start();
        bundleFabricConfigAdmin.start();
        //Check if the agent is configured to auto start.
        if (options.isAgentEnabled()) {
            bundleFabricAgent.start();
        }
    }


    private void loadPropertiesFrom(Hashtable hashtable, String from) {
        InputStream is = null;
        Properties properties = new Properties();
        try {
            is = new FileInputStream(from);
            properties.load(is);
            for (String key : properties.stringPropertyNames()) {
                hashtable.put(key, properties.get(key));
            }
        } catch (Exception e) {
            // Ignore
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }


    private static void delete(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                delete(child);
            }
        }
        if (dir.exists()) {
            dir.delete();
        }
    }

    private static String getConnectionAddress() throws UnknownHostException {
        String resolver = System.getProperty(ZkDefs.LOCAL_RESOLVER_PROPERTY, System.getProperty(ZkDefs.GLOBAL_RESOLVER_PROPERTY, ZkDefs.LOCAL_HOSTNAME));
        if (resolver.equals(ZkDefs.LOCAL_HOSTNAME)) {
            return HostUtils.getLocalHostName();
        } else if (resolver.equals(ZkDefs.LOCAL_IP)) {
            return HostUtils.getLocalIp();
        } else if (resolver.equals(ZkDefs.MANUAL_IP) && System.getProperty(ZkDefs.MANUAL_IP) != null) {
            return System.getProperty(ZkDefs.MANUAL_IP);
        }  else return HostUtils.getLocalHostName();
    }

    private static String toString(Properties source) throws IOException {
        StringWriter writer = new StringWriter();
        source.store(writer, null);
        return writer.toString();
    }

    public static Properties getProperties(CuratorFramework client, String file, Properties defaultValue) throws Exception {
        try {
            String v = getStringData(client, file);
            if (v != null) {
                return DataStoreHelpers.toProperties(v);
            } else {
                return defaultValue;
            }
        } catch (KeeperException.NoNodeException e) {
            return defaultValue;
        }
    }

    public ConfigurationAdmin getConfigurationAdmin() {
        return configurationAdmin;
    }

    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    public void bindDataStoreRegistrationHandler(DataStoreRegistrationHandler dataStoreRegistrationHandler) {
        this.dataStoreRegistrationHandler = dataStoreRegistrationHandler;
    }

    public void unbindDataStoreRegistrationHandler(DataStoreRegistrationHandler dataStoreRegistrationHandler) {
        this.dataStoreRegistrationHandler = null;
    }
}
