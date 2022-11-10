package com.hh;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.*;

/**
 * @description: 下载 ok 资源网、最大资源网的  m3u8连接
 * @author gitsilence
 * @version 1.0
 * @date 2020/12/11 14:59
 */
public class M3u8Download {

    /*
     * m3u8地址
     * */


   private static final String M3U8_URL =  "http://playertest.longtailvideo.com/adaptive/bipbop/gear4/prog_index.m3u8";
//https:shushuvod1.vodyutu.comshu20221009shuz1TyMUuyshu1000kbshuhlsshuindex.m3u8

    /*
     * 视频的下载路径
     * */

    public static final String DOWNLOAD_PATH = "/usr/local/m2m/temp/";
    //public static final String DOWNLOAD_PATH = "E:\\cshi\\vode\\";

    /*
     * m3u8文件的路径
     * */

    private static final String FILE_PATH = DOWNLOAD_PATH + "tmp.m3u8";

    /*
     * 临时文件的文件名
     * */

    private static final String TMP_NAME = "tmp.txt";


    public static void main(String[] args) {
        down(M3U8_URL);
    }


    public static void down(String M3U8_URL ) {

        try {
            File tfile = new File(DOWNLOAD_PATH);
            downM3u8(M3U8_URL);
            boolean b = mergeVideo(tfile.listFiles(), DOWNLOAD_PATH+"test.mp4");
            System.out.println("合成结果"+b);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private static String downM3u8(String M3U8_URL) throws IOException {
        java.net.URL  u = new  java.net.URL(M3U8_URL);

        // 创建父级文件夹
        createParentDirs();
        // 下载外层m3u8 url
        downloadFile(M3U8_URL);
        String content = readFile(FILE_PATH);
        System.out.println(content);
        String realUrl = u.getProtocol()+"://"+u.getHost();
        System.out.println("realUrl: " + realUrl);
        List<String> urls = mergeUrl(content, realUrl);
        for(String url :urls ){
            downloadFile(url);
        }

        createMergeVideoTmp(content);

        ExecutorService executorService = Executors.newFixedThreadPool(12);
        urls.forEach( url -> {
            executorService.execute(new TsDownload(url));
        });

        executorService.shutdown();
        while (!executorService.isTerminated()) {
            System.out.println("任务下载中");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return content;
    }

    /**
     * corePoolSize：核心池的大小；当线程池中的线程数目达到corePoolSize后，就会把到达的任务放到缓存队列当中 =10
     * maximumPoolSize：线程池最大线程数 =16
     * keepAliveTime：表示线程没有任务执行时最多保持多久时间会终止 =1
     * unit：参数keepAliveTime的时间单位 =秒
     * workQueue：一个阻塞队列，用来存储等待执行的任务
     */


    public static void downloadFile (String m3u8Url) throws IOException {
        System.out.println("下载地址：" + m3u8Url);
        URL url = new URL(m3u8Url);
        URLConnection conn = url.openConnection();
        File f = new File(FILE_PATH);
        if (f.exists()) {
            f.delete();
        }

        RandomAccessFile file = new RandomAccessFile(FILE_PATH, "rw");
        InputStream inputStream = conn.getInputStream();

        byte[] buffer = new byte[1024];
        int hasRead = 0;
        while ((hasRead = inputStream.read(buffer)) != -1) {
            file.write(buffer, 0, hasRead);
        }
        file.close();
        inputStream.close();
    }

    public static String readFile (String path) {
        System.out.println("读取文件地址：" + path);
        StringBuilder builder = new StringBuilder();
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(path, "r");
            String content = null;
            while ((content = randomAccessFile.readLine()) != null) {
                if (!content.startsWith("#")) {
                    builder.append(content + "\n");
                }
            }
            randomAccessFile.close();
            return builder.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    /**
     *
     * @param content 很多的xxxxx.ts，以\n分割
     * @param realUrl 真实的m3u8地址 只是为了 取它的base url。为了拼接完整的url
     * @return
     */
    public static List<String> mergeUrl(String content, String realUrl) {
        String baseUrl = realUrl.split("index")[0];
        String[] split = content.split("\n");
        List<String> urls = new ArrayList<>();
        for (String str : split) {
            urls.add(baseUrl + str);
        }
        return urls;
    }

    /**
     *
     * @param content 很多的xxxxx.ts，以\n分割
     */
    public static void createMergeVideoTmp (String content) {
        String[] tsNames = content.split("\n");
        File file = new File(M3u8Download.DOWNLOAD_PATH + TMP_NAME);
        if (file.exists()) {
            file.delete();
        }
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(M3u8Download.DOWNLOAD_PATH + TMP_NAME, "rw");
            for (String tsName : tsNames) {
                randomAccessFile.write(("file '" + tsName + "'\n").getBytes());
            }
            randomAccessFile.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 合成视频代码
     * @param fPaths
     * @param resultPath
     * @return
     */
    public static boolean mergeVideo (File[] fPaths, String resultPath) {
        System.out.println("合成视频的路径==="+resultPath+"      文件数量"+fPaths.length);
        if (fPaths == null || fPaths.length < 1) {
            return false;
        }

        if (fPaths.length == 1) {
            return fPaths[0].renameTo(new File(resultPath));
        }
        for (int i = 0; i < fPaths.length; i++) {
            if (!fPaths[i].exists() || !fPaths[i].isFile()) {
                return false;
            }
        }
        File resultFile = new File(resultPath);

        try {
            FileOutputStream fs = new FileOutputStream(resultFile, true);
            FileChannel resultFileChannel = fs.getChannel();
            FileInputStream tfs;
            for (int i = 0; i < fPaths.length; i++) {
                tfs = new FileInputStream(fPaths[i]);
                FileChannel blk = tfs.getChannel();
                resultFileChannel.transferFrom(blk, resultFileChannel.size(), blk.size());
                tfs.close();
                blk.close();
            }
            fs.close();
            resultFileChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

         for (int i = 0; i < fPaths.length; i ++) {
               fPaths[i].delete();
         }

        return true;
    }




    public static void createParentDirs () {
        File file = new File(DOWNLOAD_PATH);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

}

/**
 * Ts 多线程下载类。
 */
class TsDownload implements Runnable {
    private String downloadUrl;
    private String filename;

    public TsDownload () {

    }

    public TsDownload(String downloadUrl) {
        this.downloadUrl = downloadUrl;
        String[] split = downloadUrl.split("/");
        this.filename = split[split.length - 1];
    }

    @Override
    public void run() {
        URL url = null;
        try {
            url = new URL(this.downloadUrl);
            System.out.println(Thread.currentThread().getName() + " 正在下载 " + this.filename);
            URLConnection conn = url.openConnection();
            RandomAccessFile file = new RandomAccessFile(M3u8Download.DOWNLOAD_PATH + this.filename, "rw");
            InputStream inputStream = conn.getInputStream();

            byte[] buffer = new byte[1024];
            int hasRead = 0;
            while ((hasRead = inputStream.read(buffer)) != -1) {
                file.write(buffer, 0, hasRead);
            }
            file.close();
            inputStream.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
