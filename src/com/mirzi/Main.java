package com.mirzi;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

public class Main {

    private final static String VERSION = "0.5";
    private final static String[] AVAILABLE_TYPES = { "skin", "plugin", "visualization" };
    private final static String[] AVAILABLE_WEBSITES = { "winampheritage", "wincustomize", "winampskins" };

    // default values
    private static int requestDelay = 500;
    private static int timeout = 20000;
    private static String dest = "./downloads/";
    private static String type = "skin";

    // json file where we store scraped skin info
    private static JSONArray skinJSON;

    // dirty fix
    private static String currentCategory, currentSkinType, websiteToScrape;

    public static void main(String[] args){
        websiteToScrape = null;

        // todo: arguments (category to scrape, page to scrape)
        // parse arguments
        parseArguments(args);

        if (websiteToScrape == null){
            System.err.println("Website to scrape is not defined. Use -h for a brief explanation.");
            System.exit(1);
        }

        System.out.printf(
                "Winamp skin scraper version %s\nWebsite:       %s\nAdd-on type:   %s\nRequest delay: %d\nTimeout:       %d\nOutput folder: %s\n",
                VERSION,
                websiteToScrape,
                type,
                requestDelay,
                timeout,
                dest);
        System.out.println("------------------------------------------------------------------------");

        skinJSON = new JSONArray();

        try{
            // java only allows constants in switch statements which is very cool :-)
            if (websiteToScrape.equals(AVAILABLE_WEBSITES[0])){
                scrapeWinampHeritage();
            } else if (websiteToScrape.equals(AVAILABLE_WEBSITES[1])) {
                scrapeWinCustomize();
            } else if (websiteToScrape.equals(AVAILABLE_WEBSITES[2])){
                scrapeWinampSkinsDotInfo();
            }
            else {
                // how
                System.err.println("Have a thumbs up from me for managing to break this program in the most spectacular way.");
                System.exit(1);
            }

        } catch (Exception e){
            System.err.println("Exception thrown");
            e.printStackTrace();
        }

        saveSkinInfo();
        System.out.printf("Scraping %s finished.", websiteToScrape);
    }

    private static void parseArguments(String[] args){
        try{
            for (int i = 0; i < args.length; i++) {
                switch (args[i]){
                    case "-h":
                        printHelp();
                        System.exit(0);
                    case "-o":
                        dest = args[i+1];
                        i++;
                        break;
                    case "-d":
                        requestDelay = Integer.parseInt(args[i+1]);
                        i++;
                        break;
                    case "-i":
                        timeout = Integer.parseInt(args[i+1]);
                        i++;
                        break;
                    case "-t":
                        for (int j = 0; j < AVAILABLE_TYPES.length; j++){
                            if (AVAILABLE_TYPES[j].equals(args[i + 1])) break;
                            if (j == AVAILABLE_TYPES.length - 1){
                                System.err.printf("Invalid type %s. Use -h for a brief explanation.\n",args[j+1]);
                                System.exit(1);
                            }
                        }
                        type = args[i+1];
                        i++;
                        break;
                    case "-w":
                        for (int j = 0; j < AVAILABLE_WEBSITES.length; j++){
                            if (AVAILABLE_WEBSITES[j].equals(args[i + 1])) break;
                            if (j == AVAILABLE_WEBSITES.length - 1){
                                System.err.printf("Invalid website %s. Use -h for a brief explanation.\n",args[j+1]);
                                System.exit(1);
                            }
                        }
                        websiteToScrape = args[i+1];
                        i++;
                        break;

                    default:
                        System.err.printf("Invalid argument %s. Use -h for a brief explanation.\n",args[i]);
                        System.exit(1);
                }
            }
        }catch (Exception e){
            System.err.println("Invalid arguments. Use -h for a brief explanation.");
            System.exit(1);
        }
    }

    public static String getDestinationFolder(String filename){
        try{
            String filetype = filename.substring(filename.length()-3);

            if (type.equals("skin")){
                switch (filetype){
                    case "wal": currentSkinType = "modern"; return dest+type+"/modern/";
                    case "wsz": currentSkinType = "classic"; return dest+type+"/classic/";
                    default: currentSkinType = "unknown"; return dest+type+"/unknown/";
                }
            }else{
                return dest+type+"/"+currentCategory+"/";
            }
        }catch (Exception e){
            // i have no idea how could one cause this to throw an exception but let's keep it anyway
            return dest+"unknown/";
        }
    }

    public static String getFileNameFromUrl(String url){
        String[] temp = url.split("/");
        return temp[temp.length-1];
    }

    private static void saveSkinInfo(){
        try{
            System.out.println("Saving skin info json file");
            FileWriter fw = new FileWriter(dest+type+"info.json");
            fw.write(skinJSON.toString());
            fw.flush();
            fw.close();
        } catch (Exception e){
            System.err.println("An error has occurred while writing skin info file.");
            e.printStackTrace();
        }
    }

    private static void downloadSkin(String urlstr, String filename, String cookie) throws Exception {
        if (requestDelay > 0){
            System.out.printf("Waiting %dms before sending request...", requestDelay);
            Thread.sleep(requestDelay);
        }

        // determine type of skin
        String destination = getDestinationFolder(filename) + filename;

        System.out.printf("\rDownloading %s to %s", urlstr, destination);

        // and we're off
        URL url = new URL(urlstr);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        if (cookie != null) conn.setRequestProperty("cookie", cookie);
        ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());

        try {
            File file = new File(destination);
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileOutputStream os = new FileOutputStream(file, false);
            os.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            os.close();
        }catch (Exception ex){
            System.err.println("Exception caught, your destination may be invalid.");
            ex.printStackTrace();
        }

        System.out.printf("\rSkin saved to %s\n", destination);
    }

    private static void downloadSkin(String urlstr, String filename) throws Exception{
        downloadSkin(urlstr, filename, null);
    }

    private static void downloadSkinWithRedirect(String urlstr) throws Exception {
        URL url = new URL(urlstr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);

        conn.setReadTimeout(timeout);
        conn.setConnectTimeout(timeout);

        if (conn.getResponseCode() == 302){
            String downloadURL = conn.getHeaderField("Location");
            if (!downloadURL.startsWith("https:")) downloadURL = "https:" + downloadURL;
            String[] temp = downloadURL.split("/");
            System.out.println("Download URL: "+downloadURL);
            downloadSkin(downloadURL, temp[temp.length-1], conn.getHeaderField("Set-Cookie"));
        }else {
            System.err.println("Server responded with code " + conn.getResponseCode());
        }
    }

    private static void downloadImage(String urlstr, String filename, String cookie) throws Exception{
        System.out.printf("Downloading image %s", urlstr);
        String destination = type.equals("skin")
                ? dest+"images/"+type+"/"+currentSkinType+"/"+filename
                : dest+"images/"+type+"/"+currentCategory+"/"+filename;

        URL url = new URL(urlstr);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        if (cookie != null) conn.setRequestProperty("cookie", cookie);
        ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());

        try {
            File file = new File(destination);
            file.getParentFile().mkdirs();
            file.createNewFile();
            FileOutputStream os = new FileOutputStream(file, false);
            os.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            os.close();
        }catch (Exception ex){
            System.err.println("Exception caught, your destination may be invalid.");
            ex.printStackTrace();
        }

        System.out.printf("\rImage file saved to %s\n\n", destination);
    }

    private static void downloadImage(String urlstr, String filename) throws Exception{
        downloadImage(urlstr, filename, null);
    }

    private static void scrapeWinampHeritage() throws Exception {
        String url = "https://winampheritage.com";
        System.out.printf("Scraping %s\n\n", url);

        // get categories from main website
        Elements categories = Jsoup.connect(url + "/" + type + "s").get()
                .getElementsByClass("colorul inlineul")
                .first()
                .getElementsByTag("a");

        // map to store category names and destination urls
        LinkedHashMap<String, String> destinations = new LinkedHashMap<>();
        if (!categories.isEmpty()){
            System.out.println("Found categories: ");
            for (Element e : categories){
                String categoryName = e.text();
                String destinationUrl = url + e.attr("href");
                destinations.put(categoryName, destinationUrl);
                System.out.printf("%-20s (%s)\n", categoryName, destinationUrl);
            }
        }else{
            throw new Exception("Cannot find categories");
        }

        // iterate over categories
        for (Map.Entry<String,String> destination : destinations.entrySet()){
            currentCategory = destination.getKey();
            boolean pageEmpty = false;
            int page = 0;

            while (!pageEmpty) {
                pageEmpty = true;

                // get page for category
                System.out.printf("\nCategory: %s, page %d\n\n", destination.getKey(), page);
                Elements skins = Jsoup.connect(destination.getValue() + "/page-" + page).get()
                        .getElementsByAttribute("title");

                // iterate over skins on current page
                for (Element e : skins) {
                    String skinName = e.text();
                    String skinTitle = e.attr("title");
                    String skinUrl = url + e.attr("href");

                    if (skinName.isEmpty() || skinTitle.isEmpty() || skinUrl.isEmpty())
                        continue;      // ignore this tag if it's not a skin

                    pageEmpty = false;

                    // parse download page and get download link
                    try {
                        // get onclick attribute
                        Document downloadPage = Jsoup.connect(skinUrl).get();
                        String skinDownloadUrl = downloadPage
                                .getElementsByClass("downloadbutton")
                                .first()
                                .attr("onclick");

                        // cut out the download url
                        skinDownloadUrl = skinDownloadUrl.split(",")[1];
                        skinDownloadUrl = url + skinDownloadUrl.substring(1, skinDownloadUrl.length() - 1);

                        // get description and author
                        String skinDescription = downloadPage.getElementsByTag("p").first().text();
                        String[] skinDateAuthor = downloadPage.select("td[style='width:50%;']").text().split(" by ");
                        String skinDownloads = downloadPage.select("td[style='text-align:center;width:25%;']").text().split(" ")[0];

                        // print skin info
                        System.out.printf("Name:         %s\nAuthor:       %s\nDownload URL: %s\n",
                                skinName,
                                skinDateAuthor[1],
                                skinDownloadUrl);

                        // download skin then save info to json array
                        downloadSkin(skinDownloadUrl, getFileNameFromUrl(skinDownloadUrl), "downloadsite=winampheritage");
                        JSONObject obj = new JSONObject();
                        obj.put("name", skinName);
                        obj.put("title", skinTitle);
                        obj.put("description", skinDescription);
                        obj.put("url", skinUrl);
                        obj.put("downloadurl", skinDownloadUrl);
                        obj.put("author", skinDateAuthor[1]);
                        obj.put("date", skinDateAuthor[0]);
                        obj.put("downloads", skinDownloads);
                        obj.put("category", destination.getKey());
                        skinJSON.add(obj);

                        // download image file
                        String skinImageUrl = downloadPage.select("img[alt][title][src]").first().attr("src");
                        downloadImage(url + skinImageUrl, getFileNameFromUrl(skinImageUrl));

                    } catch (Exception ex) {
                        System.err.println("\nError occurred while downloading skin.");
                        ex.printStackTrace();
                    }
                }

                // increment page number for next loop
                page++;
                saveSkinInfo();
            }
            System.out.println("Last page reached, jumping to next category");
        }
    }

    private static void scrapeWinCustomize() throws Exception {
        String url = "https://www.wincustomize.com/explore/winamp";
        String plainUrl = "https://www.wincustomize.com";

        if (!type.equals("skin")) System.out.printf("Type %s is unsupported with this website, scraping skins instead", type);
        System.out.printf("Scraping %s\n\n", url);

        int page = 0;
        int totalpages = Integer.MAX_VALUE;

        while (page++ < totalpages){
            // get main webpage
            Document webpage = Jsoup.connect(url + "/page/" + page).get();
            totalpages = Integer.parseInt(webpage.getElementsByClass("ptotalpages").first().text());
            System.out.printf("Page %d of %d.\n", page, totalpages);

            Elements skins = webpage.getElementsByClass("explore_listing");
            for (Element skin : skins){
                try{
                    // get url and webpage
                    String skinUrl = plainUrl + skin.getElementsByTag("a").first().attr("href");
                    Document skinPage = Jsoup.connect(skinUrl).get();

                    // id
                    String[] skinidtemp = skinUrl.split("/");
                    String skinId = skinidtemp[skinidtemp.length-1];

                    // name
                    String skinName = skinPage.getElementById("skinname").text();

                    // extract description (i know this part looks like garbage but it works i promise =D)
                    String skinDescription = "";
                    try{
                        Element descriptionEl = skinPage.getElementsByClass("description").first();
                        skinDescription = descriptionEl.wholeText();
                        skinDescription.concat(descriptionEl.getElementsByClass("details").first().wholeText()).trim();
                    }catch (Exception e){
                        // sup
                    }

                    // extract date and author
                    Element dateAuthorElement = skinPage.getElementById("skinname").nextElementSibling();
                    String[] temp = dateAuthorElement.text().split(" by ");
                    String skinDate = temp[0].substring(8);
                    String skinAuthor = temp[1];

                    // rating and download count
                    String skinRating = skinPage.getElementById("averagerating-wrapper-cap").text();
                    String skinDownloads = skinPage.getElementsByClass("downloadstats").get(1).text();

                    // save to json
                    JSONObject obj = new JSONObject();
                    obj.put("id", skinId);
                    obj.put("name", skinName);
                    obj.put("description", skinDescription);
                    obj.put("url", skinUrl);
                    obj.put("author", skinAuthor);
                    obj.put("date", skinDate);
                    obj.put("downloads", skinDownloads);
                    obj.put("rating", skinRating);
                    skinJSON.add(obj);

                    // print info
                    System.out.printf("Name:         %s (%s)\nAuthor:       %s\n",
                            skinName,
                            skinId,
                            skinAuthor);

                    // download the skin
                    downloadSkinWithRedirect(plainUrl + skinPage.select("a[title='Download']").attr("href"));

                    // download image
                    Element imageEl = skinPage.select("a[style='float:left;padding:0 10px 10px 0']").first();
                    String imgUrl = "http:" + imageEl.attr("href");
                    String[] imgtemp = imgUrl.split("/");
                    downloadImage(imgUrl, imgtemp[imgtemp.length-1]);
                }catch (Exception e){
                    System.err.println("\nError occurred while downloading skin.");
                    e.printStackTrace();
                }
            }
            saveSkinInfo();
        }
    }

    private static void scrapeWinampSkinsDotInfo() throws Exception {
        String url = "http://www.winampskins.info/";

        if (!type.equals("skin")) System.out.printf("Type %s is unsupported with this website, scraping skins instead", type);
        System.out.printf("Scraping %s\n\n", url);

        // get categories
        Elements categoryEls = Jsoup.connect(url).get().select("table[cellSpacing=0][cellPadding=0][width='100%'][border=0]").first().getElementsByClass("links1");

        LinkedHashMap<String, String> categories = new LinkedHashMap<>();
        for (Element el : categoryEls){
            String[] temp = el.text().split(" ");
            categories.put(temp[0], el.attr("href"));
        }

        System.out.println("Available categories: " + categories.keySet());

        for (Map.Entry<String, String> category : categories.entrySet()){
            int page = 0;

            while (true) {
                // get skin links for current page
                System.out.printf("\nCategory: %s, page %d\n\n", category.getKey(), page);
                Elements skinRows = Jsoup.connect(category.getValue() +"/"+page+"/index.html")
                        .get()
                        .select("td[valign='top'][align='left']");

                if (skinRows.size() == 0)
                    break;

                for (Element row : skinRows){
                    try{
                        String skinUrl = row.getElementsByTag("a").attr("href");
                        Document skinPage = Jsoup.connect(skinUrl).get();
                        Element skinTable = skinPage.select("table[border='0'][width='100%'][cellpadding='10'][cellspacing='0'][align='left']").first();

                        // skin info
                        Elements temp = skinTable.getElementsByTag("td");
                        String skinName = temp.get(1).text();
                        String skinDownloads = temp.get(4).text();
                        String skinDate = temp.get(7).text();

                        // boi
                        String skinDownloadUrl = Jsoup.connect(temp.get(8).getElementsByTag("a").first().attr("href"))
                                .get()
                                .select("a[class='article-download'][title='download winamp skins']")
                                .attr("href");

                        // description and image
                        Element temp1 = skinPage.select("td[class='article']").first();
                        String skinDescription = temp1.wholeOwnText().trim();
                        String skinImageUrl = temp1.getElementsByTag("img").first().attr("src");
                        String skinImgName = getFileNameFromUrl(skinImageUrl);

                        System.out.printf("Name:         %s\nDownload URL: %s\n",
                                skinName,
                                skinDownloadUrl);

                        JSONObject obj = new JSONObject();
                        obj.put("name", skinName);
                        obj.put("url", skinUrl);
                        obj.put("description", skinDescription);
                        obj.put("imagefile", skinImgName);
                        obj.put("downloadurl", skinDownloadUrl);
                        obj.put("date", skinDate);
                        obj.put("downloads", skinDownloads);
                        skinJSON.add(obj);

                        downloadSkin(skinDownloadUrl, getFileNameFromUrl(skinDownloadUrl));
                        downloadImage(skinImageUrl, skinImgName);
                    } catch (Exception ex) {
                        System.err.println("\nError occurred while downloading skin.");
                        ex.printStackTrace();
                    }
                }

                page++;
                saveSkinInfo();
            }
            System.out.println("Last page reached, jumping to next category");
        }
    }

    private static void printHelp(){
        System.out.println(
                "-o output         Change output folder where skins will be saved\n" +
                "-d delay          Adjust delay between requests\n"+
                "-t type           Type of add-on to download. Works only with -w winampheritage.\n"+
                "-i timeout        Change request timeout\n"+
                "                  "+Arrays.toString(AVAILABLE_TYPES)+"\n"+
                "-w website        Website to scrape\n"+
                "                  "+Arrays.toString(AVAILABLE_WEBSITES)+"\n"
        );
    }
}
