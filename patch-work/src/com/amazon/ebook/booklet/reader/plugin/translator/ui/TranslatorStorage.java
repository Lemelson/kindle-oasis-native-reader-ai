package com.amazon.ebook.booklet.reader.plugin.translator.ui;

import com.amazon.ebook.booklet.reader.sdk.ReaderSDK;
import com.amazon.ebook.booklet.reader.sdk.content.Book;
import com.amazon.ebook.booklet.reader.sdk.content.BookMetadata;
import com.amazon.ebook.booklet.reader.storage.SimpleStorage;
import com.amazon.ebook.booklet.reader.storage.StorageFactory;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class TranslatorStorage {
    private static final String DEFAULT_TARGET_LANGUAGE = "ru";
    private static final String STORAGE_NAME = "Translator";
    private static final String TO_LANG_PREFIX = "toLang.";
    private static final String DEFAULT_NO_ID = "default_no_id";
    private final SimpleStorage dsW;
    private final String dvj;

    public TranslatorStorage(ReaderSDK sdk) {
        this.dsW = StorageFactory.d(STORAGE_NAME, sdk);
        this.dvj = bH(sdk.mO());
    }

    public String avp() {
        Map map = this.dsW.aHt();
        if (map != null) {
            Object value = map.get(this.dvj);
            if (value instanceof String) {
                String savedTarget = (String) value;
                if (savedTarget.length() > 0 && !"en".equalsIgnoreCase(savedTarget)) {
                    return savedTarget;
                }
            }
        }
        return DEFAULT_TARGET_LANGUAGE;
    }

    public void mA(String lang) {
        Map map = this.dsW.aHt();
        if (map == null) {
            map = new HashMap();
        }
        map.put(this.dvj, lang);
        this.dsW.aj(map);
    }

    private String bH(Book book) {
        StringBuffer key = new StringBuffer(TO_LANG_PREFIX);
        BookMetadata metadata = book.mx();
        if (isNotEmpty(metadata.getASIN())) {
            key.append(metadata.getASIN());
        } else if (isNotEmpty(metadata.getGUID())) {
            key.append(metadata.getGUID());
        } else {
            key.append(DEFAULT_NO_ID);
        }
        return key.toString();
    }

    public String am(String text, String sourceLang, String destLang) {
        Map map = this.dsW.aHt();
        if (map == null) {
            return null;
        }
        if (!map.containsKey("cachedText") ||
                !text.equals(map.get("cachedText")) ||
                !map.containsKey("cachedDestLang") ||
                !destLang.equals(map.get("cachedDestLang")) ||
                !map.containsKey("cachedResult")) {
            return null;
        }
        if (!map.containsKey("cachedSourceLang") ||
                (sourceLang != null && !sourceLang.equals(map.get("cachedSourceLang")))) {
            return null;
        }
        if (!deviceLocale().equalsIgnoreCase((String) map.get("cachedDeviceLocale"))) {
            return null;
        }
        return (String) map.get("cachedResult");
    }

    public void o(String text, String sourceLang, String destLang, String result) {
        if (sourceLang == null) {
            return;
        }
        Map map = this.dsW.aHt();
        if (map == null) {
            map = new HashMap();
        }
        map.put("cachedText", text);
        map.put("cachedSourceLang", sourceLang);
        map.put("cachedDestLang", destLang);
        map.put("cachedResult", result);
        map.put("cachedDeviceLocale", deviceLocale());
        this.dsW.aj(map);
    }

    private static boolean isNotEmpty(String value) {
        return value != null && value.length() > 0;
    }

    private static String deviceLocale() {
        try {
            Class languageTag = Class.forName("com.amazon.ebook.util.text.LanguageTag");
            Method method = languageTag.getMethod("bla", new Class[0]);
            Object value = method.invoke(null, new Object[0]);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
