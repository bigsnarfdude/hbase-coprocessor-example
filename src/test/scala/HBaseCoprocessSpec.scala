/**
 * Copyright (C) 2014 MediaMath <http://www.mediamath.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 * @author daikeshi
 * @author themodernlife
 */

import com.twitter.algebird.HLL
import com.twitter.chill.algebird.AlgebirdRegistrar
import com.twitter.chill.{KryoPool, ScalaKryoInstantiator}
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.{HConnectionManager, Put}
import org.specs2.mutable._
import org.specs2.specification.{Fragments, Step}

import scala.collection.JavaConversions._

object testEnvironment {
  val conf = HBaseConfiguration.create
  conf.set("hbase.zookeeper.quorum", sys.env.getOrElse("ZOOKEEPER_QUORUM", "localhost"))
  conf.set("hbase.zookeeper.property.clientPort", sys.env.getOrElse("ZOOKEPPER_CLIENTPORT", "2181"))
  conf.setLong("hbase.rpc.timeout", 60000000L)

  lazy val connection = HConnectionManager.createConnection(conf)
  lazy val kryo = {
    val inst = () => {
      val newK = (new ScalaKryoInstantiator).newKryo()
      newK.setReferences(false) // typical in production environment (scalding, spark)
      (new AlgebirdRegistrar).apply(newK)
      newK
    }
    KryoPool.withByteArrayOutputStream(1, inst)
  }

  // this section loads a csv of test data and puts it batch into running HBASE 
  lazy val setUpTestEnv = {

    // sets up records parser into MobileDeviceRecord case class found in HBaseTable.scala
    def newRecordFromLine(line: String) = {
      val parts = line.split("\t")
      // splits on tab-delimited
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

    try {
      // loads ba
      htable.batch(puts)
    } catch {
      case e: Exception ⇒ sys.error("Error: " + e)
    }

    htable.close()
  }

  lazy val tearDownTestEnv = {
    connection.close()
  }
}

class HBaseCoprocessSpec extends Specification {
  lazy val testEnv = testEnvironment
  override def map(fs: ⇒ Fragments) = Step(testEnv.setUpTestEnv) ^ fs ^ Step(testEnv.tearDownTestEnv)

  "setUpVideoReportTest" should {
    "group on device and aggregate impressions correctly" in {
      val query = GroupByQuery("mobile-device", "1", None, None, List("device"), List("impressions"))
      val groupBySumClient = new CoprocessorClient(testEnv.conf)
      val results = groupBySumClient.groupBySum(query)
      results("Apple iPhone")("impressions") === 201L
      results("Android Phone")("impressions") === 17L
      results("Desktop")("impressions") === 13L
      results("Windows Phone")("impressions") === 2L
    }
  }

  //"setUpVideoReportTest" should {
  //  "group on device and aggregate uniques correctly" in {
  //    val query = GroupByQuery("mobile-device", "1", None, None, List("device"), List("uniques"))
  //    val groupBySumClient = new CoprocessorClient(testEnv.conf)
  //   val results = groupBySumClient.groupBySum(query)
  //    results("Apple iPhone")("uniques").asInstanceOf[HLL].estimatedSize === 198.0
  //    results("Android Phone")("uniques").asInstanceOf[HLL].estimatedSize === 10.0
  //    results("Desktop")("uniques").asInstanceOf[HLL].estimatedSize === 10.0
  //    results("Windows Phone")("uniques").asInstanceOf[HLL].estimatedSize === 2.0
  //  }
  //}
}
