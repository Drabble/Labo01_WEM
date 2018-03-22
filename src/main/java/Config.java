/**
 * Some configs like solr url.
 *
 * authors: Antoine Drabble & Sébastien Richoz
 * date: March 2018
 */
public class Config {
    public final static String SOLR_URL = "http://localhost:8983/solr/crawler";
    public final static int PERIODICAL_FLUSH = 50; // commit docs when collection reaches 50 docs
}
