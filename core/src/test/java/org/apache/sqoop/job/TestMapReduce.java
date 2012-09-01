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
package org.apache.sqoop.job;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import junit.framework.Assert;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.sqoop.job.etl.Context;
import org.apache.sqoop.job.etl.Extractor;
import org.apache.sqoop.job.etl.Loader;
import org.apache.sqoop.job.etl.Partition;
import org.apache.sqoop.job.etl.Partitioner;
import org.apache.sqoop.job.io.Data;
import org.apache.sqoop.job.io.DataReader;
import org.apache.sqoop.job.io.DataWriter;
import org.apache.sqoop.job.mr.SqoopInputFormat;
import org.apache.sqoop.job.mr.SqoopMapper;
import org.apache.sqoop.job.mr.SqoopNullOutputFormat;
import org.apache.sqoop.job.mr.SqoopSplit;
import org.junit.Test;

public class TestMapReduce {

  private static final int START_ID = 1;
  private static final int NUMBER_OF_IDS = 9;
  private static final int NUMBER_OF_ROWS_PER_ID = 10;

  @Test
  public void testInputFormat() throws Exception {
    Configuration conf = new Configuration();
    conf.set(JobConstants.JOB_ETL_PARTITIONER, DummyPartitioner.class.getName());
    Job job = Job.getInstance(conf);

    SqoopInputFormat inputformat = new SqoopInputFormat();
    List<InputSplit> splits = inputformat.getSplits(job);
    Assert.assertEquals(9, splits.size());

    for (int id = START_ID; id <= NUMBER_OF_IDS; id++) {
      SqoopSplit split = (SqoopSplit)splits.get(id-1);
      DummyPartition partition = (DummyPartition)split.getPartition();
      Assert.assertEquals(id, partition.getId());
    }
  }

  @Test
  public void testMapper() throws Exception {
    Configuration conf = new Configuration();
    conf.set(JobConstants.JOB_ETL_PARTITIONER, DummyPartitioner.class.getName());
    conf.set(JobConstants.JOB_ETL_EXTRACTOR, DummyExtractor.class.getName());

    Job job = Job.getInstance(conf);
    job.setInputFormatClass(SqoopInputFormat.class);
    job.setMapperClass(SqoopMapper.class);
    job.setMapOutputKeyClass(Data.class);
    job.setMapOutputValueClass(NullWritable.class);
    job.setOutputFormatClass(DummyOutputFormat.class);
    job.setOutputKeyClass(Data.class);
    job.setOutputValueClass(NullWritable.class);

    boolean success = job.waitForCompletion(true);
    Assert.assertEquals("Job failed!", true, success);
  }

  @Test
  public void testOutputFormat() throws Exception {
    Configuration conf = new Configuration();
    conf.set(JobConstants.JOB_ETL_PARTITIONER, DummyPartitioner.class.getName());
    conf.set(JobConstants.JOB_ETL_EXTRACTOR, DummyExtractor.class.getName());
    conf.set(JobConstants.JOB_ETL_LOADER, DummyLoader.class.getName());

    Job job = Job.getInstance(conf);
    job.setInputFormatClass(SqoopInputFormat.class);
    job.setMapperClass(SqoopMapper.class);
    job.setMapOutputKeyClass(Data.class);
    job.setMapOutputValueClass(NullWritable.class);
    job.setOutputFormatClass(SqoopNullOutputFormat.class);
    job.setOutputKeyClass(Data.class);
    job.setOutputValueClass(NullWritable.class);

    boolean success = job.waitForCompletion(true);
    Assert.assertEquals("Job failed!", true, success);
  }

  public static class DummyPartition extends Partition {
    private int id;

    public void setId(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    @Override
    public void readFields(DataInput in) throws IOException {
      id = in.readInt();
    }

    @Override
    public void write(DataOutput out) throws IOException {
      out.writeInt(id);
    }
  }

  public static class DummyPartitioner extends Partitioner {
    @Override
    public List<Partition> run(Context context) {
      List<Partition> partitions = new LinkedList<Partition>();
      for (int id = START_ID; id <= NUMBER_OF_IDS; id++) {
        DummyPartition partition = new DummyPartition();
        partition.setId(id);
        partitions.add(partition);
      }
      return partitions;
    }
  }

  public static class DummyExtractor extends Extractor {
    @Override
    public void run(Context context, Partition partition, DataWriter writer) {
      int id = ((DummyPartition)partition).getId();
      for (int row = 0; row < NUMBER_OF_ROWS_PER_ID; row++) {
        Object[] array = new Object[] {
          String.valueOf(id*NUMBER_OF_ROWS_PER_ID+row),
          new Integer(id*NUMBER_OF_ROWS_PER_ID+row),
          new Double(id*NUMBER_OF_ROWS_PER_ID+row)
        };
        writer.writeArrayRecord(array);
      }
    }
  }

  public static class DummyOutputFormat
      extends OutputFormat<Data, NullWritable> {
    @Override
    public void checkOutputSpecs(JobContext context) {
      // do nothing
    }

    @Override
    public RecordWriter<Data, NullWritable> getRecordWriter(
        TaskAttemptContext context) {
      return new DummyRecordWriter();
    }

    @Override
    public OutputCommitter getOutputCommitter(TaskAttemptContext context) {
      return new DummyOutputCommitter();
    }

    public static class DummyRecordWriter
        extends RecordWriter<Data, NullWritable> {
      private int index = START_ID*NUMBER_OF_ROWS_PER_ID;
      private Data data = new Data();

      @Override
      public void write(Data key, NullWritable value) {
        Object[] record = new Object[] {
          String.valueOf(index),
          new Integer(index),
          new Double(index)
        };
        data.setContent(record);
        Assert.assertEquals(data.toString(), key.toString());
        index++;
      }

      @Override
      public void close(TaskAttemptContext context) {
        // do nothing
      }
    }

    public static class DummyOutputCommitter extends OutputCommitter {
      @Override
      public void setupJob(JobContext jobContext) { }

      @Override
      public void setupTask(TaskAttemptContext taskContext) { }

      @Override
      public void commitTask(TaskAttemptContext taskContext) { }

      @Override
      public void abortTask(TaskAttemptContext taskContext) { }

      @Override
      public boolean needsTaskCommit(TaskAttemptContext taskContext) {
        return false;
      }
    }
  }

  public static class DummyLoader extends Loader {
    private int index = START_ID*NUMBER_OF_ROWS_PER_ID;
    private Data expected = new Data();
    private Data actual = new Data();

    @Override
    public void run(Context context, DataReader reader) {
      Object[] array;
      while ((array = reader.readArrayRecord()) != null) {
        actual.setContent(array);

        expected.setContent(new Object[] {
          String.valueOf(index),
          new Integer(index),
          new Double(index)});
        index++;

        Assert.assertEquals(expected.toString(), actual.toString());
      };
    }
  }

}