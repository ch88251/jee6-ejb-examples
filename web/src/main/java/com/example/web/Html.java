package com.example.web;

/** HTML helpers shared by the servlets. */
final class Html {

    private Html() {
    }

    /** Escapes text for use in HTML content and single- or double-quoted attributes. */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }
}
