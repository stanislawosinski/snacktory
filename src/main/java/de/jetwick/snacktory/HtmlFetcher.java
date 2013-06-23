/*
 *  Copyright 2011 Peter Karich jetwick_@_pannous_._info
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetwick.snacktory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to fetch articles.
 * This class is thread safe.
 * 
 * @author Peter Karich, jetwick_@_pannous_._info
 */
public class HtmlFetcher {

    static {
        SHelper.enableCookieMgmt();
        SHelper.enableUserAgentOverwrite();
        SHelper.enableAnySSL();
    }
    private static final Logger logger = LoggerFactory.getLogger(HtmlFetcher.class);

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader("urls.txt"));
        String line = null;
        Set<String> existing = new LinkedHashSet<String>();
        while ((line = reader.readLine()) != null) {
            int index1 = line.indexOf("\"");
            int index2 = line.indexOf("\"", index1 + 1);
            String url = line.substring(index1 + 1, index2);
            String domainStr = SHelper.extractDomain(url, true);
            String counterStr = "";
            // TODO more similarities
            if (existing.contains(domainStr))
                counterStr = "2";
            else
                existing.add(domainStr);

            String html = new HtmlFetcher().fetchAsString(url, 20000, null, IConnectionConfigurator.NOOP);
            String outFile = domainStr + counterStr + ".html";
            BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
            writer.write(html);
            writer.close();
        }
        reader.close();
    }
    private String referrer = "http://jetsli.de/crawler";
    private String userAgent = "Mozilla/5.0 (compatible; Jetslide; +" + referrer + ")";
    private String cacheControl = "max-age=0";
    private String language = "en-us";
    private String accept = "application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5";
    private String charset = "UTF-8";
    private SCache cache;
    private final int MAX_REDIRECTS = 10;
    private AtomicInteger cacheCounter = new AtomicInteger(0);
    private int maxTextLength = -1;
    private ArticleTextExtractor extractor = new ArticleTextExtractor();

    public HtmlFetcher() {
    }

    public void setExtractor(ArticleTextExtractor extractor) {
        this.extractor = extractor;
    }

    public HtmlFetcher setCache(SCache cache) {
        this.cache = cache;
        return this;
    }

    public SCache getCache() {
        return cache;
    }

    public int getCacheCounter() {
        return cacheCounter.get();
    }

    public HtmlFetcher clearCacheCounter() {
        cacheCounter.set(0);
        return this;
    }

    public HtmlFetcher setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
        return this;
    }

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setAccept(String accept) {
        this.accept = accept;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setCacheControl(String cacheControl) {
        this.cacheControl = cacheControl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getReferrer() {
        return referrer;
    }

    public HtmlFetcher setReferrer(String referrer) {
        this.referrer = referrer;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getAccept() {
        return accept;
    }

    public String getCacheControl() {
        return cacheControl;
    }

    public String getCharset() {
        return charset;
    }

    public JResult fetchAndExtract(String url, int timeout, boolean resolve) throws Exception {
        return fetchAndExtract(url, timeout, resolve, IHtmlTransformer.IDENTITY, IConnectionConfigurator.NOOP);
    }
    
    public JResult fetchAndExtract(String url, int timeout, boolean resolve, IHtmlTransformer transformer,
            IConnectionConfigurator configurator) throws Exception {
        String originalUrl = url;
        url = SHelper.removeHashbang(url);
        String gUrl = SHelper.getUrlFromUglyGoogleRedirect(url);
        if (gUrl != null)
            url = gUrl;
        else {
            gUrl = SHelper.getUrlFromUglyFacebookRedirect(url);
            if (gUrl != null)
                url = gUrl;
        }

        if (resolve) {
            // check if we can avoid resolving the URL (which hits the website!)
            JResult res = getFromCache(url, originalUrl);
            if (res != null)
                return res;

            String resUrl = getResolvedUrl(url, timeout, MAX_REDIRECTS);
            if (resUrl.isEmpty()) {
                if (logger.isDebugEnabled())
                    logger.warn("resolved url is empty. Url is: " + url);

                JResult result = new JResult();
                if (cache != null)
                    cache.put(url, result);
                return result.setUrl(url);
            }

            if (resUrl != null) {
                // this is necessary e.g. for some homebaken url resolvers which returl 
                // the resolved url relative to url!
                url = SHelper.useDomainOfFirstArg4Second(url, resUrl);
            }
        }

        // check if we have the (resolved) URL in cache
        JResult res = getFromCache(url, originalUrl);
        if (res != null)
            return res;

        JResult result = new JResult();
        // or should we use? <link rel="canonical" href="http://www.N24.de/news/newsitem_6797232.html"/>
        result.setUrl(url);
        result.setOriginalUrl(originalUrl);
        result.setDate(SHelper.estimateDate(url));

        // Immediately put the url into the cache as extracting content takes time.
        if (cache != null) {
            cache.put(originalUrl, result);
            cache.put(url, result);
        }

        String lowerUrl = url.toLowerCase();
        if (SHelper.isDoc(lowerUrl) || SHelper.isApp(lowerUrl) || SHelper.isPackage(lowerUrl)) {
            // skip
        } else if (SHelper.isVideo(lowerUrl) || SHelper.isAudio(lowerUrl)) {
            result.setVideoUrl(url);
        } else if (SHelper.isImage(lowerUrl)) {
            result.addImageUrl(url);
        } else {
            String contentAsString = "";
            try {
                contentAsString = fetchAsString(url, timeout, result, configurator);
            } catch (IOException e) {
                logger.warn("Content fetching failed, response code = " + result.getResponseCode(), e);
            }
            if (contentAsString.length() > 0) {
                final String rawHtml = transformer.transform(contentAsString, result.getEncoding());
                result.setRawHtml(rawHtml);
                extractor.extractContent(result, rawHtml);
                if (result.getFaviconUrl().isEmpty())
                    result.setFaviconUrl(SHelper.getDefaultFavicon(url));
    
                // some links are relative to root and do not include the domain of the url :/
                result.setFaviconUrl(fixUrl(url, result.getFaviconUrl()));
                result.setVideoUrl(fixUrl(url, result.getVideoUrl()));
                result.setRssUrl(fixUrl(url, result.getRssUrl()));
            }
        }
        result.setText(lessText(result.getText()));
        synchronized (result) {
            result.notifyAll();
        }
        return result;
    }

    public String lessText(String text) {
        if (text == null)
            return "";

        if (maxTextLength >= 0 && text.length() > maxTextLength)
            return text.substring(0, maxTextLength);

        return text;
    }

    private static String fixUrl(String url, String urlOrPath) {
        return SHelper.useDomainOfFirstArg4Second(url, urlOrPath);
    }

    public String fetchAsString(String urlAsString, int timeout, RequestMetadata status, IConnectionConfigurator configurator)
            throws MalformedURLException, IOException {
        return fetchAsString(urlAsString, timeout, true, status, configurator);
    }

    private static final Set<String> DATE_HEADERS = new HashSet<String>(
        Arrays.asList("Expires", "Last-Modified"));
    
    public String fetchAsString(String urlAsString, int timeout, boolean includeSomeGooseOptions, 
        RequestMetadata resultMetadata, IConnectionConfigurator configurator)
            throws MalformedURLException, IOException {
        if (logger.isDebugEnabled())
            logger.debug("FetchAsString:" + urlAsString);

        HttpURLConnection hConn = createUrlConnection(urlAsString, timeout, includeSomeGooseOptions, true);
        hConn.setInstanceFollowRedirects(true);
        configurator.configure(hConn);
        if (resultMetadata != null) {
            resultMetadata.setResponseCode(hConn.getResponseCode());
            for (String header : DATE_HEADERS)
            {
                final String value = hConn.getHeaderField(header);
                if (value != null) {
                    resultMetadata.addHeader(header, value);
                }
            }
        }
        InputStream is = hConn.getInputStream();

//            if ("gzip".equals(hConn.getContentEncoding()))
//                is = new GZIPInputStream(is);                        

        String enc = Converter.extractEncoding(hConn.getContentType());
        final Converter converter = new Converter(urlAsString);
        final String string = converter.streamToString(is, enc);
        resultMetadata.setEncoding(converter.getEncoding());
        return string;
    }

    /**
     * On some devices we have to hack:
     * http://developers.sun.com/mobility/reference/techart/design_guidelines/http_redirection.html
     * @return the resolved url if any. Or null if it couldn't resolve the url
     * (within the specified time) or the same url if response code is OK
     */
    public String getResolvedUrl(String urlAsString, int timeout, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            return urlAsString;
        }
        HttpURLConnection hConn = null;
        try {
            if (logger.isDebugEnabled())
                logger.debug("getResolvedUrl:" + urlAsString);
            hConn = createUrlConnection(urlAsString, timeout, true, false);
            // force no follow
            hConn.setInstanceFollowRedirects(false);
            // the program doesn't care what the content actually is !!
            // http://java.sun.com/developer/JDCTechTips/2003/tt0422.html
            hConn.setRequestMethod("HEAD");
            hConn.connect();
            int responseCode = hConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK)
                return urlAsString;

            String newUrl = hConn.getHeaderField("Location");
            if (responseCode / 100 == 3 && newUrl != null) {
                newUrl = newUrl.replaceAll(" ", "+");
                if (urlAsString.startsWith("http://bit.ly") || urlAsString.startsWith("http://is.gd"))
                    newUrl = encodeUriFromHeader(newUrl);
                                
                // Resolve until limit is reached
                return getResolvedUrl(newUrl, timeout, attemptsLeft - 1);
            } else
                return urlAsString;

        } catch (Exception ex) {
            logger.error("getResolvedUrl:" + urlAsString + " Error:" + ex.getMessage());
        }
        return "";
    }

    /**
     * Takes a URI that was decoded as ISO-8859-1 and applies percent-encoding
     * to non-ASCII characters. Workaround for broken origin servers that send
     * UTF-8 in the Location: header.
     */
    static String encodeUriFromHeader(String badLocation) {
        StringBuilder sb = new StringBuilder();

        for (char ch : badLocation.toCharArray()) {
            if (ch < (char) 128) {
                sb.append(ch);
            } else {
                // this is ONLY valid if the uri was decoded using ISO-8859-1
                sb.append(String.format("%%%02X", (int) ch));
            }
        }

        return sb.toString();
    }

    private HttpURLConnection createUrlConnection(String urlAsStr, int timeout,
            boolean includeSomeGooseOptions, boolean allowProxy) throws MalformedURLException, IOException {
        URL url = new URL(urlAsStr);
        //using proxy may increase latency
        HttpURLConnection hConn = (HttpURLConnection) (allowProxy ? url.openConnection()
            : url.openConnection(Proxy.NO_PROXY));
        hConn.setRequestProperty("User-Agent", userAgent);
        hConn.setRequestProperty("Accept", accept);

        if (includeSomeGooseOptions) {
            hConn.setRequestProperty("Accept-Language", language);
            hConn.setRequestProperty("content-charset", charset);
            hConn.addRequestProperty("Referer", referrer);
            // avoid the cache for testing purposes only?
            hConn.setRequestProperty("Cache-Control", cacheControl);
        }

        // On android we got timeouts because of this!!   
        // and here this also results in invalid html for e.g. http://twitpic.com/4kuem8
//        hConn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        hConn.setConnectTimeout(timeout);
        hConn.setReadTimeout(timeout);
        return hConn;
    }

    private JResult getFromCache(String url, String originalUrl) throws Exception {
        if (cache != null) {
            JResult res = cache.get(url);
            if (res != null) {
                // e.g. the cache returned a shortened url as original url now we want to store the
                // current original url! Also it can be that the cache response to url but the JResult
                // does not contain it so overwrite it:
                res.setUrl(url);
                res.setOriginalUrl(originalUrl);
                cacheCounter.addAndGet(1);
                return res;
            }
        }
        return null;
    }
}
