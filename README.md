# zookeeperdump
Utility script for import/export _ztrees_ to/from a [ZooKeeper Server](https://zookeeper.apache.org/). 

This tool uses [Yaml](http://www.yaml.org/start.html) format and is intended to help to load configurations for [Spring Cloud Config ZooKeeper](https://github.com/spring-cloud/spring-cloud-zookeeper).

### Downloading the script
```
mvn -Dartifact=net.therore.zookeeperdump:therore-zookeeperdump:1.1.0:groovy -q -Ddest=zookeeperdump.groovy dependency:get
```

### Help command
Executing _zookeeperdump.groovy_ without parameters it will print the help
```
usage: zookeeperdump.groovy [-s {ip:port}] [-x | -c] [-u scheme:id] [-a {scheme:id:perm,scheme:id:perm...}] zpath
 -a,--acls <arg>     set acls used for creation
 -c,--create         import tree in yaml format from stdin
 -s,--server <arg>   zookeeper server connection. By default localhost:2181
 -u,--auth <arg>     set session authentication
 -x,--extract        extract tree in yaml format to stdout
examples:
> zookeeperdump.groovy -x /config/application > dump.yml
> zookeeperdump.groovy -c /config/application < dump.yml
> zookeeperdump.groovy -x /config/application/scheduler/timeout
> echo 1000 | zookeeperdump.groovy -c /config/application/scheduler/timeout
> zookeeperdump.groovy -u digest:usr:pwd -a digest:usr:uPIxv8DxE/mT5RPGVrsDMJnQoTQ=:rw -c /config/application < dump.yml
> zookeeperdump.groovy -u digest:usr:pwd -a digest:usr:uPIxv8DxE/mT5RPGVrsDMJnQoTQ=:r -c /config/application < dump.yml
> zookeeperdump.groovy -u digest:super:secret  -c /config/myapplication < dump.yml
> zookeeperdump.groovy -u digest:super:secret  -x /config/myapplication > dump.yml
```

### Import Yaml into ZooKeeper
For example to import this yaml file into the path _/config/application_
###### dump.yml (example)
```
---
organizations:
  departments:
    office[0]:
      employee[0]:
        email: "tom@mail.com"
        name: "Tom"
      employee[1]:
        name: "James"
      employee[2]:
        name: "Mark"
      area: "marketing"
    office[1]:
      employee[0]:
        name: "Steve"
      employee[1]:
        name: "Richard"
      area: "distribution"
```

###### command
```
zookeeperdump.groovy -c /config/application < dump.yml
```

### Export Yaml from ZooKeeper

###### command
```
zookeeperdump.groovy -x /config/application > dump.yml
```

#### Further ZooKeper Information
[ZooKeeper Programmer's Guide](http://zookeeper.apache.org/doc/r3.5.0-alpha/zookeeperProgrammers.html)
