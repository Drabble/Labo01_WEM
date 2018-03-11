# WEM - Lab 1 - Crawling, indexing and searching web pages

## 1. Crawler
The crawler starts from a specific wikipedia page and crawls a certain amount of outgoing links (see crawler configs).

In this step we built the Java class "Crawler" to make the crawling and indexing of found documents in Solr.

## 2. More precise indexing
A more precise Solr index was needed to return more relevant results so we selected the principal components of web pages during the crawling. 
To achieve that we parsed the html content with jSoup and retrieved the following tags if present :

- `<title>` : The title of the page, strongest element defining what the html page speaks of
- TODO remove ? pas implémenté - `<meta name='description' content='bla bla'` : Gives an additional information. Not necessarily present
- `<h1>` : First hierarchical order in html body. Gives also a strong indication of its content
- `<b>` : Elements in bold like words or partial sentences usually have a good weight in SEO

![alt text](img/seo.png "Categories")

Additionnaly we explored for `categories` sections which also give a powerful weight to searching.

![alt text](img/categories.png "Categories")

Naturally, the choice of these elements are adapted accordingly to the domain we target. 
Here we know we are parsing wikipedia html pages, which all have the same structure.

## 3. 