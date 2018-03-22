import org.apache.http.Header;
import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.fetcher.PageFetcher;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * The crawler visits multiple web pages from a root web page (Here a wikipedia page) that we set.
 * For each page, jsoup is used to parse html content and retrieve some relevant information in order
 * to store it in our solr index.
 *
 * Code was inspired from basic examples from
 * https://github.com/yasserg/crawler4j/tree/master/crawler4j-examples/crawler4j-examples-base/src/test/java/edu/uci/ics/crawler4j/examples
 *
 * authors: Antoine Drabble & Sébastien Richoz
 * date: March 2018
 */
public class Crawler2 extends WebCrawler {

    // Client to Solr core. thread safe client as there are multiple crawlers
    private final static SolrClient solr = new ConcurrentUpdateSolrClient.Builder(Config.SOLR_URL_2).build();

    // count the number of solr document added to commit them periodically to solr client
    private AtomicInteger cnt = new AtomicInteger(0);

    /**
     * Configure and start the crawler
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // number of concurrent threads that should be initiated for crawling
        int numberOfCrawlers = Runtime.getRuntime().availableProcessors();
        CrawlConfig config = new CrawlConfig();

        config.setCrawlStorageFolder("data"); // folder where intermediate crawl data is stored.
        config.setPolitenessDelay(250); // Be polite: Make sure that we don't send more than 1 request per 0.5 second
        //config.setMaxDepthOfCrawling(2); // maximum crawl depth
        config.setMaxPagesToFetch(1000); // maximum number of pages to crawl
        config.setIncludeBinaryContentInCrawling(false); // crawl also binary data like the contents of pdf
        config.setIncludeHttpsPages(true);
        config.setUserAgentString("crawler4j/WEM/2018");

        // Instantiate the controller for this crawl.
        PageFetcher pageFetcher = new PageFetcher(config);
        RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
        RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);
        CrawlController controller = new CrawlController(config, pageFetcher, robotstxtServer);

        controller.addSeed("https://en.wikipedia.org/wiki/Bishop_Rock,_Isles_of_Scilly");

        // Delete all the data in the core
        solr.deleteByQuery( "*:*" );
        solr.commit();

        // Start the crawl. This is a blocking operation, meaning that the code
        // will reach the line after this only when crawling is finished.
        controller.start(Crawler2.class, numberOfCrawlers);

        // if any doc left
        solr.commit(true, true);
    }


    /**
     * The crawler will call this method for each link found. This method will tell it whether it must visit
     * the page or not.
     * Here we exclude every file that with an unsupported extension from the list of filters and every
     * web page that is not on the target domain (wikipedia).
     *
     * @param referringPage
     * @param url
     * @return
     */
    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        String href = url.getURL().toLowerCase();

        // Ignore the url if it has an extension that matches our defined set of image extensions.
        if (Config.FILTERS.matcher(href).matches()) {
            return false;
        }

        // Only accept the url if it is in the TARGET_DOMAIN domain and protocol is "https"
        return href.startsWith(Config.TARGET_DOMAIN);
    }

    /**
     * This method is called for each visited pages by the crawler.
     * Here we retrieve data from the HTTP response
     *
     * @param page
     */
    @Override
    public void visit(Page page) {

        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            String html = htmlParseData.getHtml();

            // Parse html with jsoup and retrieve the principal components of web pages if present
            Document doc = Jsoup.parse(html);

            // Retrieve title
            String title = doc.head().getElementsByTag("title").first().text();

            // Retrieve h1
            String h1 = doc.body().getElementsByTag("h1").first().text();

            // Retrieve the infobox
            String infobox = "";
            Element infoboxElement = doc.body().getElementsByClass("infobox").first();
            if(infoboxElement != null){
                infobox = infoboxElement.text();
            }

            // First paragraph of wikipedia pages contains a short description
            String description = "";
            Element contentElement = doc.body().getElementById("mw-content-text");
            if(contentElement != null) {
                Element descriptionElement = contentElement.getElementsByTag("p").first();
                if (descriptionElement != null) {
                    description = descriptionElement.text();
                }
            }

            // Get all body content
            String content = doc.body().text();

            // Get categories
            List<String> categories = new ArrayList<>();
            if (doc.getElementById("mw-normal-catlinks") != null)
                categories = doc.getElementById("mw-normal-catlinks").getElementsByTag("li").eachText();

            // Write everything to Solr
            SolrInputDocument doSolrInputDocument = new SolrInputDocument();
            doSolrInputDocument.setField("id", page.hashCode());
            doSolrInputDocument.setField("url", page.getWebURL().getURL());
            doSolrInputDocument.setField("title", title);
            doSolrInputDocument.setField("h1", h1);
            doSolrInputDocument.setField("infobox", infobox);
            doSolrInputDocument.setField("description", description);
            doSolrInputDocument.setField("content", content);
            doSolrInputDocument.setField("categories", categories);

            // Add the documents to solr and flush them periodically
            try {
                solr.add(doSolrInputDocument);

                // commit periodically
                if (cnt.incrementAndGet() % Config.PERIODICAL_FLUSH == 0)
                    solr.commit(true, true);
            } catch (SolrServerException | IOException e) {
                e.printStackTrace();
                return;
            }
        }

        Header[] responseHeaders = page.getFetchResponseHeaders();
        if (responseHeaders != null) {
            logger.debug("Response headers:");
            for (Header header : responseHeaders) {
                logger.debug("\t{}: {}", header.getName(), header.getValue());
            }
        }

        logger.debug("=============");
    }
}
