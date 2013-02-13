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

import org.fusesource.fabric.zookeeper.IZKClient;

import java.util.List;

public interface ZooKeeperClusterService {

    List<String> getEnsembleContainers();

    String getZooKeeperUrl();

    void createCluster(List<String> containers);

    void createCluster(List<String> containers, CreateEnsembleOptions options);

    void addToCluster(List<String> containers);

    void removeFromCluster(List<String> containers);

    void clean();

    IZKClient getZooKeeper();
}
