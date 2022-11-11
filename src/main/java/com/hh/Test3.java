package com.hh;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

//例如：#EXT-X-KEY:METHOD=AES-128,URI="key.key",IV=0x33336461653165663832316134373162
public class Test3 {
    //static String m3u8url = "http://playertest.longtailvideo.com/adaptive/bipbop/gear4/prog_index.m3u8";// m3u8链接
    static String fileName = "";// 保存文件名
   // static String Dir = "E:\\cshi\\vode\\";// 保存路径

    static   String Dir =  "/usr/local/m2m/temp/";

    static String ffmpegDir = "/usr/local/m2m/temp/ffmpeg-5.1.2-essentials_build/bin/";// ffmpeg解压路径

    //static String ffmpegDir = "C:\\Users\\admin\\Downloads\\ffmpeg-5.1.2-essentials_build\\ffmpeg-5.1.2-essentials_build\\bin//";// ffmpeg解压路径
    static String KEY = "";// 加密视频的密钥，位数必须为16的倍数
    static String IV = "";// 加密视频的密钥IV
    static int N = 10;// 线程数10
    static int INDEX = 0;// 下标

    public static void main(String[] args) throws Exception {

    }
    public static void down(String m3u8url) throws InterruptedException {
        // 使用代理服务器
        // System.getProperties().setProperty("proxySet", "true");
        // 代理服务器地址
        // System.getProperties().setProperty("http.proxyHost", "192.168.3.4");
        // 代理端口
        // System.getProperties().setProperty("http.proxyPort", "8080");

        String headUrl = m3u8url.substring(0, m3u8url.lastIndexOf("/") + 1);// 链接的头部
        if (headUrl.contains("?")) {
            headUrl = headUrl.substring(0, headUrl.indexOf("?"));
            headUrl = headUrl.substring(0, headUrl.lastIndexOf("/") + 1);// 链接的头部
        }
        // https下载
        // String sendGet = sendGet2(m3u8url, StandardCharsets.UTF_8.name());// 下载index.m3u8
        String sendGet = sendGet(m3u8url, StandardCharsets.UTF_8.name());// 下载index.m3u8
        String[] split = sendGet.split("\n");
        String url = "";
        List<String> urls = new ArrayList<String>();
        // 获取ts链接和加密视频的key
        for (String s : split) {
            if (s.contains("EXT-X-KEY")) {
                int index = s.indexOf("URI=") + 5;
                String keyUrl = s.substring(index, s.indexOf("\"", index));
                if (keyUrl.startsWith("http")) {
                    url = keyUrl;
                } else {
                    url = headUrl + keyUrl;
                }
                KEY = sendGet(url, StandardCharsets.UTF_8.name());
                if (KEY == null || KEY.length() == 0) {// key.key的链接错误）
                    String replace = keyUrl.substring(0, keyUrl.lastIndexOf("/") + 1);
                    headUrl = headUrl.replace(replace, "");
                    url = headUrl + keyUrl;
                    KEY = sendGet(url, StandardCharsets.UTF_8.name());
                    System.out.println("key：" + KEY);// 加密视频的key
                }

                if (IV == null || IV.length() == 0) {
                    if (s.contains("IV=")) {
                        index = s.indexOf("IV=") + 3;
                        IV = s.substring(index);
                        if (IV.startsWith("0x")) {
                            IV = IV.substring(2);
                            IV = hexStr2Str(IV);
                        }
                    } else {
                        IV = KEY;
                    }

                    System.out.println("iv：" + IV);// 加密视频的IV

                }

            } else if (s.contains(".ts")) {
                if (s.startsWith("http")) {
                    urls.add(s);
                } else {
                    urls.add(headUrl + s);
                }
            }
        }
        File f = new File(Dir + "/test.ts");
        while (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        // 开启多线程下载
        // CountDownLatch countDownLatch = new CountDownLatch(N);// 实例化一个倒计数器，N指定计数个数
        // countDownLatch.countDown(); // 计数减一
        // countDownLatch.await();// 等待，当计数减到0时，所有线程并行执行
        final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(N);
        for (int i = 0; i < urls.size(); i++) {
            fixedThreadPool.execute(() -> {
                try {
                    int index = getIndex();
                    String ts = sendGet(urls.get(index), StandardCharsets.ISO_8859_1.name());
                    byte[] tbyte = ts.getBytes(StandardCharsets.ISO_8859_1.name());
                    if (!"".equals(KEY)) {
                        tbyte = decryptCBC(tbyte, KEY, IV);
                    }
                    saveFile(tbyte, "000" + (index + 1) + ".ts");
                    System.out.println("下载000" + (index + 1) + ".ts结束：" + urls.get(index));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                }
            });
        }
        fixedThreadPool.shutdown();
        // 等待子线程结束，再继续执行下面的代码
        fixedThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        System.out.println("所有ts下载结束，总共：" + urls.size() + "个文件！");

        // 合并ts文件
        mergeTs(urls);
        System.out.println("合并完成：" + Dir + fileName + ".ts");
        // ffmpeg合并ts文件
        ffmpegMergeTs(urls);
        System.out.println("合并完成：" + Dir + fileName + ".mp4");
        // 删除ts文件
        deleteTs(urls);

    }

    /**
     * 获取其中一个ts文件
     */
    private synchronized static int getIndex() {
        return INDEX++;
    }

    /**
     * 删除ts文件
     */
    private static void deleteTs(List<String> urls) {
        for (int i = 0; i < urls.size(); i++) {
            new File(Dir + "000" + (i + 1) + ".ts").delete();
        }
    }

    /**
     * 合并ts文件
     *
     */
    private static void mergeTs(List<String> urls) {
        FileOutputStream fos = null;
        FileInputStream fis = null;
        try {
            if ("".equals(fileName)) {
                fileName = "1" + new Random().nextInt(10000);
            }
            File file = new File(Dir + fileName + ".ts");
            fos = new FileOutputStream(file);
            byte[] buf = new byte[4096];
            int len;
            for (int i = 0; i < urls.size(); i++) {
                fis = new FileInputStream(Dir + "000" + (i + 1) + ".ts");
                while ((len = fis.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fos.flush();
                fis.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * ffmpeg合并ts文件
     *
     */
    private static void ffmpegMergeTs(List<String> urls) {
        BufferedWriter bw = null;
        try {
            File file = new File(Dir + "file.txt");
            System.out.println("ffmpeg执行命令：");
            String command = ffmpegDir + "ffmpeg.exe -f concat -safe 0 -i " + file.getAbsolutePath() + " -c copy \"" + Dir + fileName + ".mp4\"";
            System.out.println(command);
            bw = new BufferedWriter(new FileWriter(file));
            for (int i = 0; i < urls.size(); i++) {
                bw.write("file '" + Dir + "000" + (i + 1) + ".ts" + "'");
                bw.newLine();
                bw.flush();
            }

            //
            // ffmpeg执行合并命令
            //
            // ffmpeg -i "concat:1.ts|2.ts|3.ts" -c copy output.mp4
            // # -safe 0: 防止Operation not permitted 不允许操作
            // ffmpeg.exe -f concat -safe 0 -i file.txt -c copy out.mp4
            // ffmpeg -f concat -safe 0 -i file.txt -c copy out.mp4

            try {
                Process exec = Runtime.getRuntime().exec(command);
                exec.getOutputStream().close();
                printMessage(exec.getInputStream());
                printMessage(exec.getErrorStream());
                int value = exec.waitFor();
                System.out.println("ffmpeg合并结束：" + value);
            } catch (IOException e) {
                System.out.println("IO读取异常");
            } catch (Exception e) {
                System.out.println("程序中断异常");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 用于打印日志文件，防止执行阻塞
     */
    private static void printMessage(final InputStream input) {
        new Thread(() -> {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(input, "GBK"));
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.out.println("IO读取异常");
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    /**
     * AES CBC 解密
     * @param key   sSrc ts文件字节数组
     * @param iv    IV，需要和key长度相同
     * @return  解密后数据
     */
    public static byte[] decryptCBC(byte[] src, String key, String iv) {
        try {
            byte[] keyByte = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyByte, "AES");
            byte[] ivByte = iv.getBytes(StandardCharsets.UTF_8);
            IvParameterSpec ivSpec = new IvParameterSpec(ivByte);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] content = cipher.doFinal(src);
            return content;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 保存ts文件
     */
    private static void saveFile(byte[] ts, String name) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(Dir + name);
            fos.write(ts);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * http下载文件
     */
    public static String sendGet(String url, String charset) {
        HttpURLConnection con = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            // 打开连接
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");// 客户端告诉服务器实际发送的数据类型
            con.setRequestProperty("Accept", "*/*");
            // con.setRequestProperty("Connection", "keep-alive");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36");
            // 开启连接
            con.connect();
            is = con.getInputStream();
            baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
                baos.flush();
            }
            return baos.toString(charset);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (is != null) {
                    is.close();
                }
                if (con != null) {
                    con.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * https 下载文件
     */
    public static String sendGet2(String url, String charset) {
        HttpsURLConnection con = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            // 打开连接
            con = (HttpsURLConnection) new URL(url).openConnection();
            // 绕过证书验证
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[] { new TrustAnyTrustManager() }, new java.security.SecureRandom());
            con.setSSLSocketFactory(sc.getSocketFactory());
            // 绕过验证主机名和服务器验证方案的匹配是可接受的
            con.setHostnameVerifier(new CustomizedHostnameVerifier());
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");// 客户端告诉服务器实际发送的数据类型
            con.setRequestProperty("Accept", "*/*");
            // con.setRequestProperty("Connection", "keep-alive");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36");
            // 开启连接
            con.connect();
            is = con.getInputStream();
            baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
                baos.flush();
            }
            return baos.toString(charset);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (baos != null) {
                    baos.close();
                }
                if (is != null) {
                    is.close();
                }
                if (con != null) {
                    con.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class TrustAnyTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {};
        }
    }

    static class CustomizedHostnameVerifier implements HostnameVerifier {
        // 重写验证方法
        @Override
        public boolean verify(String urlHostName, SSLSession session) {
            // System.out.println("Warning: URL Host: " + urlHostName + " vs. " + session.getPeerHost());
            // 所有都正确
            return true;
        }
    }

    /**
     * 字符串转换成为16进制
     * @param str
     * @return
     */
    public static String str2HexStr(String str) {
        char[] chars = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder("");
        byte[] bs = str.getBytes();
        int bit;
        for (int i = 0; i < bs.length; i++) {
            bit = (bs[i] & 0x0f0) >> 4;
            sb.append(chars[bit]);
            bit = bs[i] & 0x0f;
            sb.append(chars[bit]);
            // sb.append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * 16进制直接转换成为字符串
     * @param hexStr
     * @return
     */
    public static String hexStr2Str(String hexStr) {
        String str = "0123456789ABCDEF";
        char[] hexs = hexStr.toCharArray();
        byte[] bytes = new byte[hexStr.length() / 2];
        int n;
        for (int i = 0; i < bytes.length; i++) {
            n = str.indexOf(hexs[2 * i]) * 16;
            n += str.indexOf(hexs[2 * i + 1]);
            bytes[i] = (byte) (n & 0xff);
        }
        return new String(bytes);
    }
}
