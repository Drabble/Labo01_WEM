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
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;
import java.util.Set;
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

    // not working if fields do not already exist in index
    //private final static SolrClient solr = new HttpSolrClient.Builder(SOLR_URL).build();

    /**
     * Configure and start the crawler
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        int numberOfCrawlers = 2; // number of concurrent threads that should be initiated for crawling
        CrawlConfig config = new CrawlConfig();
        String crawlStorageFolder = "data";

        // folder where intermediate crawl data is stored.
        config.setCrawlStorageFolder(crawlStorageFolder);

        // Be polite: Make sure that we don't send more than 1 request per 0.5 second
        config.setPolitenessDelay(500);

        // maximum crawl depth
        config.setMaxDepthOfCrawling(2);

        // maximum number of pages to crawl
        config.setMaxPagesToFetch(80);

        // crawl also binary data like the contents of pdf, or the metadata of images etc
        config.setIncludeBinaryContentInCrawling(false);

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

            // Get categories
            List<String> categories = doc.getElementById("mw-normal-catlinks").getElementsByTag("li").eachText();

            // Write everything to Solr
            SolrInputDocument doSolrInputDocument = new SolrInputDocument();
            doSolrInputDocument.addField("id", page.hashCode());
            // doSolrInputDocument.setField("text", text); // TODO necessary ? remove if approved
            // doSolrInputDocument.setField("html", html); // TODO necessary ?
            doSolrInputDocument.addField("url", page.getWebURL().getURL());
            doSolrInputDocument.addField("title", title);
            doSolrInputDocument.addField("h1", h1);
            doSolrInputDocument.addField("description", description);
            doSolrInputDocument.addField("categories", categories);
            // doSolrInputDocument.setField("numberOfLinks", links); // TODO necessary ?
            SolrClient solr = new HttpSolrClient.Builder(Config.SOLR_URL).build(); // if instantiated as static field of the class, it doesn't create new fields (throw exception "unknown field")
            try {
                solr.add(doSolrInputDocument);
                solr.commit(true, true);
            } catch (SolrServerException e) {
                e.printStackTrace();
            } catch (IOException e) {
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
