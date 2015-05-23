#!/usr/bin/env groovy

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Import/Export utility for ZooKeeper.
 * @author <a href="mailto:alfredo.diaz@therore.net">Alfredo Diaz</a>
 * @see https://github.com/alfredodiaz/therore-zookeeperdump
 */

@Grab("org.yaml:snakeyaml:1.13")
@Grab("org.apache.curator:curator-recipes:2.7.1")
@Grab("org.slf4j:slf4j-log4j12:1.7.11")
@Grab("org.springframework:spring-context:4.1.6.RELEASE")
@Grab("org.springframework.boot:spring-boot-autoconfigure:1.2.2.RELEASE")
@Grab("com.fasterxml.jackson.core:jackson-databind:2.4.0")
@Grab("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.4.0")

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.curator.framework.AuthInfo
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Id
import org.springframework.beans.MutablePropertyValues
import org.springframework.boot.bind.RelaxedDataBinder
import org.yaml.snakeyaml.Yaml

def cli = new CliBuilder(width: 180, usage: '''
zookeeperdump.groovy [-s {ip:port}] [-x | -c] [-u scheme:id] [-a {scheme:id:perm,scheme:id:perm...}] zpath
'''.trim(), footer:'''
examples:
> zookeeperdump.groovy -x /config/application > dump.yml
> zookeeperdump.groovy -c /config/application < dump.yml
> zookeeperdump.groovy -x /config/application/scheduler/timeout
> echo 1000 | zookeeperdump.groovy -c /config/application/scheduler/timeout
> zookeeperdump.groovy -u digest:usr:pwd -a digest:usr:uPIxv8DxE/mT5RPGVrsDMJnQoTQ=:rw -c /config/application < dump.yml
> zookeeperdump.groovy -u digest:usr:pwd -a digest:usr:uPIxv8DxE/mT5RPGVrsDMJnQoTQ=:r -c /config/application < dump.yml
> zookeeperdump.groovy -u digest:super:secret  -c /config/myapplication < dump.yml
> zookeeperdump.groovy -u digest:super:secret  -x /config/myapplication > dump.yml
'''.trim())
cli.s(longOpt:'server', 'zookeeper server connection. By default localhost:2181', required:false, args:1)
cli.x(longOpt:'extract', 'extract tree in yaml format to stdout', required:false, args:0)
cli.c(longOpt:'create', 'import tree in yaml format from stdin', required:false, args:0)
cli.u(longOpt:'auth', 'set session authentication', required:false, args:1)
cli.a(longOpt:'acls', 'set acls used for creation', required:false, args:1)

OptionAccessor opt = cli.parse(args)

if( (!opt.x && !opt.c) || opt.arguments().size!=1) {
    cli.usage()
    return
}

def server = opt.s ?: "localhost:2181"
def rootZPath = opt.arguments()[0]
def auth = opt.u ? opt.u.split(",").collect{it.split(":")}.collect{new AuthInfo(it[0],it[1..-1].join(':').bytes)}:null
def acls = opt.a ? opt.a.split(",").collect{it.split(":")}
        .collect{new ACL(it[-1].chars.collect{1<<"rwcda".indexOf(it as String)}
        .inject(0){r,i->r|i},new Id(it[0],it[1..-2].join(':')))} : null

def curatorBuilder = CuratorFrameworkFactory.builder()
            .connectionTimeoutMs(1000)
            .connectString(server)
            .retryPolicy(new ExponentialBackoffRetry(1000, 5))

if (auth)
    curatorBuilder.authorization(auth)

def curator = curatorBuilder.build()
curator.start();

/// EXPORT TREE TO YAML
if (opt.x) {
    new ObjectMapper(new YAMLFactory()).writeValue(System.out,
        { zpath ->
            zpath = zpath.startsWith('/')? zpath.substring(1):zpath
            children = curator.children.forPath("/$zpath")
            if (children.empty) {
                (bytes = curator.data.forPath("/$zpath")) ? new String(bytes, "UTF-8") : ""
            } else
                children.inject([:]){ map, name -> map[name] = owner.call("$zpath/" + name); map }.sort{it.key}
        }.call(rootZPath))
}



/// IMPORT TREE FROM YAML
if (opt.c) {
    map = [:]
    yaml = new Yaml().load(System.in)
    if (yaml instanceof Map)
        new RelaxedDataBinder(map).bind(new MutablePropertyValues(yaml))
    else
        map = yaml;

    rootZPath = rootZPath.startsWith('/')? rootZPath : "/$rootZPath"

    try {curator.delete().deletingChildrenIfNeeded().forPath(rootZPath);} catch(KeeperException.NoNodeException e ){}

    rootZPath.findIndexValues(1,{it=='/'}).collect{rootZPath[0..it-1]}.each{
        builder = curator.create()
        if (acls)
            builder = builder.withACL(acls)
        try {builder.forPath(it, null)}catch(KeeperException.NodeExistsException e){}
    }

    ({ zpath, content ->
        builder = curator.create()
        if (acls)
            builder = builder.withACL(acls)
        if (content instanceof Map) {
            builder.forPath(zpath, null)
            content.sort{it.key}.each {e -> owner.call("$zpath/" + e.key, e.value)}
        } else {
            builder.forPath(zpath, String.valueOf(content).getBytes());
        }
    }.call(rootZPath, map))

}

curator.close()
