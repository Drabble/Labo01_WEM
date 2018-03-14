# WEM - Lab 1 - Crawling, indexing and searching web pages

## 1. Crawler
The crawler starts from a specific wikipedia page and crawls a certain amount 
of outgoing links (see crawler configs).

In this step we build the Java class "Crawler" which performs the crawling and indexing
of found documents in Solr. To communicate with Solr and the crawler, we use Solrj API
and to build the crawler we use crawler4j.

In Solr, the core is named "crawler" and the index built contains 80 documents :
![alt text](img/solr_core_admin.png "Crawler core admin")

## 2. More precise indexing
A more precise Solr index was needed in order to return more relevant results during
searching. 
We selected the principal components of web pages during the crawling 
by parsing their html content with [jSoup](https://jsoup.org/). The following elements
are retrieved :

![alt text](img/seo.png "SEO")

- `<title>` : The title of the page, strongest element defining in few words the topic
of the page.
- `<h1>` : First hierarchical order in html body. Gives also a strong indication of
its content. Usually similar to the title.
- First `<p>` : Contains a relevant descriptive summary of the topic.

We also explore the `categories` sections which gives a powerful weight 
for searching as its content is related to the topic.

![alt text](img/categories.png "Categories")

Naturally, the choice of these elements are specific to the domain we target. 
Here we know we are parsing wikipedia html pages, which all have the same structure
so with jsoup we use tag names and ids to identify easily the elements.

Additionally, we store the `url` as we want to be able to visit the page containing the
information and `content` which is all the text content of the `<body>`.

## 3. Searching
After indexing was successfully built, let's try the searching.
Here we perform two queries with Solr tool:

1. Default query `q(*:*)` : returns all the 80 documents

![alt text](img/default_query.png "Default query")

2. Search for `Isles of Scilly` in title `q(h1 title:Isles of Scilly)` : returns 
12 documents

![alt text](img/specific_query.png "Specific query")

Then we build this search functionality in Java class `Search.java` which 
additionally returns the score for each document. To give more weight to the elements
we previously extracted, we use specific lucene syntax :
```
q:(title:<qry> OR h1:<qry>)^5 (description:<qry>)^3 (categories:<qry>)^2 (content:<qry>)^1
```
where `<qry>` is replaced with the query made by the user. 
More power is given using the exponent. The query is passed as the arguments of the
executing class.

As an example, let's search again for `Isles of Scilly` using our little program.
It shows the 10 first documents found.

![alt text](img/specific_query1_console.png "Specific query console")

If we search for `smallest inhabited islands` it returns 5 documents where 2 are not
relevant. However the score of the first one, which is the wanted document, is much higher
than the others thanks to the weight given for title, h1 and description.

![alt text](img/specific_query2_console.png "Specific query console")

## 4 Theoretical questions
TODO

## Dependencies

- crawler4j 4.3 [https://github.com/yasserg/crawler4j](https://github.com/yasserg/crawler4j)
- solrj 7.2.1 [https://lucene.apache.org/solr/guide/7_1/using-solrj.html](https://lucene.apache.org/solr/guide/7_1/using-solrj.html)
- jsoup 1.11.2 [https://jsoup.org/](https://jsoup.org/)

The libraries are also all listed in the pom.xml.