package com.ykt.assistant;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
public class PptxWriterTest {
    @Test public void pptxContainsLinkedSlidesAndValidXml() throws Exception {
        File image=File.createTempFile("slide",".jpg");
        try {
            try(FileOutputStream f=new FileOutputStream(image)) { f.write(new byte[]{(byte)255,(byte)216,(byte)255,(byte)217}); }
            ByteArrayOutputStream out=new ByteArrayOutputStream(); PptxWriter.write(out,"课件 <&>",16.0/9,Arrays.asList(image,image));
            Map<String,byte[]> entries=new HashMap<>();
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) { ZipEntry e; while((e=zip.getNextEntry())!=null) { ByteArrayOutputStream data=new ByteArrayOutputStream(); byte[] bytes=new byte[4096]; int n; while((n=zip.read(bytes))!=-1) data.write(bytes,0,n); entries.put(e.getName(),data.toByteArray()); } }
            for(String name:Arrays.asList("[Content_Types].xml","_rels/.rels","ppt/presentation.xml","ppt/slides/slide1.xml","ppt/slides/slide2.xml","ppt/media/image1.jpg","ppt/theme/theme1.xml","ppt/slideMasters/slideMaster1.xml")) assertTrue(name,entries.containsKey(name));
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
            for(Map.Entry<String,byte[]> e:entries.entrySet()) if(e.getKey().endsWith(".xml")||e.getKey().endsWith(".rels")) factory.newDocumentBuilder().parse(new ByteArrayInputStream(e.getValue()));
            String presentation=new String(entries.get("ppt/presentation.xml"),java.nio.charset.StandardCharsets.UTF_8); assertTrue(presentation.contains("cx=\"12192000\"")); assertTrue(presentation.contains("cy=\"6858000\""));
            assertTrue(new String(entries.get("docProps/core.xml"),java.nio.charset.StandardCharsets.UTF_8).contains("&lt;&amp;&gt;"));
        } finally { assertTrue(image.delete()); }
    }
}
