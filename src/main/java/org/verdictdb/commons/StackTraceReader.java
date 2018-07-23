package org.verdictdb.commons;

import java.io.StringWriter;

public class StackTraceReader {

    public static String stackTrace2String(Exception e) {
        StringWriter sw = new StringWriter();
//        PrintWriter pw = new PrintWriter(sw);
        sw.write(e.getMessage());
        return sw.toString();
    }

}