package me.juanmacias;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;

public class HelloLucene {
    private static HashSet<String> links = new HashSet<String>();

    public HelloLucene() {
        links = new HashSet<String>();
    }

    public static void getPageLinks(String URL, IndexWriter w, int num) throws IOException {
        //4. Check if you have already crawled the URLs
        //(we are intentionally not checking for duplicate content in this example)
        if (num != 0) {

            if (!links.contains(URL)) {
                try {
                    //4. (i) If not add it to the index

                    //2. Fetch the HTML code
                    org.jsoup.nodes.Document document = Jsoup.connect(URL).get();
                    //3. Parse the HTML to extract links to other URLs
                    Elements linksOnPage = document.select("a[href]");
                    addDoc(w, document.title(), URL);
                    System.out.println("Num:" + num + " Title: " + document.title() + " Url: " + URL);
                    //5. For each extracted URL... go back to Step 4.

                    for (Element page : linksOnPage) {
                        getPageLinks(page.attr("abs:href"), w, num - 1);
                    }

                } catch (IOException e) {
                    System.err.println("For '" + URL + "': " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        // 0. Specify the analyzer for tokenizing text.
        //    The same analyzer should be used for indexing and searching
        StandardAnalyzer analyzer = new StandardAnalyzer();

        // 1. create the index
        // Directory index = new RAMDirectory();
        FSDirectory index = FSDirectory.open(Paths.get("index.lucene"));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter w = new IndexWriter(index, config);
        getPageLinks("https://en.wikipedia.org/wiki/List_of_universities_in_the_United_Kingdom", w, 2);
        w.close();
        // 2. query
        String queryString = args.length > 0 ? args[0] : "University";

        // the "title" arg specifies the default field to use
        // when no field is explicitly specified in the query.
        Query query = null;
        try {
            String[] fields = {"isbn", "title"};
            query = new MultiFieldQueryParser(fields, analyzer).parse(queryString);

        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            e.printStackTrace();
        }

        // 3. search
        int hitsPerPage = 10;
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
        searcher.search(query, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;

        // 4. display results
        System.out.println("Found " + hits.length + " hits.");

        for (int i = 0; i < hits.length; ++i) {

            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            System.out.println((i + 1) + ". " + d.get("title") + "\t" + d.get("url"));
        }
        // reader can only be closed when there
        // is no need to access the documents anymore.
        reader.close();
        //testCrawl(); Single Crawl
    }

    private static void testCrawl() throws IOException {

        org.jsoup.nodes.Document doc = Jsoup.connect("https://en.wikipedia.org/wiki/University").get();
        doc.select("title").forEach(System.out::println);
        // doc.select(".mw-parser-output p a").forEach(System.out::println);
        doc.select("a").forEach(System.out::println);


    }

    private static void addDoc(IndexWriter w, String title, String url) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));

        // use a string field for isbn because we don't want it tokenized
        doc.add(new TextField("url", url, Field.Store.YES));
        w.addDocument(doc);
    }
}