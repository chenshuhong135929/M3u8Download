package com.hh;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

//例如：#EXT-X-KEY:METHOD=AES-128,URI="https://ke.xx.com/cgi-bin/qcloud/get_dk?xx",IV=0x00000000000000000000000000000000
public class TengXunKeTang {
    static String m3u8url = "http://playertest.longtailvideo.com/adaptive/bipbop/gear4/prog_index.m3u8";// m3u8链接
    static String fileName = "";// 保存文件名
    static String Dir = "E:\\cshi\\vode\\";// 保存路径
    static String KEY = "";// 加密视频的密钥，位数必须为16的倍数
    static String IV = "";// 加密视频的密钥IV
    static int N = 10;// 线程数10
    static int INDEX = 0;// 下标

    public static void main(String[] args) throws Exception {
        String headUrl = m3u8url.substring(0, m3u8url.lastIndexOf("/") + 1);// 链接的头部
        String sendGet = sendGet(m3u8url, StandardCharsets.UTF_8.name());// 下载index.m3u8
        String[] split = sendGet.split("\n");
        String url = "";
        List<String> urls = new ArrayList<String>();
        // 获取ts链接和加密视频的key
        for (String s : split) {
            if (s.contains("EXT-X-KEY")) {
                int index = s.indexOf("URI=") + 5;
                String keyUrl = s.substring(index, s.indexOf("\"", index));
                if (keyUrl.startsWith("https:") || keyUrl.startsWith("http:")) {
                    url = keyUrl;
                } else {
                    url = headUrl + keyUrl;
                }
                if (KEY == null || KEY.length() == 0) {
                    KEY = sendGet(url, StandardCharsets.ISO_8859_1.name());
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
     * AES CBC 解密
     * @param key   sSrc ts文件字节数组
     * @param iv    IV，需要和key长度相同
     * @return  解密后数据
     */
    public static byte[] decryptCBC(byte[] src, String key, String iv) {
        try {
            byte[] keyByte = key.getBytes(StandardCharsets.ISO_8859_1);
            SecretKeySpec keySpec = new SecretKeySpec(keyByte, "AES");
            byte[] ivByte = iv.getBytes(StandardCharsets.ISO_8859_1);
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
     * 下载文件
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
            con.setRequestProperty("Connection", "keep-alive");
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
        try {
            return new String(bytes, "ISO_8859_1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
}