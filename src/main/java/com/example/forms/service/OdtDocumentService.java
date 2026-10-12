package com.example.forms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.util.regex.*;

@Service
public class OdtDocumentService {
    private final Path directory;
    private final String office;

    public OdtDocumentService(@Value("${app.docs.directory}") String directory, @Value("${app.libreoffice.command}") String office) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.office = office;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    public Path render(String template, Map<String, String> data, String docId) throws IOException, InterruptedException {
        Files.createDirectories(directory);
        Path odt = directory.resolve(docId + ".odt");
        try (InputStream in = getClass().getResourceAsStream("/templates/" + template)) {
            if (in == null) throw new IOException("Missing template " + template);
            try (ZipInputStream zin = new ZipInputStream(in); ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(odt))) {
                ZipEntry e;
                while ((e = zin.getNextEntry()) != null) {
                    ZipEntry copy = new ZipEntry(e.getName());
                    zout.putNextEntry(copy);
                    byte[] bytes = zin.readAllBytes();
                    if (e.getName().equals("content.xml")) {
                        String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        Matcher m = Pattern.compile("\\{\\{([A-Z_]+)\\}\\}").matcher(xml);
                        StringBuffer b = new StringBuffer();
                        while (m.find()) {
                            String value = data.get(m.group(1));
                            if (value == null || value.isBlank())
                                throw new IllegalArgumentException("MISSING_REQUIRED_FORM_DATA: " + m.group(1));
                            m.appendReplacement(b, Matcher.quoteReplacement(escape(value)));
                        }
                        m.appendTail(b);
                        bytes = b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    zout.write(bytes);
                    zout.closeEntry();
                }
            }
        }
        // ODT is always saved. LibreOffice headless additionally creates a PDF when installed.
        try {
            Process proc = new ProcessBuilder(office, "-env:UserInstallation=file:///tmp/lo-forms-" + docId, "--headless", "--convert-to", "pdf", "--outdir", directory.toString(), odt.toString()).redirectErrorStream(true).start();
            proc.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (!proc.waitFor(25, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly();
            }
            Path pdf = directory.resolve(docId + ".pdf");
            if (Files.exists(pdf)) return pdf;
        } catch (IOException ignored) {/* LibreOffice optional: ODT remains usable */}
        return odt;
    }

    public Path locate(String filename) {
        Path p = directory.resolve(filename).normalize();
        if (!p.startsWith(directory)) throw new IllegalArgumentException("Invalid filename");
        return p;
    }
}
