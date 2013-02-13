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
package org.fusesource.fabric.camel;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fusesource.fabric.groups.ChangeListener;
import org.fusesource.fabric.groups.Group;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Creates an endpoint which uses FABRIC to map a logical name to physical endpoint names
 */
public class FabricLocatorEndpoint extends DefaultEndpoint {
    private static final transient Log LOG = LogFactory.getLog(FabricLocatorEndpoint.class);

    private final FabricComponent component;
    private final Group group;

    private LoadBalancerFactory loadBalancerFactory;
    private LoadBalancer loadBalancer;
    private final Map<String, Processor> processors = new HashMap<String, Processor>();


    public FabricLocatorEndpoint(String uri, FabricComponent component, Group group) {
        super(uri, component);
        this.component = component;
        this.group = group;
    }

    @SuppressWarnings("unchecked")
    public Producer createProducer() throws Exception {
        final FabricLocatorEndpoint endpoint = this;
        return new DefaultProducer(endpoint) {
            public void process(Exchange exchange) throws Exception {
                loadBalancer.process(exchange);
            }
        };
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from a FABRIC endpoint using just its fabric name directly, you must use fabric:name:someActualUri instead");
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public void start() throws Exception {
        super.start();
        if (loadBalancer == null) {
            loadBalancer = createLoadBalancer();
        }
        group.add(new ChangeListener() {
            public synchronized void changed() {
                //Find what has been removed.
                Set<String> removed = new LinkedHashSet<String>();

                for (Map.Entry<String, Processor> entry : processors.entrySet()) {
                    String key = entry.getKey();
                    if (!group.members().containsKey(key)) {
                        removed.add(key);
                    }
                }

                //Add existing processors
                for (Map.Entry<String,byte[]> entry : group.members().entrySet()) {
                    try {
                        String key = entry.getKey();
                        if (!processors.containsKey(key)) {
                            Processor p = getProcessor(new String(entry.getValue(), "UTF-8"));
                            processors.put(key, p);
                            loadBalancer.addProcessor(p);
                        }
                    } catch (UnsupportedEncodingException ignore) {
                    }
                }

                //Update the list by removing old and adding new.
                for (String key : removed) {
                    Processor p = processors.remove(key);
                    loadBalancer.removeProcessor(p);
                }
            }

            public void connected() {
                changed();
            }

            public void disconnected() {
                changed();
            }
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        group.close();
    }

    public Processor getProcessor(String uri) {
        final Endpoint endpoint = getCamelContext().getEndpoint(uri);
        return new Processor() {

            public void process(Exchange exchange) throws Exception {
                ProducerCache producerCache = component.getProducerCache();
                Producer producer = producerCache.acquireProducer(endpoint);
                try {
                    producer.process(exchange);
                } finally {
                    producerCache.releaseProducer(endpoint, producer);
                }
            }

            @Override
            public String toString() {
                return "Producer for " + endpoint;
            }
        };
    }

    // Properties
    //-------------------------------------------------------------------------


    public FabricComponent getComponent() {
        return component;
    }

    public LoadBalancerFactory getLoadBalancerFactory() {
        if (loadBalancerFactory == null) {
            loadBalancerFactory = component.getLoadBalancerFactory();
        }
        return loadBalancerFactory;
    }

    public void setLoadBalancerFactory(LoadBalancerFactory loadBalancerFactory) {
        this.loadBalancerFactory = loadBalancerFactory;
    }

    public LoadBalancer createLoadBalancer() {
        return getLoadBalancerFactory().createLoadBalancer();
    }
}
