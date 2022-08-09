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
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    private final static String VERSION = "0.3";
    private final static String[] AVAILABLE_TYPES = { "skin", "plugin", "visualization" };

    // default values
    private static int requestDelay = 500;
    private static int timeout = 500;
    private static String dest = "./downloads/";
    private static String type = "skin";

    // json file where we store scraped skin info
    private static JSONArray skinJSON;

    // dirty fix
    private static String currentCategory, currentSkinType;

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
                    default:
                        System.err.printf("Invalid argument %s. Use -h for a brief explanation.\n",args[i]);
                        System.exit(1);
                }
            }
        }catch (Exception e){
            System.err.println("Invalid arguments. Use -h for a brief explanation.");
            System.exit(1);
        }

        System.out.printf("Winamp skin scraper version %s\nOutput folder: %s\nRequest delay: %s\nAdd-on type: %s\n", VERSION, dest, requestDelay, type);
        System.out.println("------------------------------------------------------------------------");

        skinJSON = new JSONArray();

        try{
            scrapeWinampHeritage();
        } catch (Exception e){
            System.err.println("Exception thrown");
            e.printStackTrace();
        }

        saveSkinInfo();
        System.out.println("Scraping finished.");
    }

    public static String getDestinationFolder(String filename){
        try{
            String filetype = filename.substring(filename.length()-3);

            if (type.equals("skin")){
                switch (filetype){
                    case "wal": currentSkinType = "modern"; return dest+"/"+type+"/modern/";
                    case "wsz": currentSkinType = "classic"; return dest+"/"+type+"/classic/";
                    default: currentSkinType = "unknown"; return dest+"/"+type+"/unknown/";
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
            FileWriter fw = new FileWriter(dest+"fileinfo.json");
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
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(timeout);
        //urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:103.0) Gecko/20100101 Firefox/103.0");   // this is a surprise tool that will help us later
        if (cookie != null) urlConnection.setRequestProperty("cookie", cookie);
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

        System.out.printf("\rSkin saved to %s\n", destination);
    }

    private static void downloadSkin(String urlstr, String filename) throws Exception{
        downloadSkin(urlstr, filename, null);
    }

    private static void downloadImage(String urlstr, String filename, String cookie) throws Exception{
        System.out.printf("Downloading image %s", urlstr);
        String destination = type.equals("skin")
                ? dest+"images/"+type+"/"+currentSkinType+"/"+filename
                : dest+"images/"+type+"/"+currentCategory+"/"+filename;

        URL url = new URL(urlstr);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(timeout);
        //urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:103.0) Gecko/20100101 Firefox/103.0");   // this is a surprise tool that will help us later
        if (cookie != null) urlConnection.setRequestProperty("cookie", cookie);
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
                                .get(0)
                                .attr("onclick");

                        // cut out the download url
                        skinDownloadUrl = skinDownloadUrl.split(",")[1];
                        skinDownloadUrl = url + skinDownloadUrl.substring(1, skinDownloadUrl.length() - 1);

                        // get description and author
                        String skinDescription = downloadPage.getElementsByTag("p").get(0).text();
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
                        String skinImageUrl = downloadPage.select("img[alt][title][src]").get(0).attr("src");
                        downloadImage(url + skinImageUrl, getFileNameFromUrl(skinImageUrl));

                    } catch (Exception ex) {
                        System.err.println("Error occurred while downloading skin: " + ex.getMessage());
                    }
                }

                // increment page number for next loop
                page++;
                saveSkinInfo();
            }
            System.out.println("Last page reached, jumping to next category");
        }

        System.out.println("Finished scraping "+url);
    }



    private static void printHelp(){
        System.out.println(
                "-o output         Change output folder where skins will be saved\n" +
                "-d delay          Adjust delay between requests\n"+
                "-t type           Type of add-on to download. Currently works with winampheritage.com.\n"+
                "                  [plugin, skin, visualization]"
        );
    }
}
