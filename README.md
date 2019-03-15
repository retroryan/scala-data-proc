## Quick start to Using Scala with Google Cloud Dataproc

This is a quick introduction to using Scala with
[Google Cloud Dataproc](https://cloud.google.com/dataproc/)

The dataproc documentation about using Scala is out of sync and doesn't have some of the most current approaches to Scala development.

## Scala Setup with sbt

### Manual Setup of sbt

There is no need to install the Scala binaries. A scala project can be boot strapped with just sbt:

On a mac sbt can be installed with:

```
brew install sbt
```

On linux [follow these instructions] (https://www.scala-sbt.org/1.0/docs/Installing-sbt-on-Linux.html)

This repo contains a sample Scala project. If you want to create a new Scala project from scratch the easiest way is with the sbt template resolver.

```
sbt new scala/scala-seed.g8
```

### Setup IntelliJ <-- Recommended approach

An even easier approach is to use [IntelliJ IDEA](https://www.jetbrains.com/idea/)  with the Scala plugin.

## Package the code into a jar file

```
sbt package
```

## Running Cloud Dataproc with Jupyter and Zeppelin

This builds on the [dataproc getting started guide](https://cloud.google.com/dataproc/docs/guides/create-cluster)

I believe the easiest and latest way to start Cloud Dataproc with Jupyter and Zeppelin is with [Components](https://cloud.google.com/dataproc/docs/concepts/components/overview)  This approach seems to work much better than initialization actions.

1. Setup Environment Variables by setting the Google Cloud Storage name, the GCP Project name and a unique name for the data proc cluster:

```
export BUCKET_NAME= ...
export PROJECT=...
export CLUSTER=...
```

2. Create a Google Cloud Storage bucket:

```
gsutil mb -c regional -l us-central1 gs://$BUCKET_NAME
gsutil ls
gsutil ls -r gs://$BUCKET_NAME
```

3. Create a cloud dataproc cluster.

This starts a cloud dataproc cluster with a couple of changes:

- I found a cloud dataproc cluster with a master node of n1-standard-2 works best because it has more memory to run the spark shell and various notebooks.

- The network is tagged so we can create a firewall rule to allow access to the web notebooks.

- Add the additional components of Jupyter and Zeppelin

- Run the latest image - see [this page for the latest and greatest](https://cloud.google.com/dataproc/docs/concepts/versioning/dataproc-release-1.4)

```
gcloud dataproc clusters create ${CLUSTER} --bucket ${BUCKET_NAME} --subnet default --zone us-central1-a --master-machine-type n1-standard-2 --master-boot-disk-size 50 --num-workers 2 --worker-machine-type n1-standard-1 --worker-boot-disk-size 50 --image-version 1.4.0-RC12-deb9 --scopes 'https://www.googleapis.com/auth/cloud-platform' --optional-components=ZEPPELIN,ANACONDA,JUPYTER --tags hadoopaccess --project ${PROJECT}
```

4. Create a firewall rule to allow local access.  First find your IP at: [http://ip4.me/](http://ip4.me/) then create a rule using:


```
export MY_IP=
gcloud compute --project=${PROJECT} firewall-rules create allow-hadoop2 --direction=INGRESS --priority=1000 --network=default --action=ALLOW --rules=tcp:9870,tcp:8088,tcp:8080 --source-ranges=${MY_IP}/32 --target-tags=hadoopaccess
```

This can also be managed in the console under VPC network -> Firewall rules

5. Find your external IP for web notebooks:

In the list of instances the master node hosts the web notebooks. Look for the instance with a `-m` and get the external IP:

```
gcloud compute instances list
```

6. Browse to Zeppelin at EXTERNAL_IP:8080

## Creating a Zeppelin notebook that uses Spark and Google Cloud Storage

Test out Zeppelin by going to the Zeppelin Tutorial -> Basic Features (Spark).

In that notebook DO NOT be tempted to click on the Interpreter bindings. Blue means they are enabled and everything breaks if the wrong ones get disabled.  

Leave the Interpreter bindings alone and just click save.  The click run for the whole notebook.

Once you have Zeppelin running let's create a new notebook that interacts with Google Cloud Storage.

1. Copy all-shakespeare.txt into your bucket. At the command line run:

```
gsutil cp data/all-shakespeare.txt \
    gs://${BUCKET_NAME}/input/all-shakespeare.txt
```

2. Create a new Zeppelin notebook and add the following:

Be sure to replace your bucket name below:

```
val lines = sc.textFile("gs://[BUCKET_NAME]/input/")
```

3. Count the words in the notebook:

```
val words = lines.map(line => line.toLowerCase).flatMap(line => line.split("""\W+"""))
val wordCounts = words.collect {case (word) if (!word.isEmpty && (word.length > 2)) => (word.trim, 1) }.reduceByKey((n1,n2) => n1+n2)
val sortedWordCounts = wordCounts.sortBy( { case (word, count) => count}, false)
```

4. Create a data frame from the RDD:

```
case class WordCount(word: String, count: Integer)
val wordCountTable = sortedWordCounts.map {
    case (word,count) => WordCount(word,count)
}.toDF()
wordCountTable.registerTempTable("wordCountTable")
```

5. Query the data from with spark sql:

```
%sql
select *
from wordCountTable where count > 1000
```

5.  Save the word count out to your bucket from inside the notebook:

```
sortedWordCounts.saveAsTextFile("gs://[BUCKET_NAME]/output/")
```

5. Back at the command prompt look at the output:

```
gsutil ls -r gs://${BUCKET_NAME}/output
gsutil cat gs://${BUCKET_NAME}/output/part-00000
```

## Run this project in cloud Dataproc

1 - compile and package the project:

```
sbt package
```

2 - If you have output from before delete the output directory in your bucket:

```
gsutil rm -r gs://${BUCKET_NAME}/output
```

3 - Copy the jar file to your cloud storage bucket:

```
gsutil cp target/scala-2.11/scala-data-proc_2.11-0.1.0.jar \
  gs://${BUCKET_NAME}/scala-2.11/scala-data-proc_2.11-0.1.0.jar
```

4 - If you didn't already copy the input text to the bucket run this:

```
gsutil cp data/all-shakespeare.txt \
    gs://${BUCKET_NAME}/input/all-shakespeare.txt
```

5 - Run the spark job:

Note that spark might not have enough workers if a notebook is running. I had to stop the zeppelin spark interpreter to get this to run.

```
gcloud dataproc jobs submit spark \
    --properties='spark.executor.memory=2G' \
    --cluster=${CLUSTER} \
    --class examples.WordCount \
    --jars gs://${BUCKET_NAME}/scala-2.11/scala-data-proc_2.11-0.1.0.jar \
    -- gs://${BUCKET_NAME}/input/ gs://${BUCKET_NAME}/output/
```


## Start a spark shell:

1 - In the cloud console you can find the command to ssh into your master node.  The command will be approx.:

```
gcloud compute --project "${PROJECT}" ssh --zone "us-central1-a" "${CLUSTER}-m"
```

2 - run the spark shell with a fixed size of executor memory:

```
spark-shell --executor-memory 2G
```
