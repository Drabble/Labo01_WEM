import java.util.regex.Pattern;

/**
 * Some configs for Crawler classes
 *
 * authors: Antoine Drabble & Sébastien Richoz
 * date: March 2018
 */
public class Config {

    // Core paths
    public final static String SOLR_URL_1 = "http://localhost:8983/solr/core1";
    public final static String SOLR_URL_2 = "http://localhost:8983/solr/core2";

    // Only accepted domain
    public final static String TARGET_DOMAIN = "https://en.wikipedia.org/wiki/";

    // commit docs when collection reaches 50 docs
    public final static int PERIODICAL_FLUSH = 50;

    // List of ignored file extensions
    public final static Pattern FILTERS = Pattern.compile(".*(\\.(css|js|bmp|gif|jpe?g"
            + "|png|tiff?|mid|mp2|mp3|mp4"
            + "|wav|avi|mov|mpeg|ram|m4v|pdf"
            + "|rm|smil|wmv|swf|wma|zip|rar|gz))$");
}
