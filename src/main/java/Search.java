import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.MapSolrParams;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Execute a query to the Solr Client.
 * The documents are returned in console with their score.
 *
 * The configuration of Solr Client is in Config.java
 *
 * authors: Antoine Drabble & Sébastien Richoz
 * date: March 2018
 */
public class Search {

    // Args represent the query
    public static void main(String ... args) {

        // Raw string query
        String query = args.length == 0 ? "*" : String.join(" ", args);

        // Solr Client
        SolrClient solr = new HttpSolrClient.Builder(Config.SOLR_URL_2).build();

        // Build query
        final Map<String, String> queryParamMap = new HashMap<String, String>();

        // boost title and h1 more than description more than categories
        queryParamMap.put("q", String.format("(title:%s OR h1:%s)^5 (description:%s)^4 (categories:%s)^1.6 (content:%s)^1",
                query, query, query, query, query));
        queryParamMap.put("fl", "title,h1,url,description,score");
        MapSolrParams queryParams = new MapSolrParams(queryParamMap);

        // Run query and display response
        final QueryResponse response;
        try {
            response = solr.query(queryParams);
            final SolrDocumentList documents = response.getResults();

            // Get the total number of documents for informational purpose
            SolrQuery q = new SolrQuery("*:*");
            q.setRows(0);  // don't actually request any data
            long totalDocuments = solr.query(q).getResults().getNumFound();

            System.out.println("\n===========================================");
            System.out.println("QUERY: '" + query + "'");
            System.out.println("NUMBER OF DOCUMENTS FOUND: " + documents.getNumFound());
            System.out.println("TOTAL DOCUMENTS IN INDEX: " + totalDocuments);
            System.out.println("===========================================");
            for (SolrDocument document : documents) {
                System.out.println("title: " + document.get("title"));
                System.out.println("h1: " + document.get("h1"));
                System.out.println("url: " + document.get("url"));
                System.out.println("description: " + document.get("description"));
                System.out.println("score: " + document.get("score"));
                System.out.println("===========================================");
            }
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }
}
