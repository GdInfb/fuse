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
package org.fusesource.fabric.internal.locks;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.fusesource.fabric.api.locks.LockService;

import java.util.HashMap;
import java.util.Map;

@Component(name = "org.fusesource.fabric.lock.service",
           description = "Fabric Lock Service")
@Service(LockService.class)
public class LockServiceImpl implements LockService {

    @Reference(cardinality = org.apache.felix.scr.annotations.ReferenceCardinality.MANDATORY_UNARY)
    private CuratorFramework curator;
    private final Map<String, InterProcessLock> locks = new HashMap<String, InterProcessLock>();

    @Override
    public synchronized InterProcessLock getLock(String path) {
        if (locks.containsKey(path)) {
            return locks.get(path);
        } else {
            locks.put(path, new InterProcessMutex(curator, path));
            return locks.get(path);
        }
    }
}
