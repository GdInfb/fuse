package org.fusesource.fabric.zookeeper.internal;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.fusesource.fabric.utils.HostUtils;
import org.fusesource.fabric.zookeeper.IZKClient;
import org.fusesource.fabric.zookeeper.ZkDefs;
import org.fusesource.fabric.zookeeper.ZkPath;
import org.linkedin.zookeeper.client.LifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.fusesource.fabric.zookeeper.ZkPath.CONFIG_CONTAINER;
import static org.fusesource.fabric.zookeeper.ZkPath.CONFIG_VERSIONS_CONTAINER;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_ADDRESS;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_ALIVE;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_DOMAIN;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_DOMAINS;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_IP;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_JMX;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_LOCAL_HOSTNAME;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_LOCAL_IP;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_PORT_MAX;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_PORT_MIN;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_RESOLVER;
import static org.fusesource.fabric.zookeeper.ZkPath.CONTAINER_SSH;


public abstract class ContainerRegistration implements LifecycleListener, NotificationListener {

    private transient Logger logger = LoggerFactory.getLogger(ContainerRegistration.class);

    public static final String IP_REGEX = "([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])){3}";
    public static final String HOST_REGEX = "[a-zA-Z][a-zA-Z0-9\\-\\.]*[a-zA-Z]";
    public static final String IP_OR_HOST_REGEX = "(("+IP_REGEX+")|("+HOST_REGEX+")|0.0.0.0)";
    public static final String RMI_HOST_REGEX= "://" + IP_OR_HOST_REGEX;

    private IZKClient zooKeeper;
    private final Set<String> domains = new CopyOnWriteArraySet<String>();
    private volatile MBeanServer mbeanServer;

    /**
     * Returns the name of the container.
     * @return
     */
    public abstract String getContainerName();

    /**
     * Returns the JMX Url of the container.
     * @param name
     * @return
     */
    public abstract String getJmxUrl(String name) throws IOException;

    /**
     * Returns the SSH Url of the container.
     * @param name
     * @return
     */
    public abstract String getSshUrl(String name) throws IOException;

    public synchronized void onConnected() {
        //We get the name here as it can change.
        String name = getContainerName();
        logger.trace("onConnected");
        try {
            String nodeAlive = CONTAINER_ALIVE.getPath(name);
            Stat stat = zooKeeper.exists(nodeAlive);
            if (stat != null) {
                if (stat.getEphemeralOwner() != zooKeeper.getSessionId()) {
                    zooKeeper.delete(nodeAlive);
                    zooKeeper.createWithParents(nodeAlive, CreateMode.EPHEMERAL);
                }
            } else {
                zooKeeper.createWithParents(nodeAlive, CreateMode.EPHEMERAL);
            }

            String domainsNode = CONTAINER_DOMAINS.getPath(getContainerName());
            stat = zooKeeper.exists(domainsNode);
            if (stat != null) {
                zooKeeper.deleteWithChildren(domainsNode);
            }

            String jmxUrl = getJmxUrl(name);
            if (jmxUrl != null) {
                zooKeeper.createOrSetWithParents(CONTAINER_JMX.getPath(name), jmxUrl, CreateMode.PERSISTENT);
            }
            String sshUrl = getSshUrl(name);
            if (sshUrl != null) {
                zooKeeper.createOrSetWithParents(CONTAINER_SSH.getPath(name), sshUrl, CreateMode.PERSISTENT);
            }

            if (zooKeeper.exists(CONTAINER_RESOLVER.getPath(name)) == null) {
                zooKeeper.createOrSetWithParents(CONTAINER_RESOLVER.getPath(name), getContainerResolutionPolicy(zooKeeper, name), CreateMode.PERSISTENT);
            }
            zooKeeper.createOrSetWithParents(CONTAINER_LOCAL_HOSTNAME.getPath(name), HostUtils.getLocalHostName(), CreateMode.PERSISTENT);
            zooKeeper.createOrSetWithParents(CONTAINER_LOCAL_IP.getPath(name), HostUtils.getLocalIp(), CreateMode.PERSISTENT);
            zooKeeper.createOrSetWithParents(CONTAINER_IP.getPath(name), getContainerPointer(zooKeeper, name), CreateMode.PERSISTENT);

            //Check if there are addresses specified as system properties and use them if there is not an existing value in the registry.
            //Mostly usable for adding values when creating containers without an existing ensemble.
            for (String resolver : ZkDefs.VALID_RESOLVERS) {
                String address = System.getProperty(resolver);
                if (address != null && !address.isEmpty()) {
                    if (zooKeeper.exists(CONTAINER_ADDRESS.getPath(name, resolver)) == null) {
                        zooKeeper.createOrSetWithParents(CONTAINER_ADDRESS.getPath(name, resolver), address, CreateMode.PERSISTENT);
                    }
                }
            }

            //Set the port range values
            String minimumPort = System.getProperty(ZkDefs.MINIMUM_PORT);
            String maximumPort = System.getProperty(ZkDefs.MAXIMUM_PORT);
            if (zooKeeper.exists(CONTAINER_PORT_MIN.getPath(name)) == null) {
                zooKeeper.createOrSetWithParents(CONTAINER_PORT_MIN.getPath(name), minimumPort, CreateMode.PERSISTENT);
            }

            if (zooKeeper.exists(CONTAINER_PORT_MAX.getPath(name)) == null) {
                zooKeeper.createOrSetWithParents(CONTAINER_PORT_MAX.getPath(name), maximumPort, CreateMode.PERSISTENT);
            }

            String version = System.getProperty("fabric.version", ZkDefs.DEFAULT_VERSION);
            String profiles = System.getProperty("fabric.profiles");

            if (profiles != null) {
                String versionNode = CONFIG_CONTAINER.getPath(name);
                String profileNode = CONFIG_VERSIONS_CONTAINER.getPath(version, name);

                if (zooKeeper.exists(versionNode) == null) {
                    zooKeeper.createOrSetWithParents(versionNode, version, CreateMode.PERSISTENT);
                }
                if (zooKeeper.exists(profileNode) == null) {
                    zooKeeper.createOrSetWithParents(profileNode, profiles, CreateMode.PERSISTENT);
                }
            }
            registerDomains();
        } catch (Exception e) {
            logger.warn("Error updating Fabric Container information. This exception will be ignored.", e);
        }
    }


    /**
     * Returns the global resolution policy.
     *
     * @param zookeeper
     * @return
     * @throws InterruptedException
     * @throws org.apache.zookeeper.KeeperException
     */
    private static String getGlobalResolutionPolicy(IZKClient zookeeper) throws InterruptedException, KeeperException {
        String policy = ZkDefs.LOCAL_HOSTNAME;
        List<String> validResoverList = Arrays.asList(ZkDefs.VALID_RESOLVERS);
        if (zookeeper.exists(ZkPath.POLICIES.getPath(ZkDefs.RESOLVER)) != null) {
            policy = zookeeper.getStringData(ZkPath.POLICIES.getPath(ZkDefs.RESOLVER));
        } else if (System.getProperty(ZkDefs.GLOBAL_RESOLVER_PROPERTY) != null && validResoverList.contains(System.getProperty(ZkDefs.GLOBAL_RESOLVER_PROPERTY))) {
            policy = System.getProperty(ZkDefs.GLOBAL_RESOLVER_PROPERTY);
            zookeeper.createOrSetWithParents(ZkPath.POLICIES.getPath("resolver"), policy, CreateMode.PERSISTENT);
        }
        return policy;
    }

    /**
     * Returns the container specific resolution policy.
     *
     * @param zookeeper
     * @return
     * @throws InterruptedException
     * @throws KeeperException
     */
    private static String getContainerResolutionPolicy(IZKClient zookeeper, String container) throws InterruptedException, KeeperException {
        String policy = null;
        List<String> validResoverList = Arrays.asList(ZkDefs.VALID_RESOLVERS);
        if (zookeeper.exists(ZkPath.CONTAINER_RESOLVER.getPath(container)) != null) {
            policy = zookeeper.getStringData(ZkPath.CONTAINER_RESOLVER.getPath(container));
        } else if (System.getProperty(ZkDefs.LOCAL_RESOLVER_PROPERTY) != null && validResoverList.contains(System.getProperty(ZkDefs.LOCAL_RESOLVER_PROPERTY))) {
            policy = System.getProperty(ZkDefs.LOCAL_RESOLVER_PROPERTY);
        }

        if (policy == null) {
            policy = getGlobalResolutionPolicy(zookeeper);
        }

        if (policy != null && zookeeper.exists(ZkPath.CONTAINER_RESOLVER.getPath(container)) == null) {
            zookeeper.createOrSetWithParents(ZkPath.CONTAINER_RESOLVER.getPath(container), policy, CreateMode.PERSISTENT);
        }
        return policy;
    }

    /**
     * Returns a pointer to the container IP based on the global IP policy.
     *
     * @param zookeeper The zookeeper client to use to read global policy.
     * @param container The name of the container.
     * @return
     * @throws InterruptedException
     * @throws KeeperException
     */
    private static String getContainerPointer(IZKClient zookeeper, String container) throws InterruptedException, KeeperException {
        String pointer = "${zk:%s/%s}";
        String policy = getContainerResolutionPolicy(zookeeper, container);
        return String.format(pointer, container, policy);
    }


    private static String getExternalAddresses(String host, String port) throws UnknownHostException, SocketException {
        InetAddress ip = InetAddress.getByName(host);
        if (ip.isAnyLocalAddress()) {
            return HostUtils.getLocalHostName() + ":" + port;
        } else if (!ip.isLoopbackAddress()) {
            return ip.getHostName() + ":" + port;
        }
        return null;
    }

    public void destroy() {
        logger.trace("destroy");
        try {
            unregisterDomains();
        }  catch (Exception e) {
            logger.warn("An error occurred during disconnecting to zookeeper. This exception will be ignored.", e);
        }
    }

    public synchronized void onDisconnected() {
        logger.trace("onDisconnected");
        // noop
    }

    protected void registerDomains() throws InterruptedException, KeeperException {
        if (isConnected() && mbeanServer != null) {
            domains.addAll(Arrays.asList(mbeanServer.getDomains()));
            for (String domain : mbeanServer.getDomains()) {
                zooKeeper.createOrSetWithParents(CONTAINER_DOMAIN.getPath(getContainerName(), domain), (byte[]) null, CreateMode.PERSISTENT);
            }
        }
    }

    protected void unregisterDomains() throws InterruptedException, KeeperException {
        if (isConnected()) {
            String domainsPath = CONTAINER_DOMAINS.getPath(getContainerName());
            if (zooKeeper.exists(domainsPath) != null) {
                for (String child : zooKeeper.getChildren(domainsPath)) {
                    zooKeeper.delete(domainsPath + "/" + child);
                }
            }
        }
    }

    @Override
    public synchronized void handleNotification(Notification notif, Object o) {
        logger.trace("handleNotification[{}]", notif);

        // we may get notifications when zookeeper client is not really connected
        // handle mbeans registration and de-registration events
        if (isConnected() && mbeanServer != null && notif instanceof MBeanServerNotification) {
            MBeanServerNotification notification = (MBeanServerNotification) notif;
            String domain = notification.getMBeanName().getDomain();
            String path = CONTAINER_DOMAIN.getPath((String) o, domain);
            try {
                if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType())) {
                    if (domains.add(domain) && zooKeeper.exists(path) == null) {
                        zooKeeper.createOrSetWithParents(path, "", CreateMode.PERSISTENT);
                    }
                } else if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(notification.getType())) {
                    domains.clear();
                    domains.addAll(Arrays.asList(mbeanServer.getDomains()));
                    if (!domains.contains(domain)) {
                        // domain is no present any more
                        zooKeeper.delete(path);
                    }
                }
//            } catch (KeeperException.SessionExpiredException e) {
//                logger.debug("Session expiry detected. Handling notification once again", e);
//                handleNotification(notif, o);
            } catch (Exception e) {
                logger.warn("Exception while jmx domain synchronization from event: " + notif + ". This exception will be ignored.", e);
            }
        }
    }

    /**
     * Replaces hostname/ip occurances inside the jmx url, with the specified hostname
     * @param jmxUrl
     * @param hostName
     * @return
     */
    public static String replaceJmxHost(String jmxUrl, String hostName) {
        if (jmxUrl == null) {
            return null;
        }
        return jmxUrl.replaceAll(RMI_HOST_REGEX,  "://" + hostName);
    }

    private boolean isConnected() {
        // we are only considered connected if we have a client and its connected
        return zooKeeper != null && zooKeeper.isConnected();
    }

    public Set<String> getDomains() {
        return domains;
    }

    public IZKClient getZooKeeper() {
        return zooKeeper;
    }

    public void setZooKeeper(IZKClient zooKeeper) {
        this.zooKeeper = zooKeeper;
    }

    public MBeanServer getMbeanServer() {
        return mbeanServer;
    }

    public void setMbeanServer(MBeanServer mbeanServer) {
        this.mbeanServer = mbeanServer;
    }
}
