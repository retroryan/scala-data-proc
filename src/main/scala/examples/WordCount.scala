package examples

import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

object WordCount {
  def main(args: Array[String]) {
    if (args.length != 2) {
      throw new IllegalArgumentException(
        "Exactly 2 arguments are required: <inputPath> <outputPath>")
    }

    val inputPath = args(0)
    val outputPath = args(1)

    val conf = new SparkConf().setAppName("Word Count")
    val sc = new SparkContext(conf)

    val lines = sc.textFile(inputPath)
    val words = lines
      .map(line => line.toLowerCase)
      .flatMap(line => line.split("""\W+"""))
    val wordCounts = words
      .collect { case (word) if (!word.isEmpty && (word.length > 2)) => (word.trim, 1) }
      .reduceByKey((n1, n2) => n1 + n2)

    val sortedWordCounts = wordCounts.sortBy({ case (word, count) => count }, false)

    sortedWordCounts.saveAsTextFile(outputPath)
  }
}
