package com.xuecheng.media;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 大文件处理测试
 */
public class BigFileTest {


    //测试分块方法
    @Test
    public void testChunk() throws IOException {

        /**
         * 步骤流程：
         * 1、获取源文件长度
         * 2、根据设定的分块文件的大小计算出块数
         * 3、从源文件读数据依次向每一个块文件写数据。
         */

        File sourceFile = new File("C:\\study_java\\xc_edu资料\\day01\\视频\\Day1-00.项目导学.mp4");
        //分块文件夹路径
        String chunkPath = "C:\\study_java\\xc_edu资料\\day01\\视频\\chunk\\";
        File chunkFolder = new File(chunkPath);

        //判断分块文件夹是否存在，如果不存在则创建
        if (!chunkFolder.exists()) {
            chunkFolder.mkdirs();
        }

        //每个分块的大小
        long chunkSize = 1024 * 1024 * 1;

        //分块的数量为：源文件大小 / 分块大小
        long chunkNum = (long) Math.ceil(sourceFile.length() * 1 / chunkSize);

        //缓冲区大小（为什么缓冲区不设置成1024 * 1024）？
        byte[] b = new byte[1024];

        //使用 RandomAccessFile 来读取文件（这个RandomAccessFile经常被用来多线程下载和断点续传）
        RandomAccessFile raf_read = new RandomAccessFile(sourceFile, "r");

        //文件分块（注意：这里是 i <= chunkNum）
        for (int i = 0; i <= chunkNum; i++) {
            //创建分块文件
            File file = new File(chunkPath + i);
            //如果分块文件已存在则删除分块文件
            if (file.exists()) {
                file.delete();
            }
            //createNewFile 方法在文件不存在的时候创建文件, 返回true;
            //              文件存在的时候不会创建文件, 返回false;
            boolean newFile = file.createNewFile();
            if (newFile) {
                //向分块文件中写数据
                RandomAccessFile raf_write = new RandomAccessFile(file, "rw");
                //当读取到文件末尾的时候会返回 -1
                int len = -1;
                while ((len = raf_read.read(b)) != -1) {
                    raf_write.write(b, 0, len);
                    //判断如果分块文件大于分块大小则跳出循环, 注意是 >= (其实无所谓，反正都要合并)
                    if (file.length() >= chunkSize) {
                        break;
                    }
                }
                raf_write.close();
                System.out.println("完成分块：" + i);
            }
        }

        raf_read.close();

    }


    //测试分块合并功能
    @Test
    public void testMerge() throws IOException {

        /**
         * 步骤流程
         * 1、找到要合并的文件并按文件合并的先后进行排序。
         * 2、创建合并文件
         * 3、依次从合并的文件中读取数据向合并文件写入数
         */

        //分块文件夹路径
        String chunkPath = "C:\\study_java\\xc_edu资料\\day01\\视频\\chunk\\";
        File chunkFolder = new File(chunkPath);

        //源文件
        File originalFile = new File("C:\\study_java\\xc_edu资料\\day01\\视频\\Day1-00.项目导学.mp4");

        //合并文件
        File mergeFile = new File("C:\\study_java\\xc_edu资料\\day01\\视频\\merge01.mp4");
        //如果该合并文件已存在则删除该文件
        if (mergeFile.exists()) {
            mergeFile.delete();
        }
        //创建该文件到硬盘中
        mergeFile.createNewFile();
        //使用 RandomAccessFile 来读取文件（这个RandomAccessFile经常被用来多线程下载和断点续传）
        RandomAccessFile raf_write = new RandomAccessFile(mergeFile, "rw");
        //指针指向文件顶端
        raf_write.seek(0);

        //缓冲区
        byte[] b = new byte[1024];

        //分块列表（获得该文件夹目录下的所有文件（分块）并返回 File 文件数组）
        File[] fileArray = chunkFolder.listFiles();
        //数组转集合，方便排序
        List<File> fileList = new ArrayList<>(Arrays.asList(fileArray));
        //从小到大排序
        Collections.sort(fileList,
                (o1, o2) -> Integer.parseInt(o1.getName()) - Integer.parseInt(o2.getName()));

        //合并文件
        for (File chunkFile : fileList) {
            RandomAccessFile raf_read = new RandomAccessFile(chunkFile, "r");
            int len = -1;
            while ((len = raf_read.read(b)) != -1) {
                raf_write.write(b, 0, len);
            }
            raf_read.close();
        }
        raf_write.close();

        //校验文件
        FileInputStream originalInputStream = new FileInputStream(originalFile);
        FileInputStream mergeInputStream = new FileInputStream(mergeFile);
        //原始文件的md5
        String originalMd5 = DigestUtils.md5Hex(originalInputStream);
        //合并文件的md5
        String mergeMd5 = DigestUtils.md5Hex(mergeInputStream);
        if (originalMd5.equals(mergeMd5)) {
            System.out.println("合并文件成功");
        } else {
            System.out.println("合并文件失败");
        }

    }


}
