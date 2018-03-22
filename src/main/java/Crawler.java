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
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
public class Crawler extends WebCrawler {

    // List of ignored file extensions
    private static final Pattern FILTERS = Pattern.compile(".*(\\.(css|js|bmp|gif|jpe?g"
                                                         + "|png|tiff?|mid|mp2|mp3|mp4"
                                                         + "|wav|avi|mov|mpeg|ram|m4v|pdf"
                                                         + "|rm|smil|wmv|swf|wma|zip|rar|gz))$");

    private final static String TARGET_DOMAIN = "https://en.wikipedia.org/wiki/";
    private final static SolrClient solr = new ConcurrentUpdateSolrClient.Builder(Config.SOLR_URL).build();

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
        config.setPolitenessDelay(500); // Be polite: Make sure that we don't send more than 1 request per 0.5 second
        config.setMaxDepthOfCrawling(2); // maximum crawl depth
        config.setMaxPagesToFetch(80); // maximum number of pages to crawl
        config.setIncludeBinaryContentInCrawling(false); // crawl also binary data like the contents of pdf
        config.setIncludeHttpsPages(true);
        config.setUserAgentString("crawler4j/WEM/2018");

        // Instantiate the controller for this crawl.
        PageFetcher pageFetcher = new PageFetcher(config);
        RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
        RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);
        CrawlController controller = new CrawlController(config, pageFetcher, robotstxtServer);

        controller.addSeed("https://en.wikipedia.org/wiki/Bishop_Rock,_Isles_of_Scilly");

        SolrClient solr = new HttpSolrClient.Builder(Config.SOLR_URL).build();
        solr.deleteByQuery( "*:*" );
        solr.commit();

        // Start the crawl. This is a blocking operation, meaning that the code
        // will reach the line after this only when crawling is finished.
        controller.start(Crawler.class, numberOfCrawlers);

        /*
        // Add the documents to solr and flush them periodically
        try {
            int len = solrInputDocuments.size();
            for (int i = 0; i < len; i++) {
                solr.add(solrInputDocuments.get(i));
                if (i % Config.PERIODICAL_FLUSH == 0)
                    solr.commit(true, true);
            }
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
        */
    }


    /**
     * Pour chaque lien rencontré par le crawler durant sa visite, il demandera à cette
     * méthode si la page doit être visitée (téléchargée). A vous de faire en sorte que les
     * ressources vraisemblablement non-supportées (non-textuelles) ou inutiles ne soient
     * pas visitées. Vous limiterez aussi la visite uniquement au domaine ciblé.
     *
     * @param referringPage
     * @param url
     * @return
     */
    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        String href = url.getURL().toLowerCase();
        // Ignore the url if it has an extension that matches our defined set of image extensions.
        if (FILTERS.matcher(href).matches()) {
            return false;
        }

        // Only accept the url if it is in the TARGET_DOMAIN domain and protocol is "https".
        //return href.startsWith("http://www.ics.uci.edu/"); // TODO remove if approved
        return href.startsWith(TARGET_DOMAIN);
    }

    /**
     * Cette méthode sera appelée par le crawler pour chaque page visitée. Vous pouvez ici
     * décider de limiter l’indexation uniquement aux formats supportés, tous les cas ne
     * pouvant pas être évités avec la méthode précédente. Cette méthode mettra en forme
     * le contenu de la page en vue de son indexation.
     *
     * @param page
     */
    @Override
    public void visit(Page page) {
        int docid = page.getWebURL().getDocid();
        String url = page.getWebURL().getURL();
        String domain = page.getWebURL().getDomain();
        String path = page.getWebURL().getPath();
        String subDomain = page.getWebURL().getSubDomain();
        String parentUrl = page.getWebURL().getParentUrl();
        String anchor = page.getWebURL().getAnchor();

        logger.debug("Docid: {}", docid);
        logger.info("URL: {}", url);
        logger.debug("Domain: '{}'", domain);
        logger.debug("Sub-domain: '{}'", subDomain);
        logger.debug("Path: '{}'", path);
        logger.debug("Parent page: {}", parentUrl);
        logger.debug("Anchor text: {}", anchor);

        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            String text = htmlParseData.getText(); // TODO necessary ? remove if approved
            String html = htmlParseData.getHtml();
            Set<WebURL> links = htmlParseData.getOutgoingUrls(); // TODO necessary to store that ?

            logger.debug("Text length: {}", text.length());
            logger.debug("Html length: {}", html.length());
            logger.debug("Number of outgoing links: {}", links.size());

            // Parse html with jsoup and retrieve the principal components of web pages if present
            Document doc = Jsoup.parse(html);

            // Retrieve title
            String title = doc.head().getElementsByTag("title").first().text();

            // Retrieve h1
            String h1 = doc.body().getElementsByTag("h1").first().text();
            // First paragraph of wikipedia pages contains a short description
            String description = doc.body().getElementById("mw-content-text").getElementsByTag("p").first().text();
            // Get all body content
            String content = doc.body().text();

            // Get categories
            List<String> categories = new ArrayList<>();
            if (doc.getElementById("mw-normal-catlinks") != null)
                categories = doc.getElementById("mw-normal-catlinks").getElementsByTag("li").eachText();

            // Write everything to Solr
            SolrInputDocument doSolrInputDocument = new SolrInputDocument();
            doSolrInputDocument.setField("id", page.hashCode());
            // doSolrInputDocument.setField("text", text); // TODO necessary ? remove if approved
            // doSolrInputDocument.setField("html", html); // TODO necessary ?
            doSolrInputDocument.setField("url", page.getWebURL().getURL());
            doSolrInputDocument.setField("title", title);
            doSolrInputDocument.setField("h1", h1);
            doSolrInputDocument.setField("description", description);
            doSolrInputDocument.setField("content", content);
            doSolrInputDocument.setField("categories", categories);
            // doSolrInputDocument.setField("numberOfLinks", links); // TODO necessary ?
            // Add the documents to solr and flush them periodically
            try {
                solr.add(doSolrInputDocument);

                // commit periodically
                if (cnt.incrementAndGet() % Config.PERIODICAL_FLUSH == 0)
                    solr.commit(true, true);
            } catch (SolrServerException | IOException e) {
                e.printStackTrace();
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
