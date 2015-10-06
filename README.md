#HBASE and Algebird on EMR

<img src="https://bigsnarf.files.wordpress.com/2015/10/screen-shot-2015-10-04-at-5-42-19-pm.png" alt="fucking hot">

hbase-coprocessor-example
=================
This example demonstrates how to implement a group-by aggregation using HBase coprocessor and Algebird monoid. The HBase version we used here is 
0.94.27, which is exactly the same one available on AWS EMR.
Blog Post at: http://devblog.mediamath.com/cut-your-run-time-with-hbase-and-algebird

### Create a demo table in your local HBase application
* Download and unzip hbase 0.94.27

* Start hbase ```bin/start-hbase.sh``` 

* Start hbase shell
```shell
$ ./bin/hbase shell
HBase Shell; enter 'help<RETURN>' for list of supported commands.
Type "exit<RETURN>" to leave the HBase Shell
Version: 0.90.0, r1001068, Fri Sep 24 13:55:42 PDT 2010

hbase(main):001:0> 
```

* Create a table named as mobile-device
```shell
hbase(main):001:0> create 'mobile-device', { NAME => 'stats', VERSIONS => 1, TTL => 7776000 }
0 row(s) in 1.4990 seconds

hbase(main):002:0>
``` 

* List all keys in table named as mobile-device -- if loaded data correctly
```shell
hbase(main):003:0> count 'mobile-device', INTERVAL=> 1
Current count: 1, row: 1:2014-07-23:Desktop:Linux
Current count: 2, row: 1:2014-07-23:Desktop:Mac OS X
Current count: 3, row: 1:2014-07-23:Desktop:Windows
Current count: 4, row: 1:2014-07-23:Windows Phone:Windows
Current count: 5, row: 1:2014-07-24:Android Phone:Android
Current count: 6, row: 1:2014-07-24:Apple iPhone:iOS
Current count: 7, row: 1:2014-07-24:Desktop:Mac OS X
Current count: 8, row: 1:2014-07-24:Desktop:Windows
Current count: 9, row: 1:2014-07-24:Windows Phone:Windows
Current count: 10, row: 2:2014-07-24:Desktop:Chrome OS
10 row(s) in 0.0940 seconds
```

### Build and deploy the coprocessor demo code
* Compile a fat jar, and copy it to HBase classpath
```shell
$ sbt assembly
$ cp $PWD/target/scala-2.10/hbase-coprocessor-assembly-1.0.jar $HBASE_DIR/lib/
```
* Edit $HBASE_HOME/conf/hbase-site.xml to include coprocessor class in the configuration
```xml
<property>
    <name>hbase.coprocessor.region.classes</name>
    <value>GroupByMonoidSumCoprocessorEndpoint</value>
</property>
```
* Restart HBase
```shell
$ ~/hbase-0.94.27/bin/stop-hbase.sh
$ ~/hbase-0.94.27/bin/start-hbase.sh
```

### Load Data
```shell
$ sbt console
scala> import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Put

def newRecordFromLine(line: String) = {
  val parts = line.split("\t")
  new MobileDeviceRecord(parts(1), parts(0), parts(2), parts(3), parts(4).toLong, parts(5))
}

val htable = connection.getTable("mobile-device")
val lines = scala.io.Source.fromFile("data/mobile_device_samples.tsv").getLines()
val puts = lines.map(line ⇒ {
  val record = newRecordFromLine(line)
  val put = new Put(record.rowKey)
  for ((q, c, v) ← record.colVals)
    put.add(q, c, v)
  put
}).toList
```

### Run tests
```shell 
$ sbt test
```
