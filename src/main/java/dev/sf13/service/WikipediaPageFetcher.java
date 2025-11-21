package dev.sf13.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;

@ApplicationScoped
public class WikipediaPageFetcher {
    public Document fetch(String url, String userAgent) throws IOException {
        return Jsoup.connect(url).userAgent(userAgent).get();
    }
}
