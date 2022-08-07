package com.mirzi;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private final static String VERSION = "0.1";

    // default values
    private static int requestDelay = 500;
    private static int timeout = 15000;
    private static String dest = "./downloads/";

    public static void main(String[] args){

        // todo: arguments (website to scrape, category to scrape, page to scrape)
        // parse arguments
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
                    default:
                        System.err.printf("Invalid argument %s. Use -h for a brief explanation.\n",args[i]);
                        System.exit(0);
                }
            }
        }catch (Exception e){
            System.err.println("Invalid arguments. Use -h for a brief explanation.");
            System.exit(0);
        }

        System.out.printf("Winamp skin scraper version %s\nOutput folder: %s\nRequest delay: %s\n", VERSION, dest, requestDelay);
        System.out.println("------------------------------------------------------------------------");

        try{
            scrapeWinampHeritage();
        } catch (Exception e){
            System.err.println("Exception thrown");
            e.printStackTrace();
        }
    }

    public static String getDestinationFolder(String filename){
        try{
            String filetype = filename.substring(filename.length()-3);
            switch (filetype){
                case "wal": return dest+"modern/";
                case "wsz": return dest+"classic/";
                default: return dest+"unknown/";
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
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(timeout);
        //urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:103.0) Gecko/20100101 Firefox/103.0");   // this is a surprise tool that will help us later
        if (!cookie.isEmpty()) urlConnection.setRequestProperty("cookie", cookie);
        ReadableByteChannel rbc = Channels.newChannel(urlConnection.getInputStream());

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

        System.out.printf("\rSkin saved to %s\n\n", destination);
    }

    private static void scrapeWinampHeritage() throws Exception {
        String url = "https://winampheritage.com";
        System.out.printf("Scraping %s\n\n", url);

        // get categories from main website
        Elements categories = Jsoup.connect(url + "/skins").get()
                .getElementsByClass("colorul inlineul")
                .get(0)
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
            boolean pageEmpty = false;
            int page = 0;

            while (!pageEmpty) {
                pageEmpty = true;

                // get page for category
                System.out.printf("\nCategory: %s, page %d\n\n", destination.getKey(), page);
                Elements skins = Jsoup.connect(destination.getValue() + "page-" + page).get()
                        .getElementsByAttribute("title");

                // iterate over skins on current page
                for (Element e : skins) {
                    String skinName = e.text();
                    String skinDescription = e.attr("title");
                    String destinationUrl = url + e.attr("href");

                    if (skinName.isEmpty() || skinDescription.isEmpty() || destinationUrl.isEmpty())
                        continue;      // ignore this tag if it's not a skin

                    pageEmpty = false;
                    System.out.printf("Name:         %s\nDescription:  %s\nURL:          %s\n", skinName, skinDescription, destinationUrl);

                    // parse download page and get download link
                    try {
                        // get onclick attribute
                        String downloadUrl = Jsoup.connect(destinationUrl).get()
                                .getElementsByClass("downloadbutton")
                                .get(0)
                                .attr("onclick");

                        // cut out the download url
                        downloadUrl = downloadUrl.split(",")[1];
                        downloadUrl = url + downloadUrl.substring(1, downloadUrl.length() - 1);

                        System.out.println("Download URL: " + downloadUrl);

                        downloadSkin(downloadUrl, getFileNameFromUrl(downloadUrl), "downloadsite=winampheritage");
                    } catch (Exception ex) {
                        System.err.println("Error occured while downloading skin: " + ex.getMessage());
                    }
                }

                // increment page number for next loop
                page++;
            }
            System.out.println("Last page reached, jumping to next category");
        }
    }



    private static void printHelp(){
        System.out.println(
                "-o output         Change output folder where skins will be saved\n" +
                "-d delay          Adjust delay between requests");
    }
}
